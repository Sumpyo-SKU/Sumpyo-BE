# 담당 구역 설정(온보딩) · 내 설정 API

공통 래퍼 `{ success, code, message, data }` 중 `data` 만 적는다. 권한: W = WORKER, A = ADMIN.

> **로그인 전 임시**: 모든 요청을 데모 근로생 `worker01`(김근로, WORKER)로 처리한다 (`CurrentMemberProvider`).
> 앱 시작 시 계정이 없으면 환경변수 `DEMO_PASSWORD` 를 BCrypt 로 저장해 만든다.
> `DEMO_PASSWORD` 가 없으면 계정을 만들지 않고, `/api/me/*` 와 `GET /api/actions?zone=mine` 은 401.
> 로그인을 붙이면 `CurrentMemberProvider` 만 바꾸면 된다.

## 온보딩 흐름 (프론트)

1. **화면 1** `GET /api/buildings` → 건물 버튼 12개 (`display_order` 순). 층 버튼은 선택한 건물의 `maxFloor` 까지
2. **[시작하기] → 화면 2** 칩 "북악관 · 5층"
   1. 담당 구역 저장 → `PUT /api/me/zones`
   2. 강의실 센서 연결 중 → `GET /api/buildings/{buildingId}/floors/{floor}`
   3. 환기 알림 켜기 → `PUT /api/me/settings` `{ "alertEnabled": true }`
- 앱 진입 시 `GET /api/me/zones` 가 `[]` 이면 온보딩을 띄운다
- 캠퍼스 현황 탭은 처음 열 때 `GET /api/me/zones` 의 **첫 번째 값**(`buildingId`, `floors[0]`)을 기본 선택으로 쓴다

---

## GET /api/buildings

```json
[
  { "buildingId": 2,  "name": "대일관", "maxFloor": 7 },
  { "buildingId": 3,  "name": "문예관", "maxFloor": 7 },
  { "buildingId": 4,  "name": "본관",   "maxFloor": 7 },
  { "buildingId": 1,  "name": "북악관", "maxFloor": 7 },
  { "buildingId": 5,  "name": "상승관", "maxFloor": 7 },
  "... 유담관, 은주1관, 은주2관, 청운관, 한림관",
  { "buildingId": 11, "name": "혜인관", "maxFloor": 5 },
  { "buildingId": 12, "name": "수인관", "maxFloor": 7 }
]
```

- 순서는 `building.display_order`. `buildingId` 는 순서와 무관하니 이름/순서로 하드코딩하지 말 것
- `maxFloor` 는 혜인관(5)만 확인된 값이고 나머지는 임시 7 (V5 TODO)

## GET /api/me/zones — 권한 W

```json
[ { "buildingId": 1, "buildingName": "북악관", "floors": [5] } ]
```

- 담당 구역이 없으면 `[]`. 건물 표시 순서 → 층 오름차순

## PUT /api/me/zones — 권한 W

보낸 목록으로 **통째로 교체** (기존 구역 삭제 후 저장, 한 트랜잭션). `[]` 를 보내면 전부 삭제.

```json
[ { "buildingId": 1, "floors": [5] } ]
```

응답은 GET 과 같은 형식 (저장된 값).

| 경우 | 응답 |
|---|---|
| 없는 `buildingId` | 404 `존재하지 않는 건물입니다.` |
| `floors` 가 비었거나 1~maxFloor 밖 | 400 `층은 1층부터 건물 최고 층 사이에서 하나 이상 골라야 합니다.` |
| 같은 건물 두 번 / 같은 층 두 번 | 400 `같은 건물이나 층을 중복해서 보낼 수 없습니다.` |

검증에 실패하면 기존 구역은 그대로 남는다.

## GET /api/me/settings — 권한 W, A

```json
{ "co2Threshold": 1000, "cautionFrom": 900, "criticalFrom": 1200,
  "tempMin": 20, "tempMax": 26, "humidityMin": 40, "humidityMax": 60,
  "alertEnabled": true, "alertLeadMinutes": 15 }
```

- `cautionFrom` = 기준 × 0.9, `criticalFrom` = 기준 × 1.2 (소수점 올림). 저장하지 않고 계산하며,
  `co2Level`(CAUTION/CRITICAL) 판정과 같은 값이다

## PUT /api/me/settings — 권한 W, A

**보낸 값만 바뀐다** (안 보낸 필드는 그대로). `cautionFrom`/`criticalFrom` 은 보내도 무시.

```json
{ "alertEnabled": true }
```
```json
{ "co2Threshold": 1100, "tempMin": 20, "tempMax": 25, "humidityMin": 40, "humidityMax": 60,
  "alertEnabled": true, "alertLeadMinutes": 20 }
```

응답은 GET 과 같은 형식 (`co2Threshold` 1100 이면 `cautionFrom` 990, `criticalFrom` 1320).

| 경우 | 응답 |
|---|---|
| `co2Threshold` 가 600~1500 밖 | 400 `co2Threshold 는 600~1500 사이여야 합니다.` |
| `alertLeadMinutes` 가 10·15·20 이 아님 | 400 `알림 시점은 10·15·20분 중 하나여야 합니다.` |
| 바뀐 뒤 기준으로 `tempMin ≥ tempMax` 또는 `humidityMin ≥ humidityMax` | 400 `최솟값은 최댓값보다 작아야 합니다.` |

## GET /api/actions?zone=mine&status=

조치 목록, `expectedAt` 오름차순 (급한 것부터). 예상 시각이 없는 조치(민원)는 생성 시각으로 정렬한다.

- `zone`: 없으면 전체, `mine` 이면 내 담당 구역(`member_zone` 의 건물+층)과 같은 방만. 그 외 값은 400.
  담당 구역이 없으면 `[]`
- `status`: 쉼표로 여러 개 (`PENDING,CHECKED`). 없으면 `PENDING,CHECKED,IN_PROGRESS`

```json
[
  { "actionId": 88, "roomId": 42, "room": "북악관 618호",
    "source": "PREDICTION", "recommend": "OPEN",
    "timing": "NOW", "title": "창문 열어주세요", "reason": "10분 뒤 기준 초과 예정",
    "metric": "CO2", "predictedValue": 1250, "threshold": 1000,
    "expectedAt": "2026-10-16T14:10:00", "status": "PENDING" }
]
```

| 필드 | 규칙 |
|---|---|
| `timing` | `expectedAt` 이 지났거나 10분 이내 `NOW`, 30분 이내 `SOON`, 그 외 `LATER` (`expectedAt` 이 없으면 `NOW`) |
| `title` | `OPEN` → `창문 열어주세요`, `CLOSE` → `창문 닫아주세요` |
| `reason` | `COMPLAINT` 조치 → `학생 민원 접수`. 그 외 `expectedAt` 이 지났으면 `기준 초과`, 아니면 `N분 뒤 기준 초과 예정` (N 은 올림) |
| `threshold` | 조치 생성 당시 기준값. 없으면 현재 사용자 기준값 |
