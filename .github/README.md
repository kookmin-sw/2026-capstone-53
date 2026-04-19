# `.github` 폴더 안내

이 폴더는 GitHub 저장소의 협업·자동화 설정을 담고 있습니다.

## 📁 구성

```
.github/
├── ISSUE_TEMPLATE/
│   ├── feature.yml       # ✨ 기능 개발 이슈
│   ├── bug.yml           # 🐛 버그 리포트
│   ├── task.yml          # 📋 일반 작업
│   └── config.yml        # 빈 이슈 생성 차단
├── workflows/
│   ├── frontend-ci.yml   # 프론트 린트/테스트/빌드
│   ├── backend-ci.yml    # 백엔드 빌드/테스트 (Java/Spring 예시)
│   ├── frontend-deploy.yml  # develop 머지 시 S3/CloudFront 배포
│   └── labeler.yml       # PR 자동 라벨링
├── PULL_REQUEST_TEMPLATE.md
├── CODEOWNERS            # 경로별 자동 리뷰어
├── CONTRIBUTING.md       # 브랜치/커밋/PR 규칙
├── labeler.yml           # 라벨러 규칙
└── dependabot.yml        # 의존성 자동 업데이트
```

## ⚙️ 사용 전 꼭 수정할 것

### 1. `CODEOWNERS`
팀원 GitHub 아이디로 교체. (`@YOUR_FRONTEND_DEV_1` 등)

### 2. `workflows/*.yml`
실제 프로젝트 구조에 맞춰 경로와 빌드 명령을 수정.
- 백엔드가 Node.js/Python이라면 `backend-ci.yml`의 `setup-java` 부분을 교체.
- 프로젝트 폴더명이 `frontend`/`backend`가 아니라면 `paths`와 `working-directory` 수정.

### 3. Repository Secrets 등록
`Settings → Secrets and variables → Actions`에서 아래 항목 등록:
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION`
- `S3_BUCKET_DEV`
- `CLOUDFRONT_DISTRIBUTION_ID_DEV`

### 4. Branch Protection Rules
`Settings → Branches`에서 `main`, `develop`에 아래 규칙 적용 권장:
- Require a pull request before merging
- Require approvals (최소 1명)
- Require status checks to pass (CI 통과)
- Require conversation resolution before merging

### 5. `ISSUE_TEMPLATE/config.yml`
Discussions URL을 실제 저장소 주소로 교체.

## 💡 추가로 고려할 것

- **GitHub Projects**: 이슈 칸반 보드로 4주 일정 시각화
- **Milestones**: 1주차/2주차/3주차/4주차로 생성해서 이슈 분류
- **Discussions**: 의사결정 기록용 (이벤트 스토밍 결과, API 설계 논의 등)
