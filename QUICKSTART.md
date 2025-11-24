# 빠른 시작 가이드

## 5분 안에 배포하기

### 1. VAPID 키 생성

```bash
npx web-push generate-vapid-keys
```

Public Key와 Private Key를 복사하세요.

### 2. Railway 배포

1. [railway.app](https://railway.app) 접속 및 로그인
2. "New Project" → "Deploy from GitHub repo"
3. 이 저장소 선택

### 3. 환경 변수 설정

Variables 탭에서 추가:

```bash
PUSH_VAPID_PUBLIC_KEY=BN...    # 1단계에서 복사한 Public Key
PUSH_VAPID_PRIVATE_KEY=...     # 1단계에서 복사한 Private Key
PUSH_VAPID_SUBJECT=mailto:your-email@gmail.com
```

### 4. GitHub Actions 설정

GitHub → Settings → Secrets → Actions:

```
Name: RAILWAY_APP_URL
Value: https://your-app.up.railway.app  # Railway에서 확인
```

### 5. 완료!

배포된 URL로 접속하여 앱을 사용하세요.

---

## 사용법

1. **설정** 페이지에서 근무 패턴 입력 (예: D,A,N,O,D,A,N,O,O)
2. **시작 날짜** 선택
3. **알림 시간** 설정 (근무 시작 몇 분 전)
4. 브라우저에서 **알림 권한 허용**
5. 모바일에서 **홈 화면에 추가**

## 문제 발생 시

- Railway Logs 확인
- GitHub Actions 실행 로그 확인
- [README.md](README.md) 참고
