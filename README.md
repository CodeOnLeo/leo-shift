# leo-shift

교대 근무자를 위한 Progressive Web App (PWA)

## 주요 기능

- 🔄 교대 근무 패턴 관리 (Day/Afternoon/Night/Off)
- 📅 월간 캘린더 뷰
- 📝 날짜별 메모 및 예외 처리
- 🔔 Web Push 알림 (근무 시작 전 알림)
- 📱 PWA 설치 지원 (모바일/데스크톱)

## 기술 스택

- **Backend:** Java 21, Spring Boot 4.0
- **Database:** H2 (파일 기반, PostgreSQL 호환 모드)
- **Frontend:** Vanilla JavaScript, HTML5, CSS3
- **Push:** Web Push API (VAPID)

## Railway 배포 가이드

### 1. VAPID 키 생성

```bash
npx web-push generate-vapid-keys
```

출력된 Public Key와 Private Key를 복사해두세요.

### 2. Railway 배포

1. [Railway](https://railway.app) 로그인
2. "New Project" → "Deploy from GitHub repo"
3. 저장소 연결

### 3. 환경 변수 설정

Railway 프로젝트 → Variables 탭:

```bash
# VAPID 키 (위에서 생성한 값)
PUSH_VAPID_PUBLIC_KEY=BNxxx...
PUSH_VAPID_PRIVATE_KEY=xxx...
PUSH_VAPID_SUBJECT=mailto:your-email@gmail.com

# 포트 (Railway 자동 설정)
PORT=8080
```

### 4. Volume 설정 (데이터 영구 저장)

Railway 프로젝트 → Settings → Volumes:
- Name: `leoshift-data`
- Mount Path: `/app/data`

`railway.toml`에 설정되어 있어 자동 생성될 수 있습니다.

### 5. GitHub Actions 푸시 알림 설정

**Repository Secret 추가:**

GitHub 저장소 → Settings → Secrets and variables → Actions:
- Name: `RAILWAY_APP_URL`
- Secret: `https://your-app.up.railway.app` (Railway에서 확인)

**테스트:**

Actions 탭 → Push Notification Reminder → Run workflow

**스케줄 변경:**

`.github/workflows/push-reminder.yml` 파일 수정:

```yaml
on:
  schedule:
    - cron: '0 * * * *'  # 매시간 (기본)
    - cron: '*/30 * * * *'  # 30분마다
```

참고: Cron은 UTC 기준 (한국 시간 = UTC + 9시간)

## 로컬 실행

### H2 데이터베이스 (기본)

```bash
./gradlew bootRun
```

앱 실행: http://localhost:8080

### H2 Console (개발용)

```bash
H2_CONSOLE_ENABLED=true ./gradlew bootRun
```

Console: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/leoshift`
- Username: `sa`
- Password: (비어있음)

### PostgreSQL 사용 (선택사항)

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/leo_shift
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export SPRING_DATASOURCE_DRIVER=org.postgresql.Driver

./gradlew bootRun
```

## 비용 최적화

### Railway 무료 크레딧 ($5/월)

- H2 데이터베이스: $0 (무료)
- 앱 실행: ~$0.50-1.00/월
- Volume 1GB: 무료
- 네트워크 100GB: 무료

**예상 총 비용:** 무료 크레딧 내 운영 가능

### GitHub Actions (무료)

- Public 저장소: 완전 무료
- Private 저장소: 월 2,000분 무료 (이 앱은 ~360분/월 사용)

## 문제 해결

### Railway 배포 실패

로컬에서 빌드 테스트:

```bash
./gradlew clean build
docker build -t leo-shift .
docker run -p 8080:8080 leo-shift
```

### 푸시 알림 작동 안 함

1. VAPID 키 환경 변수 확인
2. HTTPS 사용 확인 (Railway는 자동 HTTPS)
3. 브라우저에서 알림 권한 허용 확인
4. `/api/push/public-key` 응답 확인

### 데이터 손실

Railway Logs에서 확인:
- "Creating directory /app/data" 로그
- Volume 마운트 경로: `/app/data`

## API 엔드포인트

### 캘린더
- `GET /api/calendar?year={y}&month={m}` - 월간 캘린더
- `GET /api/today` - 오늘 + 3일
- `GET /api/days/{date}` - 날짜 상세
- `PUT /api/days/{date}` - 날짜 수정

### 설정
- `GET /api/settings` - 패턴 설정 조회
- `PUT /api/settings` - 패턴 저장

### 푸시 알림
- `GET /api/push/public-key` - VAPID 공개 키
- `POST /api/push/subscriptions` - 구독 등록
- `POST /api/push/test-reminder` - 테스트 알림
- `POST /api/push/send-scheduled-reminder` - 스케줄 알림 (GitHub Actions용)

## 라이선스

개인 사용 프로젝트
