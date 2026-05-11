# MSW (Mock Service Worker) 사용법

백엔드 없이 API 명세서 기반으로 프론트엔드를 개발할 수 있는 mock 서버입니다.

## 시작하기

### 1. MSW 켜기
`.env.development.local`에 다음 추가:
```
REACT_APP_USE_MOCK=true
```
개발 서버 재시작 후 콘솔에 `[MSW] enabled, scenario: default` 메시지가 나오면 성공.

### 2. MSW 끄기 (실 백엔드 사용)
```
REACT_APP_USE_MOCK=false
```
또는 해당 줄을 삭제하면 MSW가 시작되지 않고 실 서버(`REACT_APP_API_URL`)로 요청이 갑니다.

---

## 시드 계정

| 항목 | 값 |
|------|---|
| 아이디 | `testuser` |
| 비밀번호 | `Test1234!` |
| 닉네임 | `테스트유저` |
| memberId | `mem_01HSEED0001ABCDEFGHJKLMN` |

---

## 시드 데이터

| 일정 | 타입 | routeStatus |
|------|------|-------------|
| 국민대 등교 | 단발성 | CALCULATED |
| 토익 학원 | 루틴 (월/수/금) | CALCULATED |
| 동아리 모임 | 단발성 | PENDING_RETRY |

경로: 도보 → 지하철 → 도보 (국민대 등교, 토익 학원)

---

## 시나리오 전환

브라우저 콘솔에서:

```js
// 정상 응답 (기본값)
localStorage.setItem('msw-scenario', 'default')

// 일정 생성 시 경로 계산 대기
localStorage.setItem('msw-scenario', 'route-pending-retry')

// 경로 조회 502 에러
localStorage.setItem('msw-scenario', 'external-route-failed')

// 외부 API 504 타임아웃
localStorage.setItem('msw-scenario', 'external-timeout')

// 모든 인증 호출 401
localStorage.setItem('msw-scenario', 'token-expired')
```

설정 후 **새로고침** 필요.

---

## 파일 구조

```
src/mocks/
├── browser.js          # worker 시작 셋업
├── scenarios.js        # 시나리오 토글 로직
├── handlers/
│   ├── index.js        # 모든 핸들러 통합
│   ├── auth.js         # signup, login, logout
│   ├── members.js      # me, update, delete
│   ├── main.js         # main, mapConfig
│   ├── schedules.js    # CRUD 5개
│   ├── route.js        # route
│   ├── push.js         # subscribe, unsubscribe
│   └── geocode.js      # search
└── data/
    ├── members.js      # 회원 시드 + 토큰 관리
    ├── schedules.js    # 일정 시드
    └── routes.js       # 경로 시드
```

---

## 주의사항

- MSW는 **브라우저의 Service Worker**를 사용합니다. `public/mockServiceWorker.js`가 필요합니다.
- MSW가 켜져 있으면 `http://localhost:8080/api/v1/*` 패턴의 요청만 가로챕니다.
- 기존 `src/api/client.js`의 mock 데이터와는 별개입니다. MSW는 `src/api/index.js` (fetchClient 기반)와 함께 사용합니다.
- 통합 완료 후 `src/mocks/`, `.env.development.local`의 `REACT_APP_USE_MOCK`, `public/mockServiceWorker.js`를 정리하세요.
