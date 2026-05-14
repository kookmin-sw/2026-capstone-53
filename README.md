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
- **언어** Java 21 (Toolchain · LTS)
- **프레임워크** Spring Boot 3.5.3, Spring Web MVC, Spring Data JPA, Hibernate ORM, Spring Security, Spring Boot Actuator, Spring Boot DevTools, Jakarta Bean Validation, Java 21 Virtual Threads
- **빌드 / 툴체인** Gradle 8.14.4 (Kotlin DSL), Spring Dependency Management Plugin 1.1.7, Eclipse Temurin 21 (JDK / JRE)
- **DB / 마이그레이션** MySQL 8.x, MySQL Connector/J, Flyway (core + flyway-mysql)
- **인증 / 보안** JJWT 0.12.6 (api / impl / jackson), Spring Security BCrypt, SHA-256 refresh token hashing
- **웹 푸시** nl.martijndwars web-push 5.1.1, BouncyCastle bcprov-jdk18on 1.78, VAPID (RFC 8292)
- **HTTP / 직렬화** Apache HttpClient 5, Jackson (KST `Asia/Seoul`, ISO 8601)
- **유틸리티** Lombok, ulid-creator 5.2.3 (Crockford Base32 ULID)
- **테스트** JUnit 5 (Jupiter), AssertJ, Mockito, Spring Boot Test, Spring Security Test, Testcontainers 1.21.4 (junit-jupiter + mysql), Spring Boot Testcontainers, JUnit Platform Launcher
- **인프라 / 배포** Docker (multi-stage · non-root · `TZ=Asia/Seoul`), AWS EC2, AWS RDS (MySQL), GitHub Container Registry (GHCR)
- **CI** GitHub Actions (Temurin 21 · Gradle cache · `./gradlew build`)
- **외부 API** ODsay (대중교통 경로 / 권장 출발시각), Kakao Local (주소 → 좌표 지오코딩), T-map (보행자 경로 — 인도 곡선), Naver Maps (지도 타일)

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

요구사항: Java 21 · Docker (로컬 MySQL용). 빌드 / 운영(EC2 · RDS · Docker) 상세는 [`backend/README.md`](backend/README.md) 참고.

```bash
cd backend
cp .env.example .env    # DB_PASSWORD, JWT_SECRET 등 채우기
./gradlew bootRun       # http://localhost:8080/actuator/health
```


<a id="team-members"></a>

## <img src="assets/images/todayway-logo.svg" alt="2" width="15">  팀원

|---|---|---|---|
| 원수현 | 임채연 | 황찬우 | 이상진 |
| 20203102 | 20210481 | 20212667 | 20213039 |
| DevOps, 기획, 디자인 | Frontend, UI/UX | Backend, DB | Backend, DB |
