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

---

## Seat Position Terms

표시값과 계산값을 분리한다.

```text
rowLabel = 사용자에게 보이는 행 표시값. 예: AA
colLabel = 사용자에게 보이는 열/좌석 표시값. 예: 13
rowNo    = 내부 계산용 행 번호
colNo    = 내부 계산용 열 번호
```

예를 들어 `AA13` 좌석은 다음처럼 표현한다.

```text
rowLabel = AA
colLabel = 13
rowNo    = 27
colNo    = 13
code     = AA13
```

---

## Tile Metadata

`Tile` 테이블은 정적 배치 메타데이터만 가진다.

```text
row_start_no
row_end_no
col_start_no
col_end_no
seat_count
```

Tile의 row/col 범위는 실제 좌석 도형이 아니라 bounding box다.

Sector 또는 Tile이 반드시 직사각형일 필요는 없다. 실제 좌석 존재 여부는 `Seat` 목록으로 표현한다.

MVP에서는 polygon, row offset, row별 seat count 같은 정밀 layout metadata는 관리하지 않는다.

공연 회차별 잔여 좌석 수, 상태 집계, runtime version은 Redis read model 또는 추후 `performance_tile_state`에서 관리한다.
