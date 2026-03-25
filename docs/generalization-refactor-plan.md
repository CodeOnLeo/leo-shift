# Leo Shift 범용 일정 앱 확장 리팩터링 설계안

## 목적

Leo Shift를 `교대 근무표 앱`에서 `반복 일정 + 예외 처리 + 공유 캘린더`를 지원하는 범용 일정 앱으로 확장한다.

이 설계안은 기존 교대 근무 사용자 경험을 유지하면서도 일반 사용자, 가족, 소규모 팀이 함께 사용할 수 있도록 제품 개념과 도메인 모델을 재정의하는 것을 목표로 한다.

## 현재 구조의 한계

### 제품 포지셔닝

- README, 로그인 화면, PWA 메타데이터가 모두 `교대 근무자용 앱`을 전제로 한다.
- 첫 진입 UX가 패턴 설정 중심이라 일반 사용자는 시작부터 이탈할 가능성이 높다.

### 도메인 모델

- 근무 코드가 `D/A/N/V/O`로 고정되어 있다.
- 월간 요약과 범례도 고정 코드 집계에 의존한다.
- 사용자 설정과 캘린더 규칙이 혼재되어 있다.
  - `user_settings.pattern_codes`
  - `user_settings.pattern_start_date`
  - `calendar_patterns`
- 캘린더 패턴이 없으면 사용자 설정으로 fallback 하는 구조라 캘린더 독립성이 약하다.

### UI/UX

- 패턴 빌더가 `주간 -> 휴무 -> 오후 -> 휴무 -> 야간 -> 휴무` 흐름을 기본으로 강제한다.
- `패턴 없음` 캘린더는 가능하지만 제품의 주류 시나리오로 설계되어 있지 않다.
- 코드 라벨, 시간 범위, 범례가 고정 문구라 일반 일정을 표현하기 어렵다.

## 리팩터링 방향

핵심 원칙은 다음 세 가지다.

1. 앱의 중심을 `근무 코드`에서 `일정 타입`으로 이동한다.
2. 패턴은 핵심 기능이 아니라 `여러 반복 규칙 중 하나`로 재정의한다.
3. 개인 설정과 캘린더 규칙을 명확히 분리한다.

## 목표 제품 정의

새 기준의 Leo Shift는 아래와 같은 사용 사례를 모두 지원해야 한다.

- 교대 근무 패턴 관리
- 평일 출근 / 주말 휴무 같은 반복 일정
- 격주 일정, 당번, 알바, 육아 분담
- 운동, 학습, 약속 등 개인 루틴 관리
- 가족 또는 소규모 팀의 공유 일정 관리

제품 설명은 다음 수준으로 재정의한다.

> 반복 일정을 쉽게 만들고, 예외와 메모를 관리하고, 필요한 사람과 공유할 수 있는 일정 캘린더

## 권장 아키텍처

### 1. 도메인 축 변경

현재:

- `ShiftCodeDefinition`
- `CalendarPattern`
- `ShiftException`

변경 후:

- `ScheduleType`
- `CalendarRule`
- `ScheduleException`

여기서 중요한 점은 `교대 근무`가 별도 제품이 아니라 `특정 템플릿과 규칙 조합`으로 표현되어야 한다는 것이다.

### 2. 개인 설정과 캘린더 설정 분리

`UserSettings`는 개인 환경만 보관한다.

- 기본 캘린더
- 알림 기본값
- 색상 태그
- 개인 UI 선호 설정

`Calendar` 및 그 하위 엔티티는 캘린더 자체의 규칙을 보관한다.

- 일정 타입 정의
- 반복 규칙
- 예외 규칙
- 공유 권한

## 데이터 모델 설계

### 신규 엔티티 제안

#### `schedule_types`

캘린더별 일정 타입 정의 테이블

권장 컬럼:

- `id`
- `calendar_id`
- `code`
- `name`
- `color`
- `start_time`
- `end_time`
- `counts_as_work`
- `sort_order`
- `is_default_off`
- `created_at`
- `updated_at`

예시:

- `WORK` / 출근 / 09:00 / 18:00
- `REMOTE` / 재택 / 09:00 / 18:00
- `GYM` / 운동 / 19:00 / 20:00
- `OFF` / 휴무
- `D` / 주간 / 06:00 / 14:00

#### `calendar_rules`

캘린더별 반복 규칙 테이블

권장 컬럼:

- `id`
- `calendar_id`
- `rule_type`
- `start_date`
- `payload`
- `priority`
- `active`
- `created_at`
- `updated_at`

`rule_type` 예시:

- `CYCLIC_PATTERN`
- `WEEKLY_REPEAT`
- `MANUAL_ONLY`

`payload` 예시:

- cyclic: `["D","D","D","O"]`
- weekly: `{ "mon":"WORK", "tue":"WORK", "wed":"WORK", "thu":"WORK", "fri":"WORK", "sat":"OFF", "sun":"OFF" }`

#### `schedule_exceptions`

기존 `exceptions`를 일반화한 개념

권장 컬럼:

- `id`
- `calendar_id`
- `author_id`
- `date`
- `schedule_type_code`
- `memo`
- `anniversary_memo`
- `repeat_yearly`
- `created_at`
- `updated_at`

기존 테이블을 rename 하거나, 새 테이블로 옮긴 뒤 점진 전환한다.

### 기존 엔티티 처리 방안

#### `user_settings`

다음 컬럼은 제거 대상이다.

- `pattern_codes`
- `pattern_start_date`

이 정보는 캘린더 규칙으로 이동해야 한다.

#### `calendar_patterns`

즉시 삭제하지 말고 마이그레이션 단계에서 유지한다.

- 1차: 읽기 전용 호환 레이어 유지
- 2차: `calendar_rules`로 이관
- 3차: 서비스 코드에서 의존 제거
- 4차: 최종 삭제

## 백엔드 설계

### 1. 고정 enum 제거

현재 [ShiftCodeDefinition.java](/Users/hanbyeolko/dev/src/leo-shift/src/main/java/io/github/codeonleo/leoshift/service/ShiftCodeDefinition.java)의 책임이 너무 크다.

대체 방향:

- 고정 enum 대신 `ScheduleTypeDefinition` DTO 또는 엔티티 조회 기반 구조 사용
- 코드에서 라벨, 시간 범위, 근무 여부를 계산하지 않고 DB 또는 서비스 레이어에서 조회

초기 호환 전략:

- 기존 `D/A/N/V/O`를 기본 시드 데이터로 제공
- 새 캘린더 생성 시 템플릿에 따라 `schedule_types` 자동 생성

### 2. 규칙 계산기 분리

현재 `ShiftCalculationService`는 순환 패턴 계산만 담당한다.

이를 `ScheduleRuleResolver` 계층으로 확장한다.

제안 구조:

- `ScheduleRuleResolver`
- `CyclicPatternResolver`
- `WeeklyRepeatResolver`
- `ManualOnlyResolver`

동작 방식:

1. 캘린더의 활성 규칙 조회
2. 규칙 타입별 resolver 선택
3. 기본 일정 타입 계산
4. 예외 데이터로 override
5. 메모와 작성자 정보 병합

### 3. 월간 캘린더 응답 일반화

현재 [CalendarService.java](/Users/hanbyeolko/dev/src/leo-shift/src/main/java/io/github/codeonleo/leoshift/service/CalendarService.java)는 요약 키를 `D/A/N/O`로 고정한다.

변경 방향:

- summary를 동적 map으로 계산
- 범례 정보도 API에서 함께 전달
- 일정 타입별 메타데이터를 응답에 포함

예시 응답 구조:

```json
{
  "configured": true,
  "days": [],
  "summary": {
    "WORK": 20,
    "REMOTE": 4,
    "OFF": 7
  },
  "scheduleTypes": [
    {
      "code": "WORK",
      "name": "출근",
      "color": "#2563EB",
      "startTime": "09:00",
      "endTime": "18:00",
      "countsAsWork": true
    }
  ]
}
```

### 4. 설정 API 재구성

현재 [SettingsController.java](/Users/hanbyeolko/dev/src/leo-shift/src/main/java/io/github/codeonleo/leoshift/controller/SettingsController.java)는 사실상 패턴 API다.

권장 분리:

- `/api/settings`
  - 개인 설정 전용
- `/api/calendars/{id}/schedule-types`
  - 일정 타입 관리
- `/api/calendars/{id}/rules`
  - 반복 규칙 관리
- `/api/calendars/{id}/notifications`
  - 캘린더 수준 알림이 필요할 때 확장 가능

초기 단계에서는 다음처럼 점진 전환이 가능하다.

- 기존 `/api/settings` 유지
- 내부 구현은 `calendar_rules` 기반으로 변경
- 이후 명확한 캘린더 API로 분리

## 프런트엔드 설계

### 1. 온보딩 재설계

현재는 패턴 사용 여부가 첫 결정이다.

변경 후 첫 진입 선택지는 아래가 적절하다.

- `반복 일정으로 시작`
- `교대 근무 템플릿 사용`
- `비어 있는 캘린더로 시작`

### 2. 템플릿 중심 UX

패턴 입력보다 템플릿 선택이 먼저 와야 한다.

권장 기본 템플릿:

- 평일 출근
- 주 3회 루틴
- 교대 근무
- 수동 관리

템플릿 선택 후 상세 설정으로 들어간다.

예:

- 평일 출근: 월-금 09:00-18:00, 토/일 휴무
- 교대 근무: 현재 패턴 빌더 사용
- 수동 관리: 패턴 없이 달력부터 사용

### 3. 패턴 빌더 위치 조정

현재 [pattern.js](/Users/hanbyeolko/dev/src/leo-shift/src/main/resources/static/js/pattern.js)는 기본 입력 폼 역할을 한다.

변경 방향:

- 패턴 빌더는 `교대 근무` 템플릿 전용 도구로 이동
- 일반 사용자에게는 보이지 않도록 분리
- 반복 규칙 편집 UI를 별도 제공

### 4. 범례와 요약 UI 동적화

현재 [index.html](/Users/hanbyeolko/dev/src/leo-shift/src/main/resources/static/index.html)의 범례는 하드코딩되어 있다.

변경 방향:

- 범례는 API가 내려주는 일정 타입 목록으로 렌더링
- 요약도 일정 타입 기준으로 동적 렌더링
- 색상, 라벨, 시간대를 공통 컴포넌트처럼 사용

### 5. 용어 재정비

변경 권장 용어:

- `근무 패턴` -> `반복 규칙`
- `근무 코드` -> `일정 타입`
- `교대 근무표` -> `일정 캘린더`
- `패턴 없이 보기` -> `수동 일정으로 시작`

교대 근무 관련 표현은 템플릿 내부에서만 유지한다.

## 마이그레이션 전략

### 단계 1. 호환 가능한 기반 추가

- `schedule_types` 추가
- `calendar_rules` 추가
- 기존 캘린더마다 기본 일정 타입 시드 생성
- 기존 `calendar_patterns`를 읽어 `calendar_rules`로 복제
- 기존 `exceptions.custom_code`를 새 일정 타입 코드와 호환되게 유지

이 단계에서는 기존 UI와 API를 유지할 수 있다.

### 단계 2. 서비스 레이어 전환

- `CalendarPatternService`를 `CalendarRuleService`로 대체
- `ShiftCalculationService`를 resolver 구조로 분리
- `CalendarService`가 동적 summary와 일정 타입 메타데이터를 반환하도록 변경
- `TodayService`, `DayDetailService`도 새 타입 모델 사용

### 단계 3. 프런트 전환

- 온보딩 템플릿 도입
- 범례 동적화
- 패턴 빌더를 교대 템플릿 전용 UI로 이동
- 일반 일정 생성 UI 추가

### 단계 4. 레거시 제거

- `user_settings.pattern_codes` 제거
- `user_settings.pattern_start_date` 제거
- `calendar_patterns` 제거
- `ShiftCodeDefinition` 제거 또는 호환 레이어로 축소

## API 전환 예시

### 캘린더 생성

기존:

- 캘린더 생성 후 패턴 설정

변경 후:

- 캘린더 생성 시 `templateType` 선택

예시:

```json
{
  "name": "내 일정",
  "templateType": "WEEKDAY_WORK"
}
```

또는

```json
{
  "name": "내 근무표",
  "templateType": "SHIFT_WORK",
  "templateConfig": {
    "pattern": ["D", "D", "D", "O", "A", "A", "O", "N", "N", "O", "O"]
  }
}
```

### 일정 타입 조회

```json
[
  {
    "code": "WORK",
    "name": "출근",
    "color": "#2563EB",
    "startTime": "09:00",
    "endTime": "18:00",
    "countsAsWork": true
  },
  {
    "code": "OFF",
    "name": "휴무",
    "color": "#94A3B8",
    "startTime": null,
    "endTime": null,
    "countsAsWork": false
  }
]
```

## 테스트 전략

### 백엔드

- 순환 패턴 계산 테스트
- 주간 반복 계산 테스트
- 예외 override 테스트
- 동적 summary 계산 테스트
- 레거시 패턴 데이터 호환 테스트

### 프런트엔드

- 템플릿 선택 플로우
- 수동 캘린더 시작 플로우
- 교대 템플릿 설정 플로우
- 범례/요약 동적 렌더링 테스트

## 추천 구현 순서

### 1차 릴리즈

- 제품 문구 완화
- 온보딩에서 `수동 일정으로 시작` 노출
- `패턴 없음` 시나리오 강화

### 2차 릴리즈

- `schedule_types` 도입
- 동적 범례/요약 적용
- 고정 enum 의존 축소

### 3차 릴리즈

- `calendar_rules` 도입
- 주간 반복 / 순환 패턴 공존
- 템플릿 기반 캘린더 생성

### 4차 릴리즈

- 레거시 패턴 구조 제거
- 일반 사용자 중심 IA 정리

## 결정 사항 요약

- 추천 전략은 `중간 리팩터링안`이다.
- 교대 근무는 버리지 않고 `템플릿과 규칙의 한 종류`로 유지한다.
- 제품의 중심 엔티티는 `근무 코드`가 아니라 `일정 타입`이어야 한다.
- 사용자 설정에서 패턴 관련 정보를 제거하고 캘린더 규칙으로 귀속시킨다.
- 프런트는 패턴 중심 UX에서 템플릿 중심 UX로 전환한다.

## 후속 작업 제안

이 문서를 기준으로 다음 산출물을 이어서 만들 수 있다.

- DB 마이그레이션 초안
- 엔티티/DTO 변경안
- API 명세 초안
- 온보딩 와이어프레임
- 단계별 구현 티켓 분해
