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
```
REACT_APP_KAKAO_MAP_KEY=카카오맵 API 키
REACT_APP_API_URL=백엔드 API 주소 (미설정 시 mock 데이터 사용)
```

## 폴더 구조

```
src/
├── api/
│   └── client.js            API 연동 모듈 (mock/실제 전환)
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
│   └── mockData.js          mock 데이터
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
`.env`에 `REACT_APP_API_URL` 을 설정하면 mock → 실제 API로 자동 전환됩니다.  
미설정 시 `src/data/mockData.js`의 mock 데이터를 사용합니다.  
API 명세: `backend/docs/api-spec.md` 참고

## 디자인 버전 이력
- v1 (확정): 벤토 그리드 + 경로 지도
- v2: AI 대화형 → archive
- v3: 감성 미니멀 → archive
- v4: 다크 카드 스택 → archive
- v5: 풀맵 → archive
