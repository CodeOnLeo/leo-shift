# CalDAV Integration Plan

## Goal

Leo Shift의 `일반 일정 캘린더`에 대해 CalDAV 양방향 동기화를 지원한다.

이번 설계의 핵심은 다음과 같다.

1. CalDAV 대상은 `patternEnabled = false` 인 일반 일정 캘린더만 한정한다.
2. 외부 캘린더 클라이언트의 생성/수정/삭제를 Leo Shift 내부 데이터에 반영한다.
3. 교대 캘린더는 이번 범위에서 제외한다.

## Scope

### In Scope

- 일반 일정 캘린더에 대한 CalDAV 컬렉션 제공
- 캘린더 목록 조회
- 이벤트 조회
- 이벤트 생성
- 이벤트 수정
- 이벤트 삭제
- 주간 반복 규칙을 CalDAV 반복 이벤트로 노출
- 개별 날짜 예외를 override 이벤트로 반영

### Out of Scope

- 교대 근무 캘린더 CalDAV 지원
- 앱 전체 캘린더를 하나의 CalDAV 피드로 합치는 기능
- 고급 ACL / principal 관리
- 메모 전체의 완전한 round-trip 보장
- 푸시 기반 실시간 sync

## Why General Calendars First

일반 일정 캘린더는 현재 구조상 CalDAV와 가장 잘 맞는다.

- `calendar_weekly_rules`는 RRULE 기반 반복 일정으로 매핑 가능
- 날짜별 예외는 exception/override 이벤트로 매핑 가능
- 교대 패턴처럼 앱 내부 전용 규칙을 외부 표준에 억지로 맞출 필요가 없다

즉 1차 목표는 `일반 캘린더를 외부 캘린더 앱에서 수정 가능한 캘린더로 만든다`이다.

## Current Internal Model

현재 일반 일정 캘린더는 대략 다음 구조를 사용한다.

- `calendars`
  - `pattern_enabled = false`
- `schedule_types`
  - 예: `WORK`, `OFF`, `REMOTE`
- `calendar_weekly_rules`
  - 요일별 기본 일정
- `exceptions`
  - 특정 날짜의 일정 override 및 메모

이 구조는 다음처럼 해석한다.

- `calendar_weekly_rules` = 기본 반복 시리즈
- `exceptions` = 특정 날짜 수정 또는 예외

## CalDAV Mapping

### Calendar Level

Leo Shift의 일반 일정 캘린더 1개를 CalDAV calendar collection 1개로 매핑한다.

권장 원칙:

- 교대 캘린더는 CalDAV discovery 대상에서 제외
- 일반 일정 캘린더만 컬렉션으로 노출

### Event Level

CalDAV에서는 `VEVENT`를 기준으로 읽고 쓴다.

Leo Shift 일반 캘린더에서는 다음 두 계층으로 해석한다.

1. 기본 반복 이벤트
2. 개별 날짜 override 이벤트

### Weekly Rules -> VEVENT + RRULE

요일별 기본 일정은 RRULE 기반 반복 이벤트로 매핑한다.

예시:

- 월요일 `WORK`
- 화요일 `WORK`
- 수요일 `REMOTE`

이를 각 일정 타입별 반복 이벤트로 표현할 수 있다.

예시 전략:

- `WORK`가 월~금 반복이면 하나의 VEVENT + `RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR`
- `OFF`가 토/일 반복이면 하나의 VEVENT + `RRULE:FREQ=WEEKLY;BYDAY=SA,SU`

주의:

- 현재 내부 모델은 `요일별 코드`라서, CalDAV에서는 “같은 코드가 연속된 요일”을 묶어서 하나의 RRULE 이벤트로 내보내는 정규화가 필요하다.

### Exceptions -> Override Events

특정 날짜에 기본 규칙과 다른 일정이 있으면 override VEVENT로 표현한다.

예시:

- 평소 월요일 `WORK`
- 특정 월요일만 `OFF`

이 경우:

- 기본 RRULE 이벤트는 유지
- 해당 날짜에 RECURRENCE-ID 기반 override 이벤트 생성

### Memos

메모는 우선 `DESCRIPTION`으로 제한적으로 매핑한다.

권장 1차 원칙:

- `exceptions.memo` -> `VEVENT.DESCRIPTION`
- anniversary/고급 메모 구조는 1차 범위에서 부분 지원 또는 제외

## Write-back Rules

### External Create

외부 CalDAV 클라이언트에서 일반 일정 캘린더에 새 이벤트를 만들면:

1. 반복 없는 단일 이벤트면 `exceptions`에 날짜 override로 저장
2. 주간 반복 이벤트면 `calendar_weekly_rules` 또는 별도 규칙 레이어로 변환 시도

권장 1차 제한:

- 외부 생성은 `단일 이벤트`와 `주간 반복`만 허용
- 그 외 복잡한 RRULE은 거부하거나 단순화

### External Update

외부 수정 시 다음 규칙을 적용한다.

- 기본 반복 이벤트 수정:
  - 해당 RRULE이 표현하는 요일 집합을 다시 `calendar_weekly_rules`로 역매핑
- 단일 날짜 override 수정:
  - 해당 날짜 `exceptions` 업데이트

### External Delete

- 기본 반복 이벤트 삭제:
  - 관련 `calendar_weekly_rules` 제거 또는 `OFF`로 변경하는 정책 필요
- override 이벤트 삭제:
  - 해당 날짜 `exceptions` 제거

권장 1차 정책:

- RRULE 삭제는 해당 요일 규칙 제거
- 단일 override 삭제는 해당 날짜 예외 제거

## Supported CalDAV Semantics

1차에서 명시적으로 지원할 범위:

- `VEVENT`
- `RRULE:FREQ=WEEKLY`
- `BYDAY`
- `RECURRENCE-ID`
- `ETag`
- `Last-Modified`
- 기본 CalDAV sync

1차에서 제한할 범위:

- 복잡한 monthly/yearly RRULE
- VTODO
- VALARM full round-trip
- 참석자/초대 응답

## Sync and Conflict Policy

CalDAV는 양방향이므로 충돌 정책이 필요하다.

권장 정책:

- 모든 VEVENT에 내부 `UID` 유지
- 서버 응답에 `ETag` 포함
- 업데이트는 `If-Match` 기반 처리
- 충돌 시 `412 Precondition Failed`

1차 충돌 규칙:

- 마지막 수정 시간보다 ETag 우선
- 클라이언트가 오래된 ETag로 수정하면 실패 반환

## Authentication

기존 앱 JWT를 CalDAV 클라이언트에 직접 쓰는 것은 적합하지 않다.

권장 방식:

- 사용자별 CalDAV 전용 app password 또는 token 발급
- Basic Auth 또는 Bearer 대체 토큰 방식 사용

권장 1차:

- `username + app-specific password`
- 토큰은 DB에 해시 저장

## API / Protocol Layers

구성 권장:

1. CalDAV discovery endpoint
2. calendar-home-set
3. 일반 일정 캘린더 collection
4. VEVENT read/write adapter
5. 내부 모델 변환 계층

내부 서비스 분리 권장:

- `CalDavCalendarService`
- `CalDavEventService`
- `CalDavSyncService`
- `GeneralCalendarEventMapper`

## Data Conversion Strategy

권장 변환 계층:

- Internal `calendar_weekly_rules` -> CalDAV recurring VEVENT
- Internal `exceptions` -> CalDAV override VEVENT
- External VEVENT -> internal rule/exception mutation

중요한 원칙:

- CalDAV 계층이 직접 엔티티를 수정하지 않는다
- 반드시 변환 서비스와 도메인 서비스 사이를 통해 반영한다

## Delivery Plan

### Phase A: Design Lock

- 일반 캘린더만 대상 확정
- 매핑 규칙 확정
- 인증 방식 확정

### Phase B: Read-only Prototype

- CalDAV discovery
- 일반 일정 캘린더 목록
- VEVENT 조회

### Phase C: Write Support

- 단일 이벤트 생성/수정/삭제
- 주간 반복 이벤트 생성/수정/삭제
- ETag 기반 충돌 처리

### Phase D: Hardening

- 다중 클라이언트 테스트
- iOS/macOS Calendar 테스트
- Thunderbird 테스트
- edge case 정리

## Risks

### 1. Internal Model Mismatch

현재 내부 일반 일정 모델은 `요일 규칙 + 날짜 예외` 중심이다.
CalDAV 클라이언트는 event-centric 모델이라 완전한 round-trip이 항상 자연스럽지는 않다.

### 2. Complex RRULE Support

외부 클라이언트가 weekly 외의 복잡한 RRULE을 보낼 수 있다.
이를 모두 지원하려 하면 범위가 급격히 커진다.

### 3. Memo / Metadata Loss

Leo Shift 내부 메모 구조가 CalDAV 속성으로 완전 대응되지 않을 수 있다.

## Recommendation

지금 기준 추천 방향:

1. 일반 일정 캘린더만 CalDAV 지원
2. weekly rule + date override 중심으로 제한
3. 복잡한 반복 규칙은 1차에서 제외
4. app password 기반 인증 사용
5. 먼저 read-only prototype 후 write support로 확장

## Next Step

다음 구현 전에 필요한 작업:

1. CalDAV 인증 방식 문서 확정
2. 일반 일정 캘린더의 이벤트 매핑 예시 3~5개 고정
3. read-only prototype 엔드포인트 설계
4. iOS Calendar 기준 상호 운용성 테스트 계획 작성
