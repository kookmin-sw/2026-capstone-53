# 오늘어디 — Frontend

## 기술 스택
- React
- 카카오맵 JavaScript SDK
- PWA (Progressive Web App)

## 실행 방법
```bash
cd todayway-frontend
npm install
npm start
```
localhost:3000에서 확인 가능

## 환경변수 (.env)
`.env.example` 참조. `.env.development.local`에 작성 권장.
```
REACT_APP_API_URL=http://localhost:8080/api/v1   # 백엔드 API 베이스 URL
REACT_APP_USE_MOCK=true                          # true 시 MSW mock 서버 활성화 (백엔드 없이 개발 가능)
REACT_APP_KAKAO_MAP_KEY=카카오맵 API 키           # localhost:3000에서만 동작
```

## 폴더 구조

```
src/
├── api/
│   ├── fetchClient.js       핵심 fetch 래퍼 (토큰 자동 첨부, 401 처리)
│   ├── errors.js            ApiError 클래스 + ErrorCode 한국어 매핑
│   └── index.js             도메인별 API 함수 (api.auth.*, api.schedules.* 등)
├── archive/                 이전 디자인 버전 (v2~v5, 참고용)
│   ├── HomeV2~V5.jsx
│   ├── CalendarV2~V5B.jsx
│   └── RouteMapV3.jsx
├── components/
│   ├── BottomNav.js         하단 탭 바
│   ├── KakaoMap.js          카카오맵 래퍼
│   ├── RecommendationCard.js 추천 카드
│   ├── RouteCard.js         벤토 그리드 경로 카드
│   ├── ScheduleCard.js      일정 카드
│   ├── StateUI.jsx          로딩/빈 상태/에러 공통 UI
│   └── TopNav.js            상단 내비게이션 바
├── contexts/
│   ├── SettingsContext.js   앱 설정 전역 상태
│   └── ThemeContext.js      다크모드 전역 상태
├── data/
│   ├── mockData.js          레거시 mock 데이터 (archive용)
│   └── notifications.js     알림 내역 (프론트 전용, API 미정의)
├── hooks/
│   └── usePushNotification.js  PWA 푸시 알림 권한/구독 관리
├── mocks/                   MSW mock 서버 (REACT_APP_USE_MOCK=true 시 활성화)
│   ├── browser.js           worker 셋업
│   ├── scenarios.js         시나리오 토글
│   ├── handlers/            17개 엔드포인트 핸들러
│   └── data/                시드 데이터 (회원, 일정, 경로)
├── types/
│   └── api.js               API 타입 정의 (JSDoc, 17개 엔드포인트)
├── pages/
│   ├── HomeV2Route.jsx      홈 (벤토 그리드 + 경로 지도)
│   ├── Calendar.js          캘린더 + 일정 목록
│   ├── Settings.js          설정
│   ├── MapPage.jsx          경로 지도 (풀스크린)
│   ├── LoginPage.jsx        로그인
│   ├── SignupPage.jsx        회원가입
│   └── NotificationPage.jsx 알림 내역
├── styles/
│   └── dark.css             다크모드 오버라이드
├── App.js                   라우팅
└── index.css                글로벌 CSS 변수 및 베이스 스타일
```

## 페이지 구성
| 경로 | 화면 | 설명 |
|------|------|------|
| `/` | 홈 | 벤토 그리드 + 경로 지도 |
| `/calendar` | 캘린더 | 달력 + 카드 스택 |
| `/settings` | 설정 | 프로필 + 알림 |
| `/map` | 경로 지도 | 풀스크린 지도 |
| `/login` | 로그인 | |
| `/signup` | 회원가입 | |
| `/notifications` | 알림 | 알림 내역 |

## 백엔드 연동
API 명세 v1.1.11 기준, 17개 엔드포인트 통합 완료.
- 인증: signup, login, logout
- 회원: me, update, delete
- 일정: create, list, get, update, delete
- 경로: get (scheduleId별)
- 푸시: subscribe, unsubscribe
- 장소검색: geocode
- 메인/지도: main, mapConfig

`.env`에 `REACT_APP_API_URL` 설정 시 실 서버 연동.  
`REACT_APP_USE_MOCK=true` 시 MSW mock 서버로 백엔드 없이 개발 가능.  
API 명세: `backend/docs/api-spec.md` 참고

### 실 백엔드 연결 시 필요한 것
1. CORS 화이트리스트에 `http://localhost:3000` 추가
2. 테스트 계정 생성 (시드 일정 2-3개 포함 권장)
3. `.env`의 `REACT_APP_API_URL`을 서버 주소로 변경, `REACT_APP_USE_MOCK` 제거 또는 false

## 개발 워크플로우

### MSW mock 서버
`REACT_APP_USE_MOCK=true`로 설정 후 `npm start`하면 MSW가 활성화됩니다.  
콘솔에 `[MSW] enabled, scenario: default` 출력 확인.

시드 계정: `testuser` / `Test1234!`

시나리오 토글 (브라우저 콘솔에서):
```js
localStorage.setItem('msw-scenario', 'default')              // 정상 응답
localStorage.setItem('msw-scenario', 'route-pending-retry')   // 경로 계산 대기
localStorage.setItem('msw-scenario', 'external-route-failed') // 경로 502 에러
localStorage.setItem('msw-scenario', 'external-timeout')      // 외부 API 504
localStorage.setItem('msw-scenario', 'token-expired')         // 모든 인증 401
```
설정 후 새로고침 필요.

### 카카오맵 SDK
- API 키가 `localhost:3000`에서만 동작 (포트 3001 등은 차단)
- `public/index.html`에 SDK 스크립트 태그 포함

## 디자인 버전 이력
- v1 (확정): 벤토 그리드 + 경로 지도
- v2: AI 대화형 → archive
- v3: 감성 미니멀 → archive
- v4: 다크 카드 스택 → archive
- v5: 풀맵 → archive
