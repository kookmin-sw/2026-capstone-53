# `.github` 폴더 안내

BusReminder 프로젝트의 GitHub 협업·자동화 설정.

## 📁 구성

```
.github/
├── ISSUE_TEMPLATE/
│   ├── feature.yml       # ✨ 기능 개발
│   ├── bug.yml           # 🐛 버그 리포트
│   ├── task.yml          # 📋 일반 작업
│   └── config.yml        # 빈 이슈 차단
├── workflows/
│   ├── frontend-ci.yml   # 프론트 린트/테스트/빌드 (PR + main)
│   ├── backend-ci.yml    # 백엔드 빌드/테스트 + GHCR 이미지 푸시
│   ├── backend-deploy.yml  # ⭐ self-hosted runner로 EC2 배포
│   └── labeler.yml       # PR 자동 라벨링
├── PULL_REQUEST_TEMPLATE.md
├── CODEOWNERS            # 경로별 자동 리뷰어
├── CONTRIBUTING.md       # 브랜치/커밋/PR 규칙
├── labeler.yml           # 라벨러 규칙
└── dependabot.yml        # 의존성 자동 업데이트
```

## 🌿 브랜치 전략

**GitHub Flow** — `main` 단일 기본 브랜치.

```
main (보호됨)
 ↑ PR + 리뷰 + CI 통과 후 머지
feature/*, fix/*, chore/* 브랜치들
```

- 모든 작업은 feature 브랜치에서 진행
- PR 생성 시 CI가 자동 검증 (빌드/테스트)
- 코드 리뷰 1명 이상 + CI 통과 후 main 머지
- main 머지 즉시 자동 배포

## 🚀 배포 흐름

### Backend
```
PR 머지 → main 푸시
   ↓
backend-ci.yml (ubuntu-latest)
   - JAR 빌드 + 테스트
   - Docker 이미지 빌드 → GHCR 푸시 (latest, sha-<해시>)
   ↓
backend-deploy.yml (self-hosted runner = 학교 EC2)
   - GHCR에서 이미지 pull
   - 컨테이너 재시작
   - /actuator/health 헬스체크
```

### Frontend
AWS Amplify GitHub 연동으로 자동 배포. main 푸시 시 Amplify가 자체 빌드/배포.
별도 GitHub Actions 워크플로우 불필요.

## 🔐 학교 계정 제약과 자격증명 설계

**원칙**: AWS Access Key 발급 정책을 침해하지 않는다.

| 자격증명 | 위치 | 비고 |
|---|---|---|
| AWS Access Key | ❌ 사용 안 함 | 학교 정책상 발급 불가 |
| `GITHUB_TOKEN` | 워크플로우 자동 발급 | GHCR 푸시/풀 인증용 |
| EC2 SSH 키 | EC2 인스턴스에 직접 등록 | runner 등록 시 1회만 사용 |
| 앱 환경변수 | EC2 `/etc/busreminder/app.env` | DB 비밀번호 등. 컨테이너에 `--env-file`로 주입 |

self-hosted runner는 EC2 내부에서만 동작하므로 자격증명이 외부로 새지 않음.

## 🔧 사용 전 설정해야 할 것

### 1. Self-hosted runner 등록 (1회)

학교 EC2에서:
```bash
# GitHub: Settings → Actions → Runners → New self-hosted runner
mkdir actions-runner && cd actions-runner
curl -o actions-runner-linux-x64.tar.gz -L \
  https://github.com/actions/runner/releases/download/<버전>/actions-runner-linux-x64-<버전>.tar.gz
tar xzf ./actions-runner-linux-x64.tar.gz
./config.sh --url https://github.com/kookmin-sw/2026-capstone-53 --token <발급받은_토큰>
# 라벨에 'busreminder-ec2' 추가 (workflow의 runs-on과 일치 필요)
sudo ./svc.sh install
sudo ./svc.sh start
```

### 2. EC2 환경변수 파일 작성 (1회)

```bash
sudo mkdir -p /etc/busreminder
sudo tee /etc/busreminder/app.env > /dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod

# DB (RDS MySQL 8.x)
DB_URL=jdbc:mysql://<RDS_엔드포인트>:3306/routine_commute?useSSL=true&serverTimezone=UTC&characterEncoding=utf8
DB_USERNAME=...
DB_PASSWORD=...

# JWT (자체 회원가입/인증)
JWT_SECRET=...
JWT_ACCESS_TOKEN_TTL_MINUTES=30
JWT_REFRESH_TOKEN_TTL_DAYS=14

# 외부 API
ODSAY_API_KEY=...
NAVER_LOCAL_CLIENT_ID=...
NAVER_LOCAL_CLIENT_SECRET=...
KAKAO_LOCAL_REST_API_KEY=...

# Web Push (VAPID)
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...
VAPID_SUBJECT=mailto:admin@example.com

# Google OAuth는 P1 확장 시점에 추가
# GOOGLE_OAUTH_CLIENT_ID=...
# GOOGLE_OAUTH_CLIENT_SECRET=...
EOF
sudo chmod 600 /etc/busreminder/app.env
```

### 3. GHCR 패키지 가시성 설정

GHCR 이미지를 EC2에서 pull하려면 패키지 권한 필요. 첫 푸시 후:
- `kookmin-sw/2026-capstone-53/backend` 패키지 → Settings
- 레포에 연결되어 있으면 자동으로 GITHUB_TOKEN으로 pull 가능

### 4. Branch Protection Rules

`Settings → Branches → main`:
- ✅ Require a pull request before merging
- ✅ Require approvals (최소 1명)
- ✅ Require status checks to pass (`Backend CI / Build & Test`, `Frontend CI / lint-and-test`)
- ✅ Require conversation resolution before merging
- ✅ Do not allow bypassing the above settings

### 5. Frontend Amplify 연동

AWS Amplify Console에서 GitHub 레포 연결, main 브랜치 설정. 빌드 설정은 Amplify가 자동 감지.

## 🐛 트러블슈팅

**Q. 배포 후 헬스체크 실패**
- `docker logs busreminder-backend` 로 로그 확인
- 환경변수 누락 가능성 → `/etc/busreminder/app.env` 확인
- RDS 보안그룹(app-rds-sg)에서 EC2 보안그룹의 3306 인바운드 허용됐는지 확인

**Q. self-hosted runner가 offline 상태**
- EC2에서 `sudo systemctl status actions.runner.<...>` 확인
- 재시작: `sudo ./svc.sh restart`

**Q. GHCR에서 pull 실패 (401)**
- GITHUB_TOKEN의 packages:read 권한 확인
- 패키지가 private인 경우 레포와 연결됐는지 확인

## 💡 추가로 활용할 것

- **GitHub Projects**: 이슈 칸반으로 일정 시각화
- **Milestones**: Phase별로 생성해서 이슈 분류
- **Discussions**: 의사결정 기록 (이벤트 스토밍, API 설계 논의 등)
