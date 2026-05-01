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

## EC2 운영 (Docker)

EC2에서 RDS에 붙어 동작시킬 때:

### 1. `/etc/todayway/app.env` 작성 (root, `chmod 600`)

`.env.example`과 동일 키 구조. 운영 값:

| 키 | 값 |
|---|---|
| `DB_HOST` | RDS endpoint |
| `DB_PORT` | RDS 포트 (보통 `3306`) |
| `DB_NAME` | `routine_commute` |
| `DB_USER` / `DB_PASSWORD` | RDS master credential |
| `DB_SSL_MODE` | `REQUIRED` (RDS 운영 권장) / `PREFERRED` (default, server 지원 시 SSL) / `VERIFY_CA` (RDS CA bundle로 cert 검증) |
| `JWT_SECRET` | `openssl rand -base64 32` 결과 |
| `ODSAY_API_KEY` / `KAKAO_LOCAL_API_KEY` / `VAPID_*` | 외부 키 (도메인 담당자) |

### 2. 이미지 빌드 + 실행

```bash
# EC2에서 직접 빌드 시
docker build -t todayway-backend backend/

# 실행
docker run -d --name todayway \
  --restart=unless-stopped \
  --env-file /etc/todayway/app.env \
  -p 8080:8080 \
  todayway-backend
```

GHCR 이미지를 쓸 경우 `todayway-backend` 자리에 `ghcr.io/kookmin-sw/2026-capstone-53/backend:latest`.

### 3. 검증

```bash
docker logs -f todayway          # Flyway 로그 + Started BackendApplication
curl localhost:8080/actuator/health   # {"status":"UP"}
```

Flyway 로그에 `Successfully applied 1 migration` 또는 `Schema is up to date` 중 하나가 보이면 정상.

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
- **RDS endpoint, master credential 등 운영 시크릿은 README/코드에 박지 않음** — EC2의 `/etc/todayway/app.env`에만 존재
