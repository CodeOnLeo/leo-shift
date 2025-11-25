# 🔄 Leo Shift

교대 근무자를 위한 스마트 근무표 관리 앱

<div align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white" />
  <img src="https://img.shields.io/badge/PWA-5A0FC8?style=flat-square&logo=pwa&logoColor=white" />
</div>

## ✨ 주요 기능

### 📅 자동 근무표 생성
- 3교대 근무 패턴 자동 계산
- Day(주간) / Afternoon(오후) / Night(야간) / Off(휴무)
- 시작일 설정만으로 전체 근무표 자동 생성

### 🔔 똑똑한 알림
- 근무 시작 전 자동 푸시 알림
- 원하는 시간 설정 가능 (예: 출근 2시간 전)
- 테스트 알림으로 미리 확인

### 📝 유연한 일정 관리
- 날짜별 예외 처리 (연차, 특근 등)
- 메모 기능으로 중요한 일정 기록
- 기념일 등록 및 매년 반복 설정

### 📱 어디서나 편리하게
- PWA 지원으로 앱처럼 사용
- 모바일, 태블릿, 데스크톱 모두 지원
- 오프라인에서도 근무표 확인 가능

### 🔐 안전한 로그인
- Google 계정으로 간편 로그인
- 또는 이메일로 회원가입
- 내 근무표는 나만 볼 수 있어요

## 🎯 이런 분들에게 추천합니다

- 3교대 근무를 하시는 분
- 매번 근무표를 수기로 작성하시는 분
- 다음 근무일을 자주 까먹으시는 분
- 근무 시작 전 알림을 받고 싶으신 분

## 🚀 시작하기

1. **웹사이트 접속**
   - https://leo-shift-production.up.railway.app

2. **로그인**
   - Google 계정으로 간편 로그인
   - 또는 이메일로 회원가입

3. **근무 패턴 설정**
   - 근무 시작일 선택
   - 근무 패턴 입력
   - 알림 시간 설정

4. **완료!**
   - 이제 자동으로 근무표가 생성됩니다
   - 홈 화면에 추가하면 앱처럼 사용 가능

## 💡 사용 팁

### 홈 화면에 추가하기
- **iOS**: Safari에서 공유 버튼 → 홈 화면에 추가
- **Android**: Chrome에서 메뉴 → 홈 화면에 추가

### 알림 받기
- 브라우저 알림 권한 허용 필요
- 알림 시간은 자유롭게 설정 가능
- 테스트 알림으로 미리 확인해보세요

## 🔒 개인정보 보호

- 모든 데이터는 안전하게 암호화되어 저장됩니다
- 근무표 정보는 본인만 접근 가능합니다
- Google 로그인 시 이메일과 이름만 수집합니다

## 🛠 기술 스택

- Backend: Spring Boot, PostgreSQL
- Frontend: Vanilla JavaScript (PWA)
- Security: JWT, OAuth2
- Notification: Web Push API

## 📝 라이선스

개인 사용 프로젝트

---

<div align="center">
  <p>만든 사람: <a href="https://github.com/codeonleo">@codeonleo</a></p>
  <p>문의 또는 버그 제보: <a href="https://github.com/codeonleo/leo-shift/issues">Issues</a></p>
</div>
