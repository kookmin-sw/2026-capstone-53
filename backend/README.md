# TodayWay Backend

오늘어디 캡스톤 — Spring Boot 단일 백엔드 (공통 초기 세팅).

## 스택

| 항목 | 버전 |
|---|---|
| Java | 21 LTS |
| Spring Boot | 3.5.3 |
| Build | Gradle (Kotlin DSL) |
| DB | MySQL 8.x |
| ORM | Spring Data JPA |

## 포함 의존성

`build.gradle.kts` 참고:
`web`, `data-jpa`, `mysql-connector`, `validation`, `lombok`,
`actuator`, `devtools`, `configuration-processor`

## 로컬 실행

### 1. 환경변수
```bash
cp .env.example .env
# .env 파일 열어서 DB_PASSWORD 등 채우기
```

### 2. MySQL (Docker)
```bash
docker run --name todayway-mysql -d \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=routine_commute \
  -p 3306:3306 mysql:8.0
```

### 3. 실행
```bash
export $(cat .env | xargs)
./gradlew bootRun
```

→ http://localhost:8080/actuator/health 로 헬스 체크

## 도메인 패키지

본인 도메인 패키지를 직접 추가해서 작업합니다 (예: `com.todayway.backend.{도메인}`).
외부 API 키, 도메인별 설정은 담당자가 `application.yml`에 직접 추가.

> 도메인 분담은 회의에서 결정. 본 README에는 별도로 적지 않음 (변경될 수 있음).

## 브랜치

- `main` — 통합
- `feat/backend` — 백엔드 공통 초기 세팅
- 도메인 작업은 별도 브랜치 권장 (예: `feat/backend-auth`, `feat/backend-map`)

## 주의

- `.env` 는 절대 커밋 금지 (`.gitignore` 등록됨)
- 외부 API 키 / DB 패스워드는 환경변수로만 주입
