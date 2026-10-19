# 캠퍼스 현황 · 강의실 상세 API

모든 응답은 공통 래퍼 `{ success, code, message, data }` 로 감싸며, 아래 예시는 `data` 부분만 적는다.
시간은 모두 KST `LocalDateTime` (`2026-10-19T09:00:00`).

판단 규칙은 `global/air/AirQualityJudge` 한 곳에 있다.

| 항목 | 규칙 |
|---|---|
| 기준값 | 로그인 사용자 `member` 설정, 비로그인이면 CO2 1000 / 온도 20–26 / 습도 40–60 (현재 로그인 미구현 → 항상 기본값) |
| 최신 값 | 최근 **10분** 내 마지막 `sensor_reading`. 없으면 null |
| `status` | `OVER`: CO2 ≥ 기준 · `PREDICTED`: 30분 안 예측 중 기준 이상 있음 · `NORMAL` · `NO_SENSOR`: `sensor_level` NONE/TH 이거나 10분 넘게 데이터 없음. `SIM`(시뮬레이터) 방은 `FULL` 과 똑같이 판정 |
| `simulated` | `sensor_level = SIM` 이면 true. 실측 센서는 617호(FULL) 하나, 나머지 강의실은 서버 시뮬레이터가 1분마다 값을 만든다 (`SensorSimulator`, 끄기: `SIMULATOR_ENABLED=false`) |
| `co2Level` | `NORMAL` 기준 90% 미만 · `CAUTION` 90% 이상 · `CRITICAL` 120% 이상 |
| `co2OverBy` | CO2 − 기준, 음수면 0 |
| 온습도 라벨 | 범위 안 "적정", 2 이내로 벗어나면 "살짝 더워요/추워요"·"조금 습해요/건조해요", 더 벗어나면 "더워요/추워요/습해요/건조해요" |
| `comfortScore` | CO2 60% + 온도 25% + 습도 15%. CO2 600↓ 100점 ~ 1600↑ 0점 직선, 온도 1℃당 −10, 습도 1%p당 −3. 값이 없는 항목은 빼고 가중치 재분배 |
| 예측 중복 | 같은 `target_at` 예측이 여러 개면 `created_at` 최신 것만 |

---

## GET /api/buildings

```json
[ { "buildingId": 1, "name": "북악관", "maxFloor": 7 } ]
```

## GET /api/buildings/{buildingId}/floors/{floor}

방 수와 상관없이 쿼리 4번 (건물, 방 목록, 방별 최신 측정, 방별 30분 예측).
`rooms` 순서: `side` TOP → BOTTOM → null, 같은 줄 안에서는 `order` 순 (null 은 맨 뒤). 방이 없는 층이면 `rooms: []`.
`alertCount` = OVER + PREDICTED 개수.

```json
{
  "building": "북악관",
  "floor": 6,
  "alertCount": 2,
  "rooms": [
    { "roomId": 4,  "number": "화장실1", "type": "RESTROOM", "side": "TOP",    "order": 4, "co2": null, "status": "NO_SENSOR", "simulated": false },
    { "roomId": 7,  "number": "617",    "type": "LECTURE",  "side": "TOP",    "order": 7, "co2": 1250, "status": "OVER",      "simulated": false },
    { "roomId": 12, "number": "607",    "type": "LECTURE",  "side": "BOTTOM", "order": 4, "co2": 900,  "status": "PREDICTED", "simulated": true }
  ]
}
```

- 404 `존재하지 않는 건물입니다.`

## GET /api/rooms/{roomId}

평면도 바텀시트와 강의실 상세 화면이 함께 쓴다.

```json
{
  "roomId": 7,
  "building": "북악관",
  "number": "617",
  "floor": 6,
  "simulated": false,
  "co2": 1250,
  "temp": 24,
  "humidity": 61,
  "comfortScore": 61,
  "co2Level": "CRITICAL",
  "co2OverBy": 250,
  "tempLabel": "적정",
  "humidityLabel": "조금 습해요",
  "ventilating": false,
  "pendingAction": { "actionId": 1, "message": "기준 초과 · 환기 전" },
  "currentClass": { "courseName": "자료구조", "start": "21:00", "end": "23:00" },
  "nextClass": { "courseName": "운영체제", "start": "23:10", "end": "23:50", "tip": "쉬는 시간에 창문 환기 추천" }
}
```

- `ventilating`: 이 방에 `IN_PROGRESS` 조치가 있으면 true
- `pendingAction`: `PENDING`/`CHECKED` 중 최신 1개, 없으면 null.
  `message` 는 OVER "기준 초과 · 환기 전", PREDICTED "약 N분 뒤 기준 초과 예상", 그 외 `action.summary`
- `currentClass`/`nextClass`: 오늘 요일 시간표에서 오늘 휴강(`class_cancellation`)인 수업은 뺀다. 없으면 null.
  인원 표시는 화면에서 빠져 내려주지 않는다 (`room.capacity`, `class_schedule.enrolled` 컬럼은 남겨둠)
- `nextClass.tip`: 환기가 필요할 때(OVER/PREDICTED)만 "쉬는 시간에 창문 환기 추천", 아니면 null
- CO2 센서가 없는 방(화장실 등)은 측정 관련 필드가 모두 null
- 404 `존재하지 않는 공간입니다.`

## GET /api/rooms/{roomId}/readings/latest

마지막 측정값. 10분 넘게 지난 값이어도 내려주고 `sensorOnline: false`. 측정이 한 번도 없으면 값은 모두 null.
`level` 은 `co2Level` 과 같은 규칙.

```json
{ "roomId": 7, "co2": 780, "temp": 23, "humidity": 55, "comfortScore": 89, "level": "NORMAL",
  "measuredAt": "2026-10-19T22:19:14.930", "sensorOnline": true }
```

> 예전 `GET /api/rooms/{code}/readings/latest` (code 기반)는 경로가 겹쳐 이걸로 대체됨.

## GET /api/rooms/{roomId}/readings?from=&to=&interval=

그래프 실측. `[from, to)` 구간, `interval` 분(기본 5, 1~60) 평균. `from`/`to` 를 비우면 오늘 0시 ~ 지금.
`at` 은 구간 시작 시각이며 그날 0시 기준으로 맞춘다 (09:00, 09:05 ...). 측정이 없는 구간은 점이 없다.

```
GET /api/rooms/7/readings?from=2026-10-19T09:00:00&to=2026-10-19T18:00:00&interval=5
```
```json
{
  "interval": 5,
  "points": [
    { "at": "2026-10-19T09:00:00", "co2": 992, "temp": 23, "humidity": 45 },
    { "at": "2026-10-19T09:05:00", "co2": 997, "temp": 23, "humidity": 45 }
  ]
}
```

- 400 `조회 구간이 올바르지 않습니다. (from < to, 최대 31일)` / interval 범위 밖

## GET /api/rooms/{roomId}/predictions?from=&to=

그래프 예측. `[from, to)` 구간의 `target_at` 기준. `from`/`to` 를 비우면 지금 ~ 내일 0시.
`modelVersion` 은 가장 최근에 만든 예측의 버전 (예측이 없으면 null).

```json
{
  "modelVersion": "lgbm-v1",
  "points": [
    { "at": "2026-10-19T22:06:00", "co2": 1150, "temp": 24.3, "humidity": 50 },
    { "at": "2026-10-19T22:16:00", "co2": 1100, "temp": 24.3, "humidity": 50 }
  ]
}
```

## PATCH /api/actions/{actionId}/check

`PENDING` → `CHECKED`. 이미 CHECKED 면 그대로 성공.

```json
{ "status": "CHECKED" }
```

## POST /api/actions/{actionId}/start

`PENDING`/`CHECKED` → `IN_PROGRESS`. `co2_before` = 최신 CO2, `window_opened_at` = now, `member_id` = 처리자 (로그인 전이라 현재 null).

```json
{
  "actionId": 1,
  "roomId": 7,
  "source": "COMPLAINT",
  "complaintId": 1,
  "summary": "민원: 답답해요 · 창문 열기",
  "co2Before": 1260,
  "expectedCo2After": 800,
  "actualCo2After": null,
  "steps": [ { "type": "WINDOW_OPEN", "label": "창문 열기", "doneAt": "2026-10-19T22:19:14.697" } ],
  "status": "IN_PROGRESS",
  "handledBy": null
}
```

- `handledBy`: 처리자 이름 (비로그인 null)
- `complaintId`: 민원으로 생긴 조치(`source: COMPLAINT`)만 값이 있고, 예측 조치는 null
- `steps`: action 의 시각 컬럼 중 기록된 것만 시간 순.
  `window_opened_at` → `WINDOW_OPEN` "창문 열기", `ac_on_at` → `AC_ON` "냉난방기 켜기", `window_closed_at` → `WINDOW_CLOSE` "창문 닫기"

## POST /api/actions/{actionId}/complete

`IN_PROGRESS` → `DONE`. `actual_co2_after` = 최신 CO2, `window_closed_at`·`completed_at` = now. 연결된 민원(`complaint_id`)도 `DONE`.

```json
{ "co2Before": 1250, "actualCo2After": 780, "goalReached": true, "status": "DONE" }
```

- `goalReached` (임시 정의): `actualCo2After` 가 조치의 `threshold`(없으면 현재 기준값) 미만이면 true

### 조치 공통 에러

- 404 `존재하지 않는 조치입니다.`
- 409 `현재 상태에서는 처리할 수 없는 조치입니다.` (예: DONE 을 다시 start, IN_PROGRESS 를 check)
- check 는 이미 CHECKED 여도 200 (멱등). start/complete 는 같은 요청을 다시 보내면 409
