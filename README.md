<p align="center">
  <img src="assets/images/todayway-typo.svg" alt="Todayway" width="300" />
</p>

<p align="center">
  매일 당신의 곁에 함께하는 경로 알림 서비스
</p>

<p align="center">
  <a href="https://kookmin-sw.github.io/2026-capstone-53/">
    <img src="https://img.shields.io/badge/Website-Visit-2ea44f?style=for-the-badge" alt="Website">
  </a>
</p>

## 목차

- [프로젝트 소개](#project-intro)
- [기술 스택](#tech-stack)
- [사용 방법](#usage)
- [팀원](#team-members)


<a id="project-intro"></a>

## <img src="assets/images/todayway-logo.svg" alt="2" width="15">  프로젝트 소개

<!-- 프로젝트 소개 문구를 추후 작성합니다. -->

<!-- 프로젝트 소개 이미지나 기타 asset을 아래에 추가합니다. -->

<!-- 예시:
![프로젝트 대표 이미지](assets/README/project-overview.png)
-->


<a id="tech-stack"></a>

## <img src="assets/images/todayway-logo.svg" alt="2" width="15">  기술 스택

### 프론트엔드
- React (Create React App)
- JavaScript + JSDoc 타입 힌트
- react-router-dom v6, 카카오맵 JavaScript SDK
- MSW (Mock Service Worker) — 개발 환경 mock 서버
- JWT Bearer + localStorage 기반 인증
- PWA (Progressive Web App)

### 백엔드
<!-- 백엔드 팀이 추후 작성 -->

<!-- 예시:
![Tech Stack](https://skillicons.dev/icons?i=react,spring,mysql,docker,aws)
-->


<a id="usage"></a>

## <img src="assets/images/todayway-logo.svg" alt="2" width="15">  사용 방법

### 프론트엔드 실행

요구사항: Node.js (LTS 권장), npm

```bash
cd frontend
npm install
npm start   # http://localhost:3000 (카카오맵 API 키 등록 포트)
```

환경변수 (`frontend/.env.development.local`):
```
REACT_APP_API_URL=http://localhost:8080/api/v1
REACT_APP_USE_MOCK=true   # MSW 사용 시 (백엔드 없이 개발 가능)
```

MSW 모드:
- 시드 계정: `testuser` / `Test1234!`
- 시나리오 토글 (브라우저 콘솔):
```js
localStorage.setItem('msw-scenario', '<value>'); location.reload();
// 가능 값: default, route-pending-retry, external-route-failed, external-timeout, token-expired
```

### 백엔드 실행
<!-- 백엔드 팀이 추후 작성 -->


<a id="team-members"></a>

## <img src="assets/images/todayway-logo.svg" alt="2" width="15">  팀원

<!-- 섹션 번호는 추후 아래와 같이 에셋으로 교체할 수 있습니다. -->
<!-- <img src="assets/README/section-4.png" alt="4" width="48"> -->

| <img src="https://github.com/wonsh200.png" width="120" alt="원수현"> | 사진~ | 사진~ | 사진~ |
|---|---|---|---|
| 원수현 | 임채연 | 이상진 | 황찬우 |
| 20203102 | 20210481 | 20213039 | 20212667 |
| DevOps, 기획, 디자인 | Frontend, UI/UX | Backend, DB | Backend, DB |
