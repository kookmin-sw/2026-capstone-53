# Mock 데이터 사용 현황 리포트

## 1. Mock 데이터 소스

| 파일 | 역할 |
|------|------|
| `src/data/mockData.js` | 모든 mock 데이터 정의 (20+ exports) |
| `src/api/client.js` | `IS_MOCK` 분기로 17개 엔드포인트 mock 반환 |

> `src/api/client.js`의 17개 함수는 **어떤 컴포넌트에서도 import되지 않음**.
> 모든 페이지가 `mockData.js`에서 직접 import하는 방식이라 client.js는 사실상 미사용 상태.

---

## 2. 컴포넌트별 Mock 사용 현황

| 파일 | Mock import | 사용 패턴 | 대응 API | 난이도 |
|------|-------------|-----------|----------|--------|
| **HomeV2Route.jsx** | `mockRouteInfoList`, `mockMember`, `mockTodaySchedules` | 직접 반복/속성 접근 | GET /main, GET /members/me | 보통 |
| **Calendar.js** | `mockSchedules`, `mockRouteInfo`, `mockGeocodeResults` | useState 초기값 + 로컬 검색함수 | GET /schedules, POST /geocode | 보통 |
| **Settings.js** | `mockMember` | 직접 속성 접근 | GET /members/me, PATCH /members/me | 쉬움 |
| **MapPage.jsx** | `mockRouteData` | 상수로 segments 추출 | GET /schedules/{id}/route | 쉬움 |
| **NotificationPage.jsx** | `mockNotifications` | useState 초기값 | API 미정의 | 쉬움 |
| **SmokeTest.jsx** | `api` (간접) | 테스트 전용 | 다수 | 해당없음 |
| **ScheduleCard.js** | 없음 | props 수신 | — | 해당없음 |
| **RouteCard.js** | 없음 | props 수신 | — | 해당없음 |
| **KakaoMap.js** | 없음 | props 수신 | — | 해당없음 |
| **TopNav / BottomNav / StateUI** | 없음 | — | — | 해당없음 |

---

## 3. 상세 분석

### HomeV2Route.jsx — 보통

- `mockRouteInfoList`를 캐러셀로 직접 순회 (전체 화면 데이터 구조)
- `mockMember.data.nickname`으로 인사말 표시
- `mockTodaySchedules`로 오늘 타임라인 렌더링
- **마이그레이션**: GET /main 응답 + GET /schedules 조합으로 대체, 데이터 shape 변환 필요

### Calendar.js — 보통

- `useState(mockSchedules)` — 일정 리스트 상태의 초기값
- `searchMockGeocode()` — mockGeocodeResults 딕셔너리 로컬 검색
- `mockRouteInfo` — RoutePreviewSheet에서 직접 참조
- **마이그레이션**: useState를 빈 배열로 시작 + useEffect에서 `api.schedules.list()` 호출, 검색은 `api.geocode.search()` 호출로 대체

### Settings.js — 쉬움

- `mockMember.data`에서 nickname, loginId만 읽음
- ProfileEditSheet에 `member={mockMember.data}` props 전달
- **마이그레이션**: useEffect에서 `api.members.me()` 호출 → state에 저장

### MapPage.jsx — 쉬움

- `mockRouteData.data.candidates[0].segments`를 상수로 추출
- 세그먼트 path 좌표로 지도 폴리라인 렌더링
- **마이그레이션**: scheduleId를 받아 `api.route.get(id)` 호출

### NotificationPage.jsx — 쉬움

- `useState(mockNotifications)` 초기값으로 알림 목록 세팅
- 날짜별 그룹핑 후 렌더링
- **주의**: 알림 목록 API는 명세에 없음 (별도 설계 필요하거나 프론트 전용 유지)

---

## 4. 마이그레이션 권장 순서

| 순서 | 컴포넌트 | 이유 |
|------|----------|------|
| 1 | **LoginPage / SignupPage** | 화면 흐름 시작점, mock 직접 import 없음, `api.auth`만 연결 |
| 2 | **Settings.js** | mock 1개(`mockMember`), 단순 속성 접근 |
| 3 | **MapPage.jsx** | mock 1개(`mockRouteData`), 상수 추출만 |
| 4 | **NotificationPage.jsx** | mock 1개, useState 교체만 (API 미정의 주의) |
| 5 | **Calendar.js** | mock 3개, 검색 함수 교체 + useState 교체 |
| 6 | **HomeV2Route.jsx** | mock 3개, 데이터 shape 변환 필요, 캐러셀 구조 |

---

## 5. 참고

- 새로 만든 API 래퍼: `src/api/index.js` (`api.auth.*`, `api.members.*` 등)
- 새로 만든 MSW mock 서버: `src/mocks/` (환경변수 `REACT_APP_USE_MOCK=true`로 활성화)
- 마이그레이션 시 `src/data/mockData.js` import를 `src/api`의 api 호출로 교체하면 됨
