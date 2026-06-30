# Rushmore Seat

대규모 예매 오픈 상황을 가정한 좌석 예약 시스템.

목표는 단순 예약 CRUD가 아니라, **대기열 기반 트래픽 제어**, **10만 좌석 실시간 상태 동기화**, **좌석 선점 경쟁 완화**, **초과 예약 방지**, **부하 테스트 기반 성능 검증**을 구현하는 것이다.

---

## Core Goals

- 10만 좌석 규모의 공연 회차를 생성한다.
- Redis 대기열로 예매 화면 진입량을 제어한다.
- admission token을 받은 사용자만 좌석 선택 화면에 진입한다.
- 사용자는 전체 좌석이 아니라 현재 보고 있는 sector/tile의 좌석 상태만 WebSocket으로 수신한다.
- 좌석 클릭 시 즉시 hold 요청을 수행한다.
- 좌석 hold는 PostgreSQL 조건부 update 성공 시에만 인정한다.
- hold된 좌석은 TTL 내에 confirm되지 않으면 release된다.
- 최종 예약 수는 실제 좌석 수를 초과하지 않아야 한다.

---

## Domain Model

공연장 구조와 예매 회차 상태를 분리한다.

```text
Venue
  └─ Hall
      └─ SeatMap
          ├─ Sector
          ├─ Tile
          └─ Seat

Performer
  └─ Show
      └─ Performance
          └─ PerformanceSeat
              └─ Reservation
```

핵심 원칙:

- `Seat`는 고정 좌석 배치 정보다.
- `PerformanceSeat`는 특정 공연 회차에서의 좌석 상태다.
- `Performance`의 PK는 별도 surrogate id를 사용한다.
- `starts_at`, `ends_at`, `hall_id`는 공연 회차의 속성으로 두고 PK나 unique identity로 쓰지 않는다.
- 시간 범위 중복 방지는 MVP에서는 DB constraint로 강제하지 않는다. 필요 시 service validation 또는 PostgreSQL exclusion constraint로 확장한다.

현재 1차 구현은 아직 `event/asset` 기반 skeleton이며, 이후 `performance/performance_seat` 기준으로 정리할 예정이다.

---

## Seat State

```text
AVAILABLE
  -> CLAIMING
  -> HELD
  -> RESERVED
```

| State | Meaning |
|---|---|
| AVAILABLE | 선택 가능 |
| CLAIMING | 누군가 방금 선택 시도 중 |
| HELD | 특정 사용자가 제한 시간 동안 선점 |
| RESERVED | 예약 확정 |

`CLAIMING`은 UX 완화용 상태다. 최종 정합성은 PostgreSQL 조건부 update로 보장한다.

---

## Queue / Admission

대기열은 Redis Sorted Set으로 관리한다.

```text
queue:waiting:{performanceId}
member = userId
score  = joinedAtMillis
```

```text
Queue passed = seat selection screen admission
Queue passed != seat reserved
```

100만 부하는 모든 요청을 예약 처리까지 통과시키는 것이 아니라, 대기열에서 흡수하고 실제 write path 유입량을 제한하는 방식으로 처리한다.

```text
1,000,000 queue enter attempts
  -> Redis waiting queue
  -> admission control
  -> 5,000 ~ 20,000 admitted users
  -> 1,000 ~ 5,000 WebSocket clients
  -> 50,000 ~ 200,000 hold attempts
  -> 300 ~ 500 rps PostgreSQL conditional update
```

---

## Sector / Tile Subscription

10만 좌석 전체 변경 이벤트를 모든 사용자에게 broadcast하지 않는다.

```text
SeatMap
 ├─ Display Sector A
 │   ├─ Tile A-01: 약 500 seats
 │   ├─ Tile A-02: 약 500 seats
 │   └─ ...
 ├─ Display Sector B
 └─ Display Sector C
```

| Concept | Purpose |
|---|---|
| Sector | 사용자에게 보이는 공연장 구역 |
| Tile | WebSocket 구독 및 delta push 단위 |

기본 목표:

```text
Total seats: 100,000
Default tile size: 500 seats
Subscription tiles: 약 200
Client subscribed tiles: 1 ~ 3
Batch interval: 100ms
Batch max changes: 200
```

느린 클라이언트의 outbound queue가 밀리면 오래된 delta를 계속 쌓지 않고 `TILE_RESYNC_REQUIRED`를 전송한 뒤 snapshot 재조회로 복구한다.

---

## Architecture

```mermaid
flowchart TD
    Client[Client]

    Client -->|Queue Enter / Poll| QueueAPI[Queue API]
    QueueAPI --> RedisQueue[(Redis ZSET Queue)]

    Client -->|Initial Snapshot| SeatAPI[Seat Snapshot API]
    SeatAPI --> RedisRead[(Redis Seat Read Model)]

    Client <-->|Tile Events| WSGateway[WebSocket Gateway]

    Client -->|Hold / Confirm| ReservationAPI[Reservation API]
    ReservationAPI --> RedisClaim[(Redis Claim Gate)]
    ReservationAPI --> Postgres[(PostgreSQL)]

    ReservationAPI --> EventPublisher[Seat Event Publisher]
    EventPublisher --> RedisRead
    EventPublisher --> WSGateway

    Reaper[Expired Hold Reaper] --> Postgres
    Reaper --> EventPublisher
```

Scaling target:

```text
Load Balancer
  ├─ app-1: HTTP API + WebSocket
  ├─ app-2: HTTP API + WebSocket
  ├─ app-3: HTTP API + WebSocket
  └─ app-4: HTTP API + WebSocket

Redis primary
PostgreSQL primary
Redis Pub/Sub backplane for WebSocket fanout
```

Replica 판단:

- API / WebSocket app replica는 필요하다.
- PostgreSQL read replica는 핵심 예약 처리 성능에는 직접 도움이 되지 않는다. `AVAILABLE -> HELD` 전이는 primary write이기 때문이다.
- Redis replica는 write scale-out 수단이 아니라 HA 또는 read offload 용도다.
- 여러 WebSocket app replica를 둘 경우 Redis Pub/Sub 기반 backplane으로 seat state event를 전파한다.

---

## API Draft

현재 skeleton은 `events/assets` 명칭을 사용한다. 도메인 정리 후 `performances/performance-seats` 기준으로 변경한다.

Target API shape:

```http
POST /performances/{performanceId}/queue
GET  /performances/{performanceId}/queue/me

GET /performances/{performanceId}/sectors/summary
GET /performances/{performanceId}/tiles/{tileId}/seats

WS /ws/performances/{performanceId}?token={admissionToken}

POST /performances/{performanceId}/seats/{performanceSeatId}/hold
POST /performances/{performanceId}/reservations/confirm
```

---

## Hold Strategy

좌석 hold는 두 단계로 처리한다.

### 1. Redis Claim Gate

동시 클릭 UX를 줄이기 위한 짧은 gate다.

```text
SET seat:claim:{performanceId}:{performanceSeatId} {userId} NX PX 2000
```

Redis claim gate는 정합성 장치가 아니다. 최종 정합성은 DB가 보장한다.

### 2. PostgreSQL Conditional Update

```sql
UPDATE performance_seat
SET
    status = 'HELD',
    hold_owner_id = :userId,
    hold_token = :holdToken,
    hold_expires_at = now() + interval '3 minutes',
    version = version + 1,
    updated_at = now()
WHERE id = :performanceSeatId
  AND performance_id = :performanceId
  AND status = 'AVAILABLE';
```

```text
affected rows = 1 -> hold success
affected rows = 0 -> already held or reserved
```

---

## Load Test Plan

| Tier | Purpose | Target |
|---|---|---:|
| Smoke | 기능 검증 | 10,000 queue enter attempts |
| MVP | 로컬 성능 기준 | 100,000 queue enter attempts |
| Target | 포트폴리오 기준 | 1,000,000 queue enter attempts |
| Stretch | 분산 부하 테스트 | 2,000,000+ queue enter attempts |

Target scenario:

```text
total seats: 100,000
queue enter attempts: 1,000,000
admitted users: 5,000 ~ 20,000
WebSocket clients: 1,000 ~ 5,000
subscribed tiles per client: 1 ~ 3
hold attempts: 50,000 ~ 200,000
hold request rate: 300 ~ 500 rps
confirm attempts: 1,000 ~ 10,000
```

핵심 지표:

- queue enter p50 / p95 / p99
- tile snapshot p50 / p95 / p99
- hold API p50 / p95 / p99
- WebSocket propagation p50 / p95 / p99
- false-positive click failure rate
- oversell count
- duplicate hold count
- Redis ops/sec
- PostgreSQL connection pool usage
- WebSocket outbound bytes/sec

MVP success criteria:

```text
1. 100,000 seats 생성 가능
2. Redis 대기열로 선택 화면 진입 제어 가능
3. 사용자는 현재 보고 있는 tile 이벤트만 WebSocket으로 수신
4. 좌석 hold는 PostgreSQL 조건부 update로만 성공 처리
5. oversell count = 0
6. duplicate hold count = 0
7. false-positive click failure rate < 0.5%
8. hold API p95 < 500ms
9. WebSocket propagation p95 < 300ms
10. tile size별 p50 / p95 / p99 비교 리포트 작성
```

---

## Tech Stack

| Area | Stack |
|---|---|
| Language | Kotlin |
| Runtime | Java 21 |
| Backend Framework | Spring Boot 3.5.16 |
| Build | Gradle |
| HTTP API | Spring MVC |
| Realtime | Spring WebSocket |
| WebSocket Backplane | Redis Pub/Sub |
| Database | PostgreSQL |
| DB Access | JPA, JdbcClient/JdbcTemplate for hot path SQL |
| Migration | Flyway |
| Cache / Queue / Claim Gate | Redis |
| Frontend | Vite, Vanilla TypeScript, HTML, CSS |
| Seatmap Rendering | Canvas |
| Load Test | k6 |
| Metrics | Spring Boot Actuator, Micrometer, Prometheus |
| Dashboard | Grafana |
| Test | JUnit5, Testcontainers |
| Runtime | Docker Compose |

---

## Project Naming

| Target | Name |
|---|---|
| Repository | `rushmore-seat` |
| Gradle root project | `rushmore-seat` |
| Spring application | `rushmore-seat` |
| Frontend package | `rushmore-seat-frontend` |

Kotlin package path는 당장은 `com.eooog.rushseat`을 유지한다. 대규모 package rename은 도메인 리팩터링과 함께 별도 작업으로 처리한다.

---

## Current Implementation Status

현재 구현은 1차 skeleton이다.

Implemented:

- Redis queue enter / queue status / manual admission
- admission token validation
- sector summary 조회
- tile seat snapshot 조회
- raw WebSocket tile subscription
- Redis claim gate
- PostgreSQL conditional update 기반 hold
- reservation row 생성 및 confirm 기초 흐름
- Vite + Vanilla TypeScript + Canvas 기반 최소 프론트

Next:

- `event/asset` 명칭을 `performance/performance_seat`로 정리
- Venue / Hall / SeatMap / Sector / Tile / Seat / Performer / Show / Performance 도메인 반영
- 10만 seat seed runner 추가
- expired hold reaper 구현
- Redis read model 도입
- WebSocket batch/coalescing 및 Redis Pub/Sub backplane 적용
- k6 부하 테스트 스크립트 작성
