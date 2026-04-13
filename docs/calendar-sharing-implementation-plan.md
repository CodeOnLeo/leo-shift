# Calendar Sharing Implementation Plan

## Goal

캘린더 수와 공유 대상이 늘어나도 관리 가능한 구조로 정리한다.

1. `내 캘린더`와 `공유받은 캘린더`를 명확히 구분한다.
2. 반복되는 개별 공유를 줄일 수 있도록 `공유 그룹` 확장 기반을 만든다.
3. 작은 단위로 나눠 안전하게 배포한다.

## Phase 1: Ownership Grouping

목표:

- 캘린더 선택 UI에서 `내 캘린더`와 `공유받은 캘린더`를 분리한다.
- API도 같은 의미 구조를 제공한다.
- 기존 평면 리스트 응답은 유지한다.

작업:

- `CalendarListResponse`에 `ownedCalendars`, `sharedCalendars` 추가
- 캘린더 셀렉터 그룹 헤더 추가
- 기존 공유 초대/수락 로직은 유지

완료 기준:

- 캘린더가 많아도 소유 여부를 즉시 구분할 수 있다.
- 기존 캘린더 로딩/선택 흐름이 깨지지 않는다.

## Phase 2: Shared Calendar Subgrouping

목표:

- `공유받은 캘린더`를 소유자별로 다시 묶는다.

작업:

- 응답 DTO 또는 프런트에서 owner 기준 그룹화
- 셀렉터에서 `공유받은 캘린더 > 소유자명` 구조 표시

완료 기준:

- 여러 사람에게서 공유받아도 찾기 쉽다.

## Phase 2A: Calendar Entry Picker

목표:

- 캘린더가 여러 개일 때 특정 캘린더를 바로 열지 않고 먼저 선택하게 한다.

작업:

- 첫 진입 시 캘린더 개수 확인
- 2개 이상이면 `캘린더 선택 화면` 표시
- 사용자가 선택한 뒤 해당 캘린더를 로드
- 캘린더가 0개면 기존 생성 온보딩 유지

완료 기준:

- 첫 진입에서 현재 볼 캘린더를 명시적으로 선택할 수 있다.
- `내 캘린더`와 `공유받은 캘린더` 구분이 선택 화면에도 유지된다.

## Phase 3: Share Target Generalization

목표:

- 공유 대상을 `사용자`와 `그룹`으로 일반화한다.

도메인 방향:

- `ShareTargetType`: `USER`, `GROUP`
- `CalendarShareGrant`
  - calendar_id
  - target_type
  - target_id
  - permission
- `ShareGroup`
  - id
  - owner_user_id
  - name
- `ShareGroupMember`
  - group_id
  - user_id

핵심 규칙:

- 직접 공유와 그룹 공유를 함께 평가한다.
- 여러 경로로 권한이 들어오면 가장 높은 권한을 적용한다.
- 그룹 멤버 제거 시 그룹 기반 권한은 즉시 회수한다.
- 직접 공유가 남아 있으면 접근은 유지한다.

구현 순서:

1. `share_groups`, `share_group_members`, `calendar_share_grants` 테이블 추가
2. 엔티티와 리포지토리 골격 추가
3. 그룹 생성/수정/멤버 관리 서비스 추가
4. 캘린더 접근 권한 계산을 `직접 공유 + 그룹 공유`로 확장
5. 이후 기존 `calendar_shares`를 점진적으로 정리하거나 마이그레이션

현재 턴 범위:

- Phase 3 설계 문서 보강
- DB 마이그레이션 추가
- 엔티티/리포지토리 골격 추가
- 서비스 로직과 UI는 다음 단위에서 진행

## Phase 4: Share Management UX

목표:

- 공유 관리 화면에서 사용자와 그룹을 함께 관리한다.

작업:

- 사용자 초대
- 그룹 생성/수정
- 그룹 대상 공유
- 공유 출처 표시

## Delivery Strategy

권장 순서:

1. Phase 1
2. Phase 2A
3. Phase 2
4. Phase 3
5. Phase 4

이 순서를 쓰는 이유:

- Phase 1은 UX 개선 효과가 크고 리스크가 낮다.
- Phase 3부터는 권한 모델과 DB 마이그레이션이 같이 필요하다.
- 공유 그룹은 UI보다 권한 충돌 규칙 정의가 더 중요하다.

## Current Turn Scope

이번 작업에서는 Phase 1, Phase 2A, Phase 2를 구현했고, Phase 3 골격을 추가한다.

- 문서 추가
- API ownership 그룹 응답 추가
- 캘린더 셀렉터 그룹화 적용
- 다중 캘린더 진입 시 선택 화면 추가
- 공유받은 캘린더 소유자별 그룹화 적용
- 공유 그룹용 DB/엔티티/리포지토리 골격 추가

## Follow-up Checklist

- 공유 그룹 엔티티/마이그레이션 설계
- 직접 공유와 그룹 공유의 권한 병합 테스트 추가
- 공유 관리 UI에서 공유 출처 표시
