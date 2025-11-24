# Railway 환경 변수 설정

Railway 배포가 실패하면 다음 환경 변수들이 올바르게 설정되었는지 확인하세요.

## 필수 환경 변수

### 1. VAPID 키 (Web Push용)

```bash
# 생성 명령어
npx web-push generate-vapid-keys
```

Railway Variables 설정:

```bash
PUSH_VAPID_PUBLIC_KEY=BNxxxxxxxxxxxxxxxxx...
PUSH_VAPID_PRIVATE_KEY=xxxxxxxxxxxxxxxxx...
PUSH_VAPID_SUBJECT=mailto:your-email@gmail.com
```

**중요:**
- Public Key는 `BN`으로 시작합니다
- Private Key는 Base64 문자열입니다
- Subject는 실제 연락 가능한 이메일을 사용하세요

### 2. 포트 (자동 설정)

Railway가 자동으로 설정하므로 **수동 설정 불필요**:

```bash
PORT=8080  # Railway가 자동으로 할당
```

### 3. 데이터베이스 (자동 설정)

Dockerfile에 이미 설정되어 있으므로 **수동 설정 불필요**:

```bash
SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/leoshift;MODE=PostgreSQL;...
```

## 선택 환경 변수

### H2 Console (개발용)

프로덕션에서는 **비활성화 권장**:

```bash
H2_CONSOLE_ENABLED=false  # 기본값
```

개발/디버깅 시에만 활성화:

```bash
H2_CONSOLE_ENABLED=true
```

## 환경 변수 확인 방법

### Railway 대시보드

1. 프로젝트 선택
2. **Variables** 탭
3. 다음 항목이 있는지 확인:
   - `PUSH_VAPID_PUBLIC_KEY`
   - `PUSH_VAPID_PRIVATE_KEY`
   - `PUSH_VAPID_SUBJECT`

### Railway CLI

```bash
railway variables
```

## 일반적인 문제

### 1. "Missing VAPID keys" 로그

**원인:** VAPID 환경 변수가 설정되지 않음

**해결:**
```bash
railway variables set PUSH_VAPID_PUBLIC_KEY="BNxxx..."
railway variables set PUSH_VAPID_PRIVATE_KEY="xxx..."
railway variables set PUSH_VAPID_SUBJECT="mailto:your-email@gmail.com"
```

### 2. Healthcheck 실패

**원인:** 앱이 시작되지 않거나 포트가 맞지 않음

**확인:**
- Railway Logs에서 "Started LeoShiftApplication" 메시지 확인
- PORT 환경 변수가 자동으로 설정되는지 확인
- `/health` 엔드포인트 응답 확인

### 3. 데이터베이스 오류

**원인:** Volume이 마운트되지 않음

**확인:**
- Settings → Volumes → `/app/data` 마운트 확인
- Logs에서 "jdbc:h2:file:/app/data/leoshift" 확인

## 로그 확인

```bash
# Railway CLI
railway logs

# 또는 Railway 대시보드
Deployments → Latest → Logs
```

다음 메시지가 보이면 정상:

```
Started LeoShiftApplication in X.XXX seconds
```

## 테스트

배포 후 다음 URL들이 응답하는지 확인:

```bash
# Health check
curl https://your-app.up.railway.app/health

# VAPID 공개 키
curl https://your-app.up.railway.app/api/push/public-key

# 오늘 일정
curl https://your-app.up.railway.app/api/today
```
