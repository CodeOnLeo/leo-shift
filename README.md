# Leo Shift

반복 일정, 교대 근무, 예외 메모, 공유 캘린더를 함께 관리할 수 있는 일정 앱입니다.

<div align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white" />
  <img src="https://img.shields.io/badge/PWA-5A0FC8?style=flat-square&logo=pwa&logoColor=white" />
</div>

## 현재 지원 기능

### 캘린더 유형
- `교대 근무 캘린더`
- `일반 일정 캘린더`

일반 일정 캘린더는 주간 반복 규칙 기반으로 사용할 수 있고, 별도의 `비어 있는 캘린더` 템플릿은 제거되었습니다.

### 일정 관리
- 교대 패턴 기반 월간 일정 생성
- 일반 캘린더용 주간 반복 일정
- 날짜별 예외 처리
- 메모 및 기념일 메모
- 일정 타입별 범례와 월간 요약

### 캘린더 탐색
- `내 캘린더` / `공유받은 캘린더` 분리
- 공유받은 캘린더 소유자별 그룹화
- 캘린더가 여러 개일 때 첫 진입용 선택 화면

### 공유
- 사용자 이메일 초대
- 보기 / 편집 권한
- 공유 그룹 생성
- 공유 그룹 멤버 관리
- 캘린더에 그룹 권한 부여

## 현재 상태 요약

현재 코드는 다음 단계까지 반영된 상태입니다.

- 일반 일정 캘린더와 교대 캘린더 분리
- 기존 잘못 생성된 일반 캘린더 보정 마이그레이션 포함
- 공유 그룹 백엔드/프런트 1차 구현 완료
- 접근 권한 계산에 `직접 공유 + 그룹 공유` 반영

아직 남아 있는 작업은 주로 완성도 영역입니다.

- 공유 관리 화면 UX 추가 정리
- `calendar_shares`와 `calendar_share_grants` 모델 정리
- 테스트 보강

## 시작하기

1. 웹사이트 접속
   - https://leo-shift-production.up.railway.app

2. 로그인
   - Google 계정 로그인
   - 또는 이메일 회원가입

3. 캘린더 생성
   - `일반 일정`
   - 또는 `교대 근무`

4. 사용 시작
   - 캘린더가 여러 개면 먼저 캘린더를 선택
   - 교대 캘린더는 패턴 구성
   - 일반 캘린더는 주간 일정 또는 예외 메모로 사용

## 기술 스택

- Backend: Spring Boot, PostgreSQL
- Frontend: Vanilla JavaScript
- Security: JWT, OAuth2
- Client: PWA

## 문서

- [범용 일정 앱 확장 리팩터링 설계안](docs/generalization-refactor-plan.md)
- [캘린더 공유 기능 구현 계획](docs/calendar-sharing-implementation-plan.md)

## 라이선스

개인 사용 프로젝트
