# Seat Map Loading Strategy

Sector 진입 시 전체 Tile summary를 먼저 조회하고, 실제 Seat 상세는 사용자가 보는 Tile에 대해서만 조회한다.

```text
SeatMap
  └─ Sector
      └─ Tile
          └─ Seat
```

- `Sector`: 사용자에게 보이는 구역
- `Tile`: Sector 내부의 경량 로딩 단위
- `Seat`: 실제 선택 가능한 좌석

사용자 관점 흐름:

```text
Sector 선택 -> Seat 선택
```

시스템 내부 흐름:

```text
Sector 선택
  -> Tile summary 조회
  -> Tile별 잔여 상태를 색상이나 밀도로 표시
  -> 필요한 Tile의 Seat 상세 조회
  -> 필요한 Tile의 좌석 변경만 구독
```

`Tile` 테이블은 정적 배치 메타데이터만 가진다.

```text
row_start_no
row_end_no
column_start_no
column_end_no
seat_count
```

공연 회차별 잔여 좌석 수, 상태 집계, runtime version은 Redis read model 또는 추후 `performance_tile_state`에서 관리한다.
