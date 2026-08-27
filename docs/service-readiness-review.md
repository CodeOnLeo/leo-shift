# Leo Shift 서비스화 검토

작성일: 2026-08-10 · 대상: `main` (7babee1) · 함께 보기: [domain-design.md](domain-design.md), [feature-spec.md](feature-spec.md)

> **⚠️ 정정 (2026-08-10, [domain-design.md](domain-design.md) 참조)**
>
> 아래 두 가지는 **폐기됐다.** 도메인 모델은 `domain-design.md`를 기준으로 볼 것.
>
> - **2.1의 `calendar_members` 제안** — 캘린더가 여러 멤버를 담는 구조는 "프로젝트 단위로 인원이 바뀌는" 실제 요구를 못 견딘다. 프로젝트를 옮기면 휴가·근무를 다시 입력해야 한다. 올바른 모델은 **개인 캘린더가 진실의 원천, 그룹은 뷰**다.
> - **4.1의 "이벤트 레이어를 만들지 말 것"(해석 B)** — 일반 근무자가 메인 캘린더로 쓰는 게 목적이므로 해석 A로 간다.
>
> **0.1 적용 범위**도 "소수 지인용" 전제라 완화가 과하다. 사용자 확대를 전제로 하면 계정 삭제, 레이트 리밋, XSS 전면 대응, 약관 등이 되살아난다 — 다만 지금 만들 게 아니라 **나중에 못 만들게 막지 않는 것**이 지금 할 일이다. `domain-design.md` 5.2 참조.
>
> **나머지는 전부 유효하다.** 1장(보안), 2.2~2.7(구조적 결함), 3장(반복 규칙과 프리셋), 5장(클라이언트), 6장(데이터·운영)은 그대로 읽으면 된다.

---

## 0. 결론

두 가지를 분리해서 봐야 한다.

**(A) 서비스로 공개하기 전에 반드시 막아야 하는 것** — 재작성과 무관하게, 지금 구조든 새 구조든 필요한 보안·안정성 항목이다. 그중 몇 개는 사용자 데이터가 실제로 위험한 수준이다. 1장.

**(B) 재작성으로 풀어야 하는 것** — 전면 재작성 판단은 맞다. 다만 이유는 코드 품질이 아니라 **데이터 모델의 전제 세 가지가 제품이 하려는 일과 어긋나 있기 때문**이다. 기능을 붙일수록 어긋남이 커지는 구조라서, 지금 고치는 게 맞다. 2장 이후.

바꿔야 할 전제:

| # | 지금의 전제 | 바뀌어야 할 전제 |
|---|---|---|
| 1 | 캘린더 1개 = **1명**의 근무표 | 캘린더 1개 = **N명의 멤버**, 멤버마다 자기 스케줄 |
| 2 | 하루 = **코드 1개** | 하루 = 근무 배정 1개 + **이벤트 N개** |
| 3 | 패턴 = 교대 근무 전용 기능 | 반복 규칙 = **단일 원시 타입**, 교대/주5일은 그 위의 프리셋 |

세 가지를 고치면 "교대근무자용 앱"이 "교대근무자도 잘 쓰는 공유 캘린더"가 된다. 안 고치면 지금처럼 사람별 데이터가 필요할 때마다 테이블을 하나씩 더 붙이게 된다 — 이미 그렇게 되고 있다(2.1).

---

## 0.1 적용 범위 (읽기 전에)

이 문서의 우선순위는 아래 운영 전제에 맞춰 조정돼 있다.

- **홈서버 자체 호스팅**, Railway에서 이관
- **아는 사람 소수**가 초대로 사용 (현재 테스터 8명)
- 앱스토어 배포나 불특정 다수 공개 **계획 없음**
- 개발·운영 인원 1명

이 전제에서 **떨어지는 것들** — 아래 항목은 발견 사실로는 유효하지만 지금 할 일은 아니다.

| 항목 | 이유 |
|---|---|
| 이용약관 / 개인정보처리방침 | 사업자로 불특정 다수에게 제공하는 게 아니라 실질적 의무가 없다 |
| 앱스토어 심사 대응 (`user-scalable=no` 등) | 배포 계획 없음. 단 본인이 불편하면 고치면 되는 수준 |
| i18n / 다국어 | 한국어 하드코딩으로 계속 가도 된다. 다만 UI 문자열을 **제어 흐름 키로 쓰는 것**(5.1)은 별개 문제로 고쳐야 한다 |
| 스크린리더 / WCAG 준수 | 실제 사용자 중 필요한 사람이 없다면 후순위 |
| 감사 로그, 소프트 삭제, 테넌트 스코핑 | 8명 규모에서 과설계 |
| 레이트 리밋 (1.9) | Cloudflare Tunnel 앞단에서 공짜로 얻는 걸로 충분 |
| 계정 열거 방지, 이메일 인증 | 초대제라면 무의미 |
| 정교한 권한 모델 | 신뢰하는 지인끼리면 VIEW/EDIT 두 단계로 충분 |

이 전제에서 **오히려 더 중요해지는 것들**.

| 항목 | 이유 |
|---|---|
| **SSRF (1.2)** | 홈서버는 공유기 관리 페이지·NAS·다른 서비스와 **같은 내부망**에 있다. Railway의 격리된 컨테이너와는 위험도가 다르다. **이관 전 필수** |
| **백업 (6.6)** | Railway 관리형 Postgres의 안전망이 사라진다. 아무도 대신 해주지 않는다 |
| **멤버 모델 (2.1)** | 지인 소수가 쓰는 공유 캘린더라면 "우리 팀 근무표 같이 보기"가 사실상 **유일한 사용 이유**다. 개인용이면 구글 캘린더를 쓰면 된다 |
| **알림 (6.10)** | 소수여도 매일 앱을 열게 만드는 유일한 훅 |
| **타임존 (6.9)** | 지인들이 지금 실제로 9시간 어긋난 시각을 보고 있다 |
| **다크 모드 (5.5)** | 야간 근무자가 새벽에 여는 앱. 접근성이 아니라 제품 문제 |
| **규칙 엔진 테스트 (6.12)** | 8명이어도 근무표가 틀리면 앱의 존재 이유가 없어진다 |

**보안 항목의 위협 모델도 달라진다.** 1.6(XSS + localStorage 토큰)에서 "악의적인 공동 편집자"는 신뢰하는 지인 8명 사이에서는 현실적이지 않다. 다만 두 경로는 그대로 남는다.

1. 서비스가 인터넷에 노출되므로 **외부 공격자**는 여전히 존재한다 (1.1~1.4).
2. **외부 ICS 피드는 신뢰할 수 없는 입력이다.** 원격 서버가 보낸 에러 문구가 `main.js:1458`에서 `innerHTML`로 들어간다. 지인만 쓴다는 전제가 이 경로는 막아주지 못한다.

즉 XSS는 "전면 이스케이핑 + CSP"까지 갈 필요는 없어도, **외부에서 들어오는 값(ICS 피드 내용, 원격 에러 메시지)만큼은** 반드시 처리해야 한다.

---

## 1. 인터넷에 노출하기 전에 막아야 하는 것

소수 지인용이어도 홈서버가 인터넷에 노출되는 이상 아래 항목은 외부 공격자에게 그대로 열려 있다. 0.1에서 정리한 우선순위 조정이 반영돼 있다.

우선순위 순이다. 1~4번은 재작성을 기다릴 것 없이 지금 고치는 게 맞다.

### 1.1 리프레시 토큰과 액세스 토큰이 구분되지 않는다

`JwtTokenProvider.java:47-57`이 두 토큰을 **같은 키, 같은 알고리즘**으로 서명하고 `typ`/`aud`/`jti` 같은 구분 클레임을 넣지 않는다. 결과는 양방향이다.

- `JwtAuthenticationFilter.java:33-41`이 서명만 맞으면 액세스 토큰으로 받아들인다 → **7일짜리 리프레시 토큰이 그대로 7일짜리 액세스 토큰이다.**
- `AuthService.java:157-164`이 서명만 맞으면 리프레시 토큰으로 받아들인다 → 액세스 토큰으로 갱신이 된다.

리프레시 토큰의 존재 이유(짧은 액세스 토큰 + 긴 갱신 토큰의 분리)가 통째로 무효화된다. 토큰에 타입 클레임을 넣고 양쪽에서 검증해야 한다.

### 1.2 외부 캘린더 구독이 SSRF다

`ExternalCalendarService.validateFeedUrl`(`:145-156`)은 **스킴이 http/https인지만 확인한다.** 사설 IP, `127.0.0.1`, `169.254.169.254`(클라우드 메타데이터) 차단이 없고, `followRedirects(NORMAL)`(`:36`)이라 공개 URL로 검증을 통과한 뒤 내부 주소로 리다이렉트할 수도 있다.

**그리고 응답 본문이 유출된다.** 가져온 내용이 이벤트로 파싱되어(`:96-100`) 달력 화면과 API로 그대로 되돌아온다. 즉 인증된 사용자 아무나 서버 내부망을 읽고 그 결과를 자기 캘린더에서 볼 수 있다.

여기에 아래 1.3이 겹친다 — 공격자가 `http://localhost:8080/api/admin/db-stats`를 구독하면 그만이다.

응답 크기 제한도 없다(`BodyHandlers.ofString()`, `:116`). 12초 타임아웃이 유일한 방어라 큰 피드 하나로 메모리를 밀어낼 수 있다.

### 1.3 `/api/admin/db-stats`가 인증 없이 열려 있다

`SecurityConfig.java:89`가 `permitAll`이고, `HealthController.java:25-32`가 전체 사용자 수, 설정 수, 예외 수를 반환한다. 이름에 `admin`이 들어간 엔드포인트가 인터넷에 공개돼 있다.

### 1.4 JWT 시크릿에 기본값이 박혀 있다

```properties
# application.properties:31
jwt.secret=${JWT_SECRET:your-256-bit-secret-key-change-this-in-production-env}
```

환경변수가 없으면 **공개 저장소에 적힌 문자열**로 토큰을 서명한다. 누구나 임의의 `userId`로 토큰을 위조할 수 있다. 기본값을 없애고 미설정 시 부팅을 실패시켜야 한다.

(git 히스토리 전체를 훑어본 결과 실제 크리덴셜이 커밋된 적은 없다. 이 부분은 깨끗하다.)

### 1.5 토큰을 폐기할 방법이 없다

- 로그아웃(`AuthController.java:48-53`)이 200만 반환하는 **빈 함수**다. 코드에도 "향후 Redis 기반 블랙리스트 구현 가능"이라고 적혀 있다.
- 리프레시 토큰 저장소가 없다. 갱신해도 이전 토큰이 만료까지 살아 있고, 재사용 탐지도 없다.
- `UserPrincipal.java:73-76`이 `isEnabled()`를 `true`로 하드코딩한다. `users.enabled = false`로 계정을 정지시켜도 **기존 액세스 토큰은 24시간, 리프레시 토큰은 7일 동안 계속 동작한다.**

토큰이 유출됐을 때, 비밀번호를 바꿔도, 계정을 정지시켜도 막을 수단이 없다.

### 1.6 저장된 XSS + localStorage 토큰 = 계정 탈취 (공유 캘린더라서 더 위험)

`js/`에 `innerHTML` 대입이 51곳 있고, 그중 **다른 사용자가 입력한 값**을 그대로 넣는 곳들이 있다.

| 위치 | 주입되는 값 | 출처 |
|---|---|---|
| `main.js:279-280` | 메모, 기념일 메모, 작성자 닉네임 | **공유 캘린더 공동 편집자가 쓴 메모** |
| `main.js:1240`, `1287`, `1360`, `1407` | 사용자 이름·이메일, 그룹명, 권한 대상명 | 사용자 입력 |
| `main.js:1458-1461` | 외부 캘린더 이름, `lastError` | 사용자 입력 + **원격 ICS 서버 에러 문구** |
| `main.js:1570-1574` | `cal.name`, `cal.ownerName` | 다른 사용자의 캘린더 이름 |
| `main.js:545-569` | 일정 타입 값을 `value="${...}"`, `style="background:${...}"` 안에 | 속성 컨텍스트 주입 |
| `pattern.js:65-70` | 일정 타입 코드 | 사용자 입력 |

이스케이핑 헬퍼가 코드베이스에 없고, CSP도 없다 — `SecurityConfig.java:19`에서 `HeadersConfigurer`를 import해놓고 쓰지 않는다.

그리고 액세스/리프레시 토큰이 **둘 다 `localStorage`**에 있다(`login.html:196-197`, `api.js:47-48`). **팀 캘린더에 메모 한 줄만 심으면 그 캘린더를 여는 모든 사람의 토큰이 유출되고, 1.5에 따라 그 토큰은 폐기할 수 없다.** 일반 앱이면 XSS지만 공유 캘린더에서는 공격자가 피해자를 직접 초대할 수 있어 실현 난이도가 훨씬 낮다.

### 1.7 인증 실패가 "사용자 1번"으로 폴백된다

```java
// SettingsService.java:31-38
// 인증되지 않은 경우 또는 API 키 인증의 경우 기본값 사용
return SINGLE_USER_ID;   // == 1L
```

`CalendarAccessService.getCurrentUser()`가 이 위에 서 있다. 동시에 `JwtAuthenticationFilter.java:43-45`는 **모든 예외를 삼키고 인증 없이 체인을 계속 진행한다.**

지금은 `anyRequest().authenticated()` 덕분에 도달하지 않지만, `permitAll` 경로를 하나 추가하거나 matcher를 하나 잘못 쓰는 순간 **1번 사용자로 실행된다.** 예외를 던지도록 바꿔야 한다.

### 1.8 에러 응답이 내부 정보를 그대로 뱉는다

```java
// GlobalExceptionHandler.java:39-44
.body(Map.of("error","internal_error","message", ex.getMessage()));
```

처리되지 않은 모든 500에서 **원본 예외 메시지**가 나간다 — Hibernate/JDBC 문구, 제약 조건 이름, SQL 조각, 테이블·컬럼명. 아래 6.2의 FK 버그와 코드 길이 버그로 실제로 도달 가능한 경로다. `ex.getMessage()`가 null이면 `Map.of`가 NPE를 던져 핸들러 안에서 2차 실패가 난다.

상태 코드도 전부 뭉개진다. 모든 비즈니스 규칙이 `IllegalArgumentException` → **400**이라, `calendar_access_denied`(403이어야 함), `calendar_owner_only`(403), `calendar_not_found`(404)가 전부 400으로 나간다. 클라이언트가 권한 오류와 입력 오류를 구분할 수 없고, 403 급증을 감시하는 알림도 무용지물이 된다.

### 1.9 인증 엔드포인트에 레이트 리밋이 없다 — 후순위

`/api/auth/login`, `/signup`, `/refresh`가 전부 `permitAll`(`SecurityConfig.java:90-92`)이고 스로틀링이 없다.

**0.1의 전제에서는 후순위다.** Cloudflare Tunnel을 쓰면 앞단에서 기본적인 레이트 리밋과 봇 차단을 공짜로 얻는다. 애플리케이션에 bucket4j를 붙이는 건 그 다음에 필요해지면 하면 된다.

다만 **회원가입은 막아두는 게 낫다.** 초대제 운영이면 `/api/auth/signup`을 아예 비활성화하거나 초대 코드를 요구하도록 바꿔서, 홈서버 주소를 아는 누구나 계정을 만드는 상황을 없애는 편이 레이트 리밋보다 확실하고 싸다.

### 1.10 구글 계정 연결에 이메일 검증이 없다

`CustomOAuth2UserService.java:50-52`가 **이메일 문자열이 같다는 이유만으로** 구글 로그인을 기존 계정에 연결한다. 비밀번호로 만든 `LOCAL` 계정도 포함된다. `GoogleOAuth2UserInfo.java:21-24`는 `email_verified`를 **읽지 않는다.** 검증되지 않은 이메일을 반환하는 IdP가 있으면 계정 탈취로 이어진다.

같은 파일에서 `@Transactional`이 **private 메서드**에 붙어 있어(`:57`, `:82`) 스프링 프록시가 적용하지 못하고 조용히 무시된다. `loadUser`도 트랜잭션이 아니라, `User`가 커밋된 뒤 `UserSettings` insert가 실패하면 **설정 행이 없는 사용자**가 남고 나중에 `SettingsService.getOrCreate`에서 터진다.

---

## 2. 구조적 문제 — 재작성이 필요한 이유

### 2.1 캘린더가 사람을 모른다 (가장 중요)

`Calendar`는 패턴 1개, 주간 규칙 1세트를 가진다. 그리고 `exceptions` 테이블은:

```sql
-- V1__init_schema.sql
UNIQUE(calendar_id, date)
```

**캘린더 하나에 하루 한 줄.** 물리적으로 두 사람의 근무를 담을 수 없다.

그런데 나중에 붙인 테이블은 사람 단위다:

```sql
-- V8__add_calendar_leave_entries.sql
UNIQUE (calendar_id, date, target_user_id)
```

같은 캘린더 안에 "하루는 캘린더의 것"(exceptions)과 "하루는 사람의 것"(leave_entries)이라는 상반된 개념이 공존한다. `day_memos`에도 `author_id`가 붙어 있다. 사람별 데이터가 필요할 때마다 별도 테이블로 우회한 흔적이다.

최근 커밋 `feat: 휴가 작성 본인 것만 가능하도록 변경`, `remove: 휴가 신청자 셀렉트 박스 제거`가 이 문제의 증상이다. 멤버 개념이 없으니 "남의 휴가를 대신 등록"을 안전하게 표현할 수 없어 기능을 좁힌 것이다.

**동시 편집도 안전하지 않다.** `ExceptionService.saveOrUpdate`는 `(calendar, date)`로 행 하나를 찾아 통째로 덮어쓴다. 편집 권한을 가진 두 사람이 같은 날을 수정하면 나중에 저장한 쪽이 상대의 메모를 조용히 지운다. 충돌 감지도, `@Version` 낙관적 잠금도 코드베이스 어디에도 없다.

**공유 캘린더의 실제 수요는 대부분 "여러 사람의 스케줄을 한 화면에서 보는 것"이다.** 지금 구조에서 공유는 "내 근무표를 남이 구경한다"밖에 안 된다. 부부가 서로 근무 맞춰보기, 같은 부서 4개 조 근무표 한눈에 보기 — 전부 불가능하다.

**해결:** `calendar_members`를 1급 개념으로 도입하고, 스케줄을 만드는 모든 것을 `calendar_id`가 아니라 `member_id`에 매단다.

```
calendars ──< calendar_members ──< member_rules      (반복 규칙)
                    ├──< day_assignments             (날짜별 배정/예외)
                    └──< leave_entries
```

- 개인 캘린더 = 멤버 1명
- 커플/팀 캘린더 = 멤버 N명
- 멤버는 가입 사용자일 수도 있고 **계정 없는 이름표**일 수도 있어야 한다 — 팀장이 조원 4명 근무표를 대신 관리하는 건 실제로 흔한 시나리오다

### 2.2 "하루 = 코드 1개" 모델로는 일반 근무자를 못 잡는다

주5일 근무자에게 "오늘은 WORK"라는 정보는 아무 가치가 없다. 그들이 캘린더에 적는 건 "3시 회의", "7시 저녁 약속"이다. **시간을 가진 이벤트가 없으면 일반 사용자는 하루도 못 쓴다.**

지금 이벤트에 가장 가까운 건 `exceptions.memo`(하루 1개, 시간 없음)와 `day_memos`(여러 개지만 역시 시간 없음)다. 외부 ICS 구독만 시간 개념이 있는데 읽기 전용이다.

**해결:** 두 레이어를 **둘 다** 둔다.

- **배정 레이어** (`day_assignments`) — 하루에 근무 코드 하나. 교대근무자의 핵심. 반복 규칙으로 자동 생성되고 예외로 덮어씀.
- **이벤트 레이어** (`events`) — 시작/종료 시각을 가진 일정 N개. 일반 사용자의 핵심. RRULE 반복 지원.

교대근무자는 배정 위에 이벤트를 얹어 쓰고, 일반 사용자는 배정을 비워두거나 주5일 규칙 하나만 걸고 이벤트만 쓴다. 두 타겟이 **같은 모델, 같은 화면**을 공유하면서 각자 필요한 레이어만 쓰는 구조다.

이게 "캘린더 유형"(교대/일반)이라는 분기 자체를 없앤다. 지금은 `patternEnabled` boolean 하나가 캘린더 유형·규칙 사용 여부·UI 분기를 전부 겸하고 있고, 그 과부하가 다음 버그의 원인이다.

### 2.3 주5일(일반) 캘린더의 주간 반복 규칙이 코드상 도달 불가능

사용자가 "일반 일정" 캘린더를 만들면:

1. `CalendarManagementService.createCalendar` — `templateType="general"` → `patternEnabled = false`, 동시에 `ensureDefaultGeneralRules()`로 월~금 WORK / 토·일 OFF 규칙 7줄 생성
2. `V6__fix_general_calendar_pattern_flag.sql` — 정확히 그 캘린더들의 `pattern_enabled`를 `FALSE`로 확정
3. `CalendarService.buildMonthlyCalendar` — `usePattern`이 false라 주간 규칙 분기(`else if (usePattern)`)에 절대 진입하지 못함 → `baseCode = null`
4. `ScheduleService.resolveDay` — 같은 게이트에 걸려 `DaySchedule.empty(date)` 반환
5. `main.js:587` — 주간 규칙 편집 UI도 `patternEnabled !== false` 조건이라 화면에서 숨겨짐

**주5일 근무자용으로 만든 기능 전체가 만들어지자마자 읽히지도, 편집되지도 않는다.** 게다가 `TodayService`는 게이트가 달라서(`hasPattern || hasRules`) "설정됨"으로 판단한 뒤 빈 코드를 넘기고, `resolveLabel(null)`이 legacy fallback으로 `"O"`를 집어 **매일 "휴무"로 표시**된다.

`fix: 일반 캘린더 온보딩 화면 반복 오류` 커밋이 4번 연속인데, 온보딩 루프를 끊으려고 `patternEnabled`를 껐고 그 부작용으로 기능이 죽은 것으로 보인다.

### 2.4 월 뷰와 일 상세 뷰가 서로 다른 답을 낸다

`CalendarService.buildMonthlyCalendar`는 42일 그리드 **전체**에 대해 패턴을 한 번만 조회한다:

```java
var resolved = calendarPatternService.findEffective(calendar, calendarEnd);  // 그리드 마지막 날 기준
```

반면 `ScheduleService.resolveDay`는 날짜마다 조회한다:

```java
calendarPatternService.findEffective(calendar, date);  // 그 날짜 기준
```

패턴을 월 중간부터 바꾸면(`calendar_patterns`는 `pattern_start_date`별로 여러 줄을 허용한다), 월 뷰는 **월 전체를 새 패턴으로** 그리고 일 상세는 **날짜별로 올바른 패턴**을 쓴다. 같은 날을 두 화면에서 보면 근무가 다르게 나온다. 주석에 `// 패턴을 한 번만 조회 (N+1 문제 해결)`이라고 적혀 있는데, N+1을 없애면서 정확성을 깬 경우다. 올바른 해법은 구간에 걸치는 패턴들을 **한 번의 쿼리로 전부 가져와** 날짜별로 적용하는 것이다.

### 2.5 공유 모델이 세 벌이고, 서로 모순된다

세 가지 메커니즘이 공존한다.

1. **`calendar_shares`** (V1) — 초대 + 동의. `PENDING`/`ACCEPTED`/`REJECTED` × `VIEW`/`EDIT`
2. **`calendar_share_grants`** (V7) — 직접 부여. `USER`/`GROUP` 다형성, **상태 컬럼 없음**
3. **`share_groups` / `share_group_members`** (V7) — 소유자가 관리하는 사용자 묶음

`CalendarAccessService.resolveSharedPermission`(`:250-276`)의 실제 동작:

| 출처 | 상태 필터 |
|---|---|
| 직접 `CalendarShare` | **ACCEPTED만** |
| 직접 `CalendarShareGrant` | **없음** |
| 소속 그룹의 모든 grant | **없음** |

셋을 `maxPermission`으로 합친다. **우선순위도 거부도 없는 단조 합집합이라, 권한은 더해질 수만 있고 빼질 수 없다.** 여기서 나오는 결과:

- **거부를 강제할 수 없다.** 사용자가 명시적으로 REJECTED한 초대가 있어도, grant 한 줄이면 즉시 다시 들어온다. 같은 `(캘린더, 사용자)` 쌍에 모순된 두 행이 공존하고 max()가 항상 grant 편을 든다.
- **VIEW를 강제할 수 없다.** 소유자가 특정 사용자를 의도적으로 VIEW로 낮춰도, 그 사용자가 EDIT grant를 가진 그룹에 속해 있으면 EDIT이 된다. **특정 개인의 권한을 낮추는 게 불가능하다.**
- **동의 없는 강제 공유가 된다.** `CalendarGrantService.grantUser`(`:36-56`)로 부여한 권한은 수락 절차 없이 즉시 유효하다.
- **그룹에서 나갈 수 없다.** `ShareGroupService.addMember`(`:71-95`)는 동의도 알림도 없이 이메일로 아무나 추가할 수 있는데, `removeMember`는 **그룹 소유자만** 호출할 수 있다(`:108-116`). 한 번 추가되면 그 그룹에 부여된 모든 캘린더 접근을 영구히 받고 스스로 빠져나갈 수 없다.
- **거부한 캘린더가 목록에 계속 뜬다.** `listAccessible`이 `findByUser`로 **모든 상태**를 가져와 캘린더 이름과 소유자 실명을 내보내면서(`:82-95`), 열려고 하면 권한이 없다. 게다가 `calendar_shares`에는 **공유 취소 엔드포인트가 아예 없다** — 캘린더를 통째로 지우는 것 외에는 되돌릴 방법이 없다.
- `listParticipants`(`:141-175`)는 또 다른 네 번째 규칙을 쓴다 — shares에는 ACCEPTED 필터를 적용하고 grants에는 적용하지 않으며, 권한 수준을 무시한다. 그리고 이 목록이 **VIEW 권한만 있어도** 반환된다(`DayDetailService.java:43,72`). VIEW 사용자가 소유자가 부여한 모든 그룹의 전 멤버 이름을 알게 된다.

재작성 시 **하나로 통합**하고, 초대(pending)와 권한(granted)을 분리된 개념으로 명확히 해야 한다. 특히 "권한은 더해질 수만 있다"는 성질은 반드시 깨야 한다.

### 2.6 공유 캘린더가 소유자의 개인 설정을 상속한다

`CalendarPatternService.fallbackFromSettings`는 캘린더에 패턴이 없으면 `user_settings.pattern_codes`(소유자 개인 설정)로 폴백한다. 공유 캘린더에서 이건 다른 사람의 개인 근무 패턴이 새어 나오는 것이다.

게다가 이 컬럼에 **쓰는 코드는 전부 죽어 있다** — `SettingsService`의 `upsertPattern`, `clearPattern`, `ensurePatternExists`가 아무 데서도 호출되지 않는다. 즉 읽기만 하고 쓸 수 없는, 영원히 갱신되지 않는 레거시 상태다. 제거 대상.

### 2.7 일정 타입 정의가 세 군데에 중복돼 있다

- `ShiftCodeDefinition` enum (D/A/N/V/O 하드코딩, 한국어 라벨 포함)
- `ScheduleTypeService.LEGACY_DEFAULTS` (같은 5개 + WORK/OFF)
- `schedule_types` 테이블 (진짜 소스)

`schedule_types`가 이미 있는데 enum과 legacy map이 fallback으로 살아 있어서, 삭제된 코드가 조용히 "휴무"로 되살아난다. 재작성 시 DB만 남긴다.

---

## 3. 반복 규칙과 프리셋 설계

요구가 "정형 패턴을 주기보다 사용자가 지정하게 하되, 흔한 패턴은 프리셋 딕셔너리로 제공"인데, **규칙 원시 타입을 하나로 통일**하면 깔끔하게 풀린다.

### 3.1 단일 원시 타입: (기준일 + 주기 + 시퀀스)

지금은 순환 패턴(`calendar_patterns`)과 요일 규칙(`calendar_weekly_rules`)이 별개 테이블·별개 코드 경로다. 그런데 **요일 규칙은 주기가 7인 순환 패턴일 뿐이다.**

```
rule = { anchorDate, cycleLength, sequence[cycleLength] }
code(date) = sequence[ floorMod(date - anchorDate, cycleLength) ]
```

| 케이스 | anchorDate | cycleLength | sequence |
|---|---|---|---|
| 주5일 | 아무 월요일 | 7 | `[WORK,WORK,WORK,WORK,WORK,OFF,OFF]` |
| 격주 토요일 근무 | 아무 월요일 | 14 | 14칸 |
| 격일제(24h 맞교대) | 첫 근무일 | 2 | `[근무, 비번]` |
| 3조 2교대 | 첫 근무일 | 6 | `[D,D,N,N,O,O]` |
| 4조 3교대 | 첫 근무일 | 12 | `[D,D,D,A,A,A,N,N,N,O,O,O]` |

계산 엔진이 **함수 하나**가 되고 테이블도 하나(`member_rules`)가 된다. UI만 두 가지로 제시한다:

- **요일 그리드** (cycleLength가 7의 배수일 때) — 월~일 드롭다운. 일반 사용자용.
- **순서 빌더** (그 외) — 지금 `pattern.js`의 칩 방식. 교대근무자용.

즉 "교대 캘린더 / 일반 캘린더"라는 제품 분기가 사라지고 **입력 UI 선택**만 남는다. `patternEnabled` boolean과 2.3의 버그도 함께 사라진다.

규칙에는 유효기간을 둔다 — 패턴을 바꿔도 과거 근무표가 보존돼야 한다:

```
member_rules(
  id, member_id,
  anchor_date, cycle_length, sequence jsonb,
  effective_from, effective_to,   -- null이면 무기한
  source_preset_id,               -- 어떤 프리셋에서 시작했는지 (출처 기록)
  created_at, updated_at
)
```

### 3.2 프리셋 딕셔너리

프리셋은 **DB가 아니라 앱에 동봉하는 JSON 리소스**로 시작하길 권한다. git으로 버전 관리되고, 마이그레이션이 필요 없고, 나중에 DB로 옮기기도 쉽다.

```jsonc
{
  "id": "kr.shift.4team3shift",
  "name": "4조 3교대",
  "category": "SHIFT",
  "tags": ["제조", "병원", "생산직"],
  "cycleLength": 12,
  "sequence": ["D","D","D","A","A","A","N","N","N","O","O","O"],
  "requiredTypes": [
    { "code": "D", "name": "주간", "start": "06:00", "end": "14:00", "countsAsWork": true },
    { "code": "A", "name": "오후", "start": "14:00", "end": "22:00", "countsAsWork": true },
    { "code": "N", "name": "야간", "start": "22:00", "end": "06:00", "countsAsWork": true },
    { "code": "O", "name": "휴무", "countsAsWork": false }
  ],
  "teamOffsets": { "1조": 0, "2조": 3, "3조": 6, "4조": 9 },
  "description": "3일씩 주간→오후→야간 후 3일 휴무. 12일 주기."
}
```

핵심은 두 필드다.

- **`requiredTypes`** — 프리셋을 고르면 필요한 일정 타입(색·시간 포함)이 자동 생성된다. 지금은 코드를 먼저 만들고 그 다음 패턴을 짜야 해서 첫 설정 장벽이 높다.
- **`teamOffsets`** — 사용자는 "몇 조?"만 고르면 `anchorDate`가 자동 계산된다. **이게 공유 캘린더의 결정적 기능이다.** 팀장이 캘린더 하나 만들고 멤버 4명에게 각각 1~4조를 지정하면 부서 전체 근무표가 한 화면에 뜬다. 지금 구조로는 절대 못 하는 일이고, 이 앱이 개인 앱이 아니라 서비스가 되는 지점이 정확히 여기다.

**프리셋은 반드시 스냅샷으로 복사되어야 한다.** 고르는 순간 `sequence`가 `member_rules`로 복사되고, 이후 프리셋 JSON이 바뀌어도 기존 사용자에게 영향이 없어야 한다. `source_preset_id`는 참조가 아니라 출처 기록이다.

### 3.3 프리셋 목록 초안 (한국 기준)

**일반 근무 (`REGULAR`)** — 요일 그리드 UI
- 주5일 (월~금)
- 주5일 + 격주 토요일
- 주6일 (월~토)
- 주4일

**교대 근무 (`SHIFT`)** — 순서 빌더 UI
- 격일제 / 24시간 맞교대 — `[근무, 비번]`, 주기 2 (경비, 시설)
- 3조 1교대 (소방형) — `[당번, 비번, 휴무]`, 주기 3
- 2조 2교대 — `[주,주,야,야,비,휴]`, 주기 6
- 3조 2교대 (12시간) — `[D,D,N,N,O,O]`, 주기 6 (제조, 간호)
- 4조 2교대 — `[D,D,N,N,O,O,O,O]`, 주기 8
- 4조 3교대 — 주기 12 (위 예시)
- 5조 3교대 — 주기 15~20 (사업장별 변형 큼)
- 2-2-3 (파나마) — 주기 14
- 듀폰 (4일 근무 / 4일 휴무) — 주기 8

**직접 만들기** — 빈 시퀀스에서 시작

주의: 5조 3교대나 4조 3교대는 사업장마다 변형이 크다. 프리셋은 "정답"이 아니라 **출발점**으로 제시하고, 고른 직후 바로 순서 빌더에서 편집할 수 있어야 한다. "이 패턴이 내 근무랑 다른데요"가 가장 흔한 이탈 지점이 될 것이다.

### 3.4 패턴 편집 UX

지금 `pattern.js`의 칩 빌더는 발상은 맞다. 다만 제품의 핵심 차별점인데 비해 완성도가 낮다.

**지금의 한계 (161줄)**

- **추가만 된다.** 순서 변경·중간 삽입·중간 삭제가 없다. 되돌리기는 `sequence.pop()`뿐이라 **14일 주기의 3번째 칸을 고치려면 칩 12개를 되돌려야 한다.**
- **반복 횟수 입력이 없다.** "주간 4일, 휴무 2일"에 탭 6번. 실제 교대 주기는 8~28일이고 5조 3교대는 25일이 넘는다 — **25번 넘게 탭해야 하고 "×4" 같은 축약이 없다.**
- **주기 길이 피드백이 없다.** 미리보기가 `"총 12개 순서"` 텍스트뿐이다. "이 주기는 12일이라 요일과는 84일마다 맞아떨어진다" — 교대근무자에게 가장 유용한 정보가 없다.
- **검증이 없다.** 빈 시퀀스도 그대로 전송되고(`pattern.js:118`), 수동 입력 모드는 코드 존재 여부를 확인하지 않아 오타가 저장된 뒤 달력에 정체불명 문자열로 찍힌다.
- **수동 입력 모드가 손실을 만든다.** 빌더와 텍스트가 동기화되지 않아서, 칩 14개를 쌓고 "직접 입력"을 눌러 확인한 뒤 저장하면 **오래된 텍스트 값이 전송된다.**
- **발견하기 어렵다.** 설정 → 캘린더 관리 → 교대 패턴 관리로 3단계 깊이에 있고, 누르면 캘린더 화면 전체가 사라진다.

**추가로 필요한 것**

- **달력 미리보기** — 시퀀스를 만드는 동안 실제 달력에 어떻게 찍히는지 즉시 보여줘야 한다. 지금은 기준일을 하루 잘못 잡았는지 저장 후에야 안다.
- **기준일 역산** — "패턴 시작일"을 묻는 대신 **"가장 최근 야간 근무가 언제였나요?"**처럼 사용자가 아는 사실을 물어 anchor를 계산해야 한다. 교대근무자가 자기 패턴의 "1번째 날"을 아는 경우는 드물다. 온보딩 이탈의 최대 원인이 될 지점이다.
- **반복 횟수와 순서 편집** — `[D ×4][O ×2]` 입력, 드래그 재정렬, 중간 삽입/삭제.
- **패턴 변경 시 과거 보존** — 지금 `savePattern(applyRetroactive=true)`은 기존 패턴을 **전부 삭제**한다. 3.1의 유효기간 모델로 바꿔야 한다.

---

## 4. 일반 주5일 사용자를 잡기 위해 필요한 것

교대근무 쪽은 3장으로 풀린다. 일반 사용자 쪽은 별개 문제인데, 0.1의 전제에서는 **먼저 결정해야 할 갈림길**이 있다.

### 4.1 갈림길 — 이벤트 레이어를 직접 만들 것인가

"교대근무가 아닌 사람도 쓰기 편해야 한다"는 요구는 두 가지로 해석된다.

**해석 A — 이 앱에서 개인 일정도 관리한다.** 그러면 2.2의 이벤트 레이어(시작·종료 시각, RRULE 반복, 주/일 뷰)를 직접 만들어야 한다. 사실상 캘린더 앱을 하나 더 만드는 일이고 시간대·알림·반복 예외까지 전부 따라온다. 혼자 만들고 혼자 유지보수하는 프로젝트에서 가장 비싼 선택지다.

**해석 B — 이 앱이 교대근무 전용으로 느껴지지만 않으면 된다.** 개인 일정은 이미 쓰던 캘린더에 두고, 이 앱은 **근무표 + 메모 + 외부 캘린더 겹쳐보기**로 쓴다. 필요한 건 3.1의 요일 그리드 규칙, 기존 `day_memos`, 그리고 이미 만들어둔 외부 캘린더 구독이다.

**0.1의 전제에서는 B를 권한다.** 이유는 세 가지다.

- 지인들은 대부분 이미 다른 캘린더 앱을 쓰고 있다. 개인 일정을 옮겨오게 만드는 건 어렵고, 옮겨오지 않으면 반쪽짜리 이벤트 레이어만 남는다.
- 외부 캘린더 구독(ICS)이 **이미 구현돼 있다.** 6.14의 파서 한계만 고치면 곧바로 쓸 만해진다. 새로 만드는 것보다 훨씬 싸다.
- 반대 방향(ICS 내보내기)을 추가하면 **근무표를 구글 캘린더에서 볼 수 있다.** 교대근무자에게는 이게 앱 안에서 약속을 적는 기능보다 가치가 크다. 근무표는 이 앱에서 만들고 보기는 각자 편한 데서 하는 구조다.

즉 이 앱의 자리를 "범용 캘린더"가 아니라 **"근무표 전문 도구 + 다른 캘린더와 겹쳐보기"**로 잡는 것이다. 8명에게 직접 물어볼 수 있는 규모이니, 실제로 "여기에 약속도 적고 싶다"는 말이 나오면 그때 만들어도 늦지 않다.

**다만 스키마는 나중에 이벤트를 붙일 수 있게 설계해둘 것.** 2.1의 멤버 스코프로 가면 `events(member_id, starts_at, ends_at, …)`를 나중에 추가하는 게 자연스럽다. 지금 안 만드는 것과, 나중에 못 만들게 막아두는 것은 다르다.

### 4.2 B를 택해도 필요한 것

1. **온보딩에서 규칙 설정을 건너뛸 수 있어야 한다** — 지금은 캘린더 유형 선택 → 패턴/규칙 설정이 첫 관문이다. 일반 사용자에겐 "일단 달력 보여주고, 반복 근무 있으면 나중에"가 맞다.
2. **제품 정체성 정리** — `manifest.json`이 `"name": "교대 근무표"`, `"short_name": "근무표"`, `"description": "나의 교대 근무 일정"`이다. 설치하면 홈 화면에 "근무표"로 뜬다.
3. **ICS 내보내기(구독 URL)** — B의 핵심이다. 캘린더별 읽기 전용 `.ics` 피드를 토큰이 담긴 URL로 제공하면 사용자가 구글 캘린더에 등록해두고 자기 근무표를 어디서든 본다. 구현 비용도 작다.
4. **ICS 파서 교체** — 겹쳐보기가 제품의 축이 되므로 6.14의 한계(주간·월간 반복 미지원, 시각 버림)를 그냥 넘길 수 없게 된다.
5. **URL 라우팅** — 딥링크가 없으면 "이 날짜 좀 봐"라고 링크를 보낼 수 없다. 공유 캘린더에서 이건 기능 하나가 아니라 공유의 전제다.
6. **날짜 셀 오버플로** — 지금 `max-height: 100px` + `overflow: hidden`(`styles.css:379-390`)으로 내용을 잘라내는데, 외부 일정을 겹쳐 보이면 바로 터진다.

---

## 5. 클라이언트

`main.js`가 2,925줄이고 상태·렌더링·API 호출·이벤트 바인딩·부트스트랩이 한 파일에 평평하게 들어 있다. 빌드 도구가 아예 없다 — `package.json`, 번들러, TypeScript, ESLint, 프론트 테스트 전부 없다. 아래 문제 대부분은 타입 시스템이나 린터가 정적으로 잡아줬을 것들이다.

### 5.1 구조

- 상태는 가변 객체 리터럴 `state` 하나(`main.js:115-131`)이고 반응성이 없다. 변경 뒤마다 수동으로 렌더 함수를 호출해야 하고, 하나 빠뜨리면 UI가 어긋난다.
- `state.usePattern`을 세 곳에서 유도하는데 **기본값이 서로 다르다** — `main.js:941`·`1078`은 `true`, `main.js:1204`는 `false`. `renderCalendarSelector()`가 거의 모든 변경마다 실행되므로, `state.calendarId`가 `state.calendars`에 아직 없는 짧은 순간에 앱이 조용히 "패턴 없음" 모드로 뒤집히고 근무 코드가 전부 사라진다.
- **라우팅이 없다.** `pushState`/`popstate`가 한 군데도 없다. 화면 전환은 `hidden` 토글이고 뒤로가기 스택은 `viewParents` 맵으로 한 단계만 흉내 낸다. 딥링크가 불가능하고, **안드로이드에서 뒤로가기를 누르면 모달이 닫히는 대신 앱이 종료된다.**
- UI 문자열이 제어 흐름 키로 쓰인다 — `main.js:358`, `1156`이 `group.title === '공유받은 캘린더'`로 분기한다. 문구를 바꾸면 공유 캘린더 그룹핑이 조용히 깨진다.

### 5.2 동시성

- 요청 순서 가드가 `loadCalendar`(`main.js:1657`) 한 곳에만 있다. `fetchCalendar`는 가드 **바깥**에서 `setScheduleTypes()`를 호출하므로(`main.js:1637`), 캘린더를 전환한 뒤 늦게 도착한 인접 월 프리페치 응답이 **다른 캘린더의 일정 타입으로 범례와 드롭다운을 덮어쓴다.**
- **월 이동에서 클릭이 유실된다.** `navigateMonth`는 150ms 디바운스인데 이동 목표를 클릭 시점의 `state.year/month`로 계산하고, 그 값은 fetch가 끝난 뒤에야 갱신된다. "다음 달"을 빠르게 세 번 누르면 세 핸들러가 같은 값에서 +1을 계산하고 디바운스가 합쳐서 **한 달만 이동**한다.
- `bootstrap()`에 재진입 가드가 없어 캘린더 카드를 두 번 빠르게 누르면 두 부트스트랩이 `state.calendarId`를 놓고 경쟁한다.
- 날짜 상세 저장이 change/blur/change 세 리스너에서 각각 트리거돼 같은 날짜에 동시 저장이 발생할 수 있다.

### 5.3 토큰 갱신이 깨져 있다

- **구글 로그인 사용자는 리프레시 토큰을 받지 못한다.** `login.html:243-249`가 `?token=`에서 액세스 토큰만 저장한다. 24시간 뒤 만료되면 `refreshAccessToken()`이 `'No refresh token available'`로 던지고 **강제 재로그인**된다. 이메일 가입자는 둘 다 받는다. 구글 로그인이 주 경로일 텐데 매일 로그아웃되는 셈이다.
- **OAuth 토큰이 URL 쿼리스트링으로 전달된다**(`OAuth2AuthenticationSuccessHandler.java:42-46`). JWT가 브라우저 히스토리, `Referer` 헤더, 리버스 프록시 액세스 로그에 남는다. `Referrer-Policy` 헤더도 없어서 `login.html`의 서드파티 리소스가 토큰을 받는다. 주소창에서 제거하지도 않는다.
- **갱신 후 재시도가 깨져 있다.** `api.js:82-96`의 큐 재생 경로가 `options.headers`를 펼치는데 원래 헤더는 `getHeaders()`에서 왔지 `options`에 없다. 재시도되는 모든 POST/PUT이 **`Content-Type: application/json`을 잃고** 415/400으로 거부된다.
- `request()`가 인증 실패 시 **`undefined`를 반환**하는데(`api.js:76`, `133`) 호출부는 바로 프로퍼티에 접근한다(`main.js:918`).
- 만료를 미리 보고 갱신하는 로직이 없다. 401을 받아야만 갱신한다.

### 5.4 에러 처리와 캐시

- `try` 블록이 부트스트랩 fetch뿐 아니라 **이후 렌더링 전체**를 감싸고 있어(`main.js:934-1008`), 렌더 중 `TypeError`가 나면 "bootstrap API failed"로 오인 로깅되고 앱이 조용히 레거시 로드 경로를 다시 탄다. 진짜 결함이 여기 숨는다.
- `alert()`/`confirm()`/`prompt()`가 27번 쓰인다. 블로킹에 브랜딩 불가고, `prompt()`는 일부 설치형 PWA/WebView에서 동작하지 않는다. `main.js:2484-2486`은 `confirm()`으로 물어본 뒤 OK를 누르면 "직접 하세요"라는 `alert()`를 띄우는, 아무것도 하지 않는 다이얼로그다.
- 사용자에게 영어 프로토콜 문자열이 그대로 노출된다(`'Request failed after token refresh'`).
- `cacheStores`(`main.js:452`)에 TTL은 있지만 **축출이 없다.** 브라우징한 모든 달의 42일치 페이로드가 세션 내내 메모리에 남는다.
- 메모 하나 추가할 때마다 `selectDay()`와 `loadCalendar(force)`를 둘 다 호출해서 42칸 그리드를 두 번 전부 헐고 다시 짓는다.

### 5.5 접근성 / 사용성

- **`user-scalable=no`**(`index.html:5`, `login.html:5`) — 핀치 줌 차단. WCAG 1.4.4 위반이고 앱스토어 심사 지적 항목이다.
- 모달 4개 전부 `role="dialog"`, 포커스 트랩, Esc 닫기가 없다. 앱 전체에 `keydown` 리스너가 하나도 없다.
- `outline: none`을 전역으로 걸고(`styles.css:912`) `:focus-visible` 대체가 없다.
- 달력 그리드가 `div` 더미라 키보드로 날짜를 선택할 수 없다. `tabindex`가 `index.html`에 0회 등장한다.
- 토스트에 `aria-live`가 없어 40여 개의 알림이 스크린리더에 전달되지 않는다.
- **`prefers-color-scheme` 대응이 0건이다** — 라이트 전용에 `#fff` 하드코딩. **야간 근무자가 새벽에 OLED 화면으로 여는 앱**이라는 걸 생각하면 접근성 이슈가 아니라 제품 이슈다.
- 요일 순서가 모듈마다 다르다 — `calendar.js:3`은 일요일 시작, `main.js:607`은 월요일 시작.

### 5.6 정리 대상

- `js/today.js`(31줄)는 어디서도 import되지 않는 죽은 코드다.
- **`index.html.backup`(318줄)이 정적 루트에 있어 빌드 산출물에 포함되고 실제로 서빙된다.** 저장소에서 지워야 한다.

재작성 범위에 프론트가 포함된다면 지금이 프레임워크를 도입할 시점이다. 자동 이스케이핑과 키 기반 재조정만으로 1.6의 XSS 표와 위의 전체 그리드 재구축 문제가 한 번에 사라진다.

**다만 0.1의 전제에서 선택 기준은 "혼자 유지보수할 수 있는가"다.** 규모가 크지 않으니 React 풀스택까지 갈 이유는 없고, 무엇을 고르든 아래 세 가지만 확보되면 된다.

- 자동 이스케이핑 (1.6의 XSS 표가 통째로 사라진다)
- 반응형 상태 (5.1의 `usePattern` 3중 유도 같은 버그가 구조적으로 불가능해진다)
- URL 라우팅 (딥링크 + 안드로이드 뒤로가기)

Svelte나 Vue 정도가 무난하고, 익숙한 게 따로 있으면 그걸 쓰는 편이 낫다. 중요한 건 프레임워크 선택이 아니라 **2,925줄 단일 파일에서 벗어나는 것**이다.

---

## 6. 데이터·운영 준비

### 6.1 `ddl-auto=update`와 Flyway를 동시에 쓰고 있다

```properties
# application.properties:6  (운영)
spring.jpa.hibernate.ddl-auto=update
```

Flyway가 마이그레이션을 적용한 **뒤에** Hibernate가 엔티티를 보고 스키마를 또 바꾼다. 흥미롭게도 `application-local.properties:5`는 `validate`로 올바르게 돼 있다 — **운영만 틀렸다.**

`V10__align_calendar_weekly_rules_day_of_week_type.sql`(SMALLINT → INTEGER) 같은 "정렬" 마이그레이션이 생긴 게 그 흔적이다. 운영 DB와 로컬 DB의 스키마가 조용히 달라지고, **특정 환경에서만 나는 오류의 유력한 원인이다.** `ddl-auto=validate`로 바꾸고 스키마 변경은 마이그레이션으로만 해야 한다. 재작성 전에 이것부터 안 하면 새 스키마도 똑같이 드리프트한다.

### 6.2 확인된 운영 500 두 건

**(a) 공유된 캘린더를 삭제하면 FK 위반이 난다.** `V1:26`의 `user_settings.default_calendar_id`에는 `ON DELETE`가 없다(스키마의 다른 모든 FK에는 있다). `CalendarManagementService`는 **현재 사용자의** 기본 캘린더만 비우는데(`:114`), `CalendarShareService.java:85-90`은 공유를 수락한 사람의 기본 캘린더로 그 캘린더를 설정한다. 그래서 공유된 캘린더를 지우면 FK 위반 → 1.8의 핸들러를 타고 **원본 JDBC 제약 조건 메시지가 담긴 500**이 나간다. `ON DELETE SET NULL`이어야 한다.

**(b) 10자를 넘는 일정 코드를 저장하면 터진다.** `exceptions.custom_code`는 `VARCHAR(10)`(`V1:38`)인데 `schedule_types.code`는 `VARCHAR(32)`이고 DTO도 `@Size(max = 32)`를 허용한다. `ShiftException` 엔티티에 length 선언이 없어 Hibernate가 255로 가정하므로 `ddl-auto=update`가 컬럼을 넓혀주지도 않는다. **11자짜리 코드로 예외를 저장하는 순간 SQLSTATE 22001.**

### 6.3 참조 무결성이 애플리케이션에만 있다

`calendar_weekly_rules.schedule_type_code`, `exceptions.custom_code`, `calendar_patterns.pattern_codes`에서 `schedule_types(calendar_id, code)`로 가는 FK가 없다. 코드는 자유 문자열이고 검증은 Java에서만(`ScheduleTypeService.supportsCode`) 한다. 타입을 지우거나 이름을 바꾸면 그걸 참조하는 규칙·패턴·예외가 조용히 고아가 된다. `pattern_codes`가 콤마로 이어붙인 `TEXT`라 쿼리도 불가능하다.

`exceptions.author_id`와 `day_memos.author_id`도 `ON DELETE`가 없어 **사용자를 삭제할 수 없다** — 계정 삭제 경로 자체가 막혀 있다(6.6).

### 6.4 누락된 유니크 제약

- **`external_calendar_sources`에 `(calendar_id, feed_url)` 유니크가 없다** — 같은 ICS 피드를 N번 구독할 수 있고, 매 동기화마다 이벤트 전체가 중복된다. `createSource`도 중복을 확인하지 않는다.
- **`day_memos`에 `(calendar_id, date, author_id)` 유니크가 없다** — `DayMemoService`는 "사용자당 하루 하나"로 다루는데 동시 요청이면 중복 행이 생긴다.
- **이메일이 대소문자를 구분한다.** `users.email` 유니크(`V1:12`)가 case-sensitive이고 정규화 코드가 없다. `Foo@x.com`과 `foo@x.com`이 별개 계정이 된다. 그리고 초대·권한 부여·그룹 추가가 전부 정확 일치 조회라(`CalendarShareService.java:37`은 trim조차 안 한다) **대소문자가 다르게 입력된 초대가 조용히 실패한다.**
- `share_groups`의 `(owner_user_id, name)` 유니크도 case-sensitive이고, 사전 확인이 check-then-insert라 경합 시 `DataIntegrityViolationException`이 처리되지 않고 500이 된다.

### 6.5 인덱스가 실제 쿼리와 안 맞는다

**가장 뜨거운 경로가 seq scan이다.** `V7:55-61`의 `idx_calendar_share_grants_target_user`와 `..._target_group`은 `target_type`을 조건으로 하는 **부분 인덱스**인데, 실제 쿼리(`CalendarShareGrantRepository.java:27-42`)는 `target_type`으로 필터하지 않는다. Postgres가 부분 조건을 증명할 수 없어 인덱스를 못 쓴다. 이 쿼리는 `requireView`/`requireEdit`마다, 즉 **거의 모든 API 호출마다** 실행된다.

그리고 `CalendarAccessService.java:268-272`는 사용자가 속한 모든 그룹의 모든 grant를 가져와 **Java에서** 캘린더를 걸러낸다 — `findByCalendarAndTargetGroupIn`이 없어서다.

쓸모없는 인덱스도 있다: `V1:103`의 `EXTRACT(MONTH FROM date)` 인덱스는 어떤 쿼리도 그 표현식을 쓰지 않는다. 쓰기 비용만 늘린다.

### 6.6 백업이 없고, 사용자 삭제가 물리적으로 막혀 있다

**백업이 이 항목에서 가장 중요하다.** Railway 관리형 Postgres에 있던 안전망이 홈서버로 가면 사라진다. 처음부터 넣어야 한다.

- `pg_dump` 크론 + **오프사이트 복사**(R2/S3 등 다른 물리 위치)
- **복구를 실제로 한 번 해볼 것.** 테스트하지 않은 백업은 백업이 아니다
- 디스크 SMART 모니터링, 가능하면 UPS

그 외:

- 6.3의 FK(`exceptions.author_id`, `day_memos.author_id`에 `ON DELETE` 없음) 때문에 **사용자 삭제 경로가 물리적으로 막혀 있다.** 지인 8명이어도 누가 그만두면 지울 수 있어야 한다. 새 스키마에서 `ON DELETE` 정책을 처음부터 명시할 것.
- ICS 내보내기는 있으면 좋다 — 법적 요구가 아니라, 사용자가 자기 근무표를 구글 캘린더에서도 보고 싶어할 것이기 때문이다. 4장의 트레이드오프와도 연결된다.

(약관·개인정보처리방침은 0.1의 전제에서 해당 없음.)

### 6.7 감사 로그가 없다 — 후순위 (0.1)

- `calendar_shares`와 `calendar_share_grants`에 `created_by`/`updated_by`가 없다 — **누가 누구에게 권한을 줬는지 추적할 수 없다.** 감사 이벤트 테이블도 없다.
- 타임스탬프가 JPA `@PrePersist`/`@PreUpdate`로만 유지돼서, 파생 삭제나 벌크 연산은 전부 우회한다.
- `calendar_shares`, `share_group_members`, `calendar_patterns`, `user_settings`에 `updated_at`이 없다.

### 6.8 관측성

- **actuator 의존성이 없다.** 메트릭도, readiness/liveness 프로브도 없다. `railway.toml`이 헬스체크하는 `HealthController.java:20-23`은 **정적으로 `{"status":"UP"}`을 반환한다** — 데이터베이스가 죽어도 정상이라고 보고한다.
- **`JwtTokenProvider.java:76-84`가 모든 잘못된 토큰을 스택 트레이스와 함께 ERROR로 남긴다** — 인증 없이 로그를 채워 넣을 수 있고, *만료된* 토큰이라는 지극히 정상적인 이벤트도 에러로 찍힌다.
- 요청/추적 ID도, userId를 담은 MDC도, 구조화 로그도 없다. 사용자가 "오류 났어요"라고 하면 재현할 방법이 없다.

### 6.9 타임존 모델이 없다

- `Dockerfile`, `docker-compose.yml`, `railway.toml`, `docker-entrypoint.sh` 어디에도 `TZ`가 없다 → Railway 컨테이너는 **UTC**로 동작한다.
- 엔티티가 `LocalDateTime.now()`(UTC)로 시각을 찍고 프론트는 그 문자열을 **클라이언트 로컬**로 해석한다(`main.js:2698`, `calendar.js:22`). **한국 사용자에게 모든 "수정됨" 시각이 9시간 어긋나 보인다.**
- 서버의 "오늘"은 UTC 날짜, 클라이언트의 "오늘"은 로컬 날짜다. **한국 시간 00:00~09:00 사이에는 둘이 다른 날을 가리킨다.** 야간 근무 끝나고 새벽에 앱을 여는 사용자가 정확히 이 구간에 있다.
- 모든 시각 컬럼이 `TIMESTAMP WITHOUT TIME ZONE`이다.
- `date-utils.js`에 UTC 자정 드리프트를 막으려고 만든 `parseIsoDateLocal`이 있는데 정작 중요한 두 곳(`main.js:2481`, `2495`)에서 우회된다 — 기념일 삭제 확인 다이얼로그가 **틀린 연도**를 보여준 뒤 데이터를 지운다.

사용자 또는 캘린더에 타임존을 두고, 시각 데이터는 `Instant`/`timestamptz`로 저장해야 한다. 야간 근무가 자정을 넘어가는 것(22:00–06:00)도 아직 모델링돼 있지 않다.

### 6.10 알림이 동작하지 않는다

`user_settings.default_notification_minutes`를 저장하고 `NotificationPreferenceService`로 읽고 쓰지만 **알림을 보내는 코드가 없다.** `push_subscriptions` 테이블은 `V3`에서 삭제됐고 스케줄러도 없다. 사용자는 "60분 전 알림"을 설정하고 아무것도 받지 못한다.

교대근무자에게 "내일 야간이에요" 알림은 **부가 기능이 아니라 핵심 기능**이고, 매일 앱을 열게 만드는 유일한 훅이다. 서비스화한다면 여기에 제대로 투자해야 한다 — 웹푸시(VAPID) 또는 FCM + 스케줄러.

### 6.11 PWA에 서비스 워커가 없다

`static/` 전체에 `serviceWorker`, `sw.js`, `caches.` 참조가 0건이다. 홈 화면 추가는 되지만 **오프라인이 전혀 안 된다.** 지하 주차장, 병동, 공장 현장 — 교대근무자가 실제로 근무표를 확인하는 장소가 전부 네트워크가 나쁜 곳인데, 설치한 앱을 열면 브라우저 오류 페이지가 뜬다.

`manifest.json`도 최소 수준이다. `id`, `scope`, `shortcuts`, `screenshots`가 없고, 아이콘 둘 다 `"purpose": "any maskable"`로 두 용도를 겸해서 마스킹 안전영역이 잘린다.

### 6.12 테스트가 실행되지 않는다

`contextLoads()` 하나가 전부고, `.github/workflows`가 **비어 있으며**(CI 없음), `Dockerfile:14`는 `./gradlew bootJar -x test`로 **빌드에서 테스트를 건너뛴다.** 어떤 경로로도 테스트가 실행되지 않는다.

근무 계산은 **순수 함수로 분리하기 가장 쉬운 로직**이다 — 입력이 (기준일, 주기, 시퀀스, 조회일)이고 출력이 코드 하나다. 규칙 엔진을 스프링 의존 없는 클래스로 빼고 프리셋 전체에 대한 테이블 테스트를 붙이면 이후 리팩터링이 훨씬 안전해진다. 2.3, 2.4 같은 버그는 이 테스트 한 벌이면 전부 잡혔을 것들이다.

권한 모델도 마찬가지다. 2.5의 모순들은 "REJECTED한 사용자는 grant가 있어도 접근할 수 없다" 같은 테스트 한 줄이면 드러났을 것이다.

### 6.13 기타

- **입력 검증 공백**: `ExceptionUpdateRequest`에 검증 애노테이션이 **하나도 없는데** `@Valid`가 붙어 있다 — 메모가 무제한 길이로 `TEXT`에 들어간다. `MemoSaveRequest.memo`, `SignupRequest.name`, `ExternalCalendarSourceRequest.feedUrl`도 `@Size`가 없다. `ShareDecisionRequest.accept`가 원시 `boolean`이라 **필드가 없으면 조용히 거부로 처리된다.**
- **CORS**: `frontendUrl`이 `http://localhost:8080`으로 기본값이 잡혀 있어서(`application.properties:36`), `APP_FRONTEND_URL`을 빠뜨리고 배포하면 **운영에서 localhost origin을 신뢰한다.** `allowCredentials(true)`인데 인증이 Bearer 방식이라 이 플래그는 필요 없다 — 제거해야 한다.
- **CSRF/세션**: CSRF 비활성화는 Bearer API에 맞지만, 같은 오리진이 HTML도 서빙하고 **세션 기반 OAuth2 로그인 흐름**도 `SessionCreationPolicy.STATELESS` 아래서 돌린다. OAuth2 authorization request의 `state`를 저장할 곳이 없는데 `HttpCookieOAuth2AuthorizationRequestRepository`가 설정돼 있지 않다.
- **`@EnableMethodSecurity`가 켜져 있는데 `@PreAuthorize`가 코드베이스에 0개다.** `User.Role.ADMIN`도 정의만 되고 부여·검사되지 않는다.
- **죽은 코드**: `CalendarShareRepository.findAcceptedSharesByUser`(정작 `listAccessible`이 써야 할 쿼리), `UserRepository.findByProviderAndProviderId`(OAuth 연결이 이메일 기반이라 provider 조회를 안 씀), `SettingsService`의 패턴 쓰기 메서드 4개, `ExternalCalendarSourceRepository.findByCalendarAndActiveTrueOrderByNameAsc`.
- **외부 캘린더 동기화가 요청 스레드에서 전체 이벤트를 지우고 다시 넣는다**(`ExternalCalendarService.java:97`).

### 6.14 ICS 파서의 한계

`IcsFeedParser`는 직접 구현한 163줄이다.

- 반복 규칙은 `FREQ=YEARLY`만 처리한다. **주간/월간 반복 이벤트는 첫 회만 나타난다.**
- 시각을 버리고 `LocalDate`만 남긴다 — 구독한 회의 일정의 시간을 알 수 없다.
- `TZID`를 무시한다.
- 같은 프로퍼티가 여러 번 나오면 `putIfAbsent`로 첫 값만 쓴다.

Google Calendar 구독처럼 흔한 케이스에서도 반복 일정이 깨진다. `ical4j` 같은 검증된 라이브러리로 교체하는 게 맞다.

---

## 7. 권장 순서

**전제: 홈서버로 이관하면서 새 스키마로 새로 시작하고, 기존 데이터는 가져오지 않는다.**

운영 DB에 사용자 8명, `exceptions` 0건뿐이고 전부 테스터다. 마이그레이션 스크립트를 짜고 검증하는 비용이 데이터를 다시 넣는 비용보다 크다. 더 중요한 건 **"기존 데이터를 받아와야 한다"는 제약이 새 설계를 오염시킨다**는 점이다 — `exceptions`를 멤버 스코프로 옮길 때 그 하루가 누구 것인지(author는 메모가 있을 때만 설정되므로 신뢰할 수 없다), `calendar_shares`와 grants가 모순될 때 어느 쪽인지, 죽어 있는 주간 규칙을 살릴지를 전부 결정해야 하는데, 지금 존재하지도 않는 데이터를 위한 결정이다.

**단, 버리는 것과 보관하는 것은 다르다.** Railway를 내리기 전에 `pg_dump`를 떠서 파일로 아카이브해둘 것. 비용이 0이고 나중에 조회할 수 있다.

**컷오버 대신 병렬 운영.** 데이터를 안 가져오니 두 시스템이 간섭하지 않는다. Railway를 살려둔 채 홈서버에 새로 세우고, 테스터들에게 새 주소를 안내한 뒤 한 달쯤 지켜보고 Railway를 내린다.

### 0단계: 이관 전 필수

- **1.2 SSRF 차단** — 홈서버는 공유기 관리 페이지·NAS와 같은 내부망이다. Railway의 격리 컨테이너와 위험도가 다르다. 사설 IP 대역 차단 + 리다이렉트 후 재검증 + 응답 크기 제한. **이관 작업의 맨 위**
- **1.4 JWT 시크릿** 기본값 제거, 미설정 시 부팅 실패. 홈서버 환경변수 설정 확인
- **1.3 `/api/admin/db-stats`** 인증 추가 또는 삭제
- **회원가입 차단 또는 초대 코드화** — 홈서버 주소를 아는 누구나 계정을 만드는 상황을 없앤다 (1.9)
- **1.1 토큰 타입 클레임** — 클레임 하나 추가하고 양쪽에서 검증. 비용이 작다
- **1.8** `server.error.include-message=never` + 예외별 상태 코드
- **외부에서 들어오는 값 이스케이핑** — ICS 피드 내용과 원격 에러 문구가 `innerHTML`로 들어가는 경로만이라도 (1.6)

### 0.5단계: 홈서버 인프라

- **Cloudflare Tunnel** — 포트 개방 없음, 홈 IP 비노출, TLS 자동, 동적 IP 문제 해소, 앞단 레이트 리밋 무료
- **백업**: `pg_dump` 크론 + 오프사이트 복사, **복구 리허설 1회**
- `docker-entrypoint.sh` **삭제** — 100줄 전부 Railway의 `DATABASE_URL` → JDBC 변환용이다. JDBC URL을 직접 주면 된다
- `railway.toml` 삭제, sleep mode 전제 제거 — 알림 스케줄러(6.10)를 붙이려면 애초에 방해였다
- 메모리 절약 튜닝(Hikari pool 5, Tomcat 80 threads, RAMPercentage) 완화
- `TZ=Asia/Seoul` (근본 해결은 6.9)
- app + postgres + cloudflared를 묶은 `docker-compose.yml` 작성 (지금 것은 postgres만 있는 로컬 개발용)

### 1단계: 모델 재정의 (재작성의 핵심)

**Flyway를 V1부터 새로 시작한다.** 기존 V1~V11은 폐기. 처음부터 `ddl-auto=validate`, 시각 컬럼은 `timestamptz`, `ON DELETE` 정책 명시.

- `calendar_members` 도입, 스케줄 관련 전부를 멤버 스코프로 이동
- `member_rules` 단일 규칙 모델로 `calendar_patterns` + `calendar_weekly_rules` 통합
- `exceptions` → `day_assignments`(멤버 스코프)로 이관, 낙관적 잠금 도입
- `ShiftCodeDefinition` / `LEGACY_DEFAULTS` 제거, `schedule_types` 단일화
- `user_settings.pattern_codes` / `pattern_start_date` 제거
- 공유 모델 한 벌로 통합 — **"권한은 더해질 수만 있다"는 성질을 깨는 게 핵심**, 그룹 탈퇴와 공유 취소 경로 추가
- **규칙 엔진을 순수 함수로 분리하고 테스트 작성**, 권한 모델 테스트 작성

### 2단계: 교대근무 경험 완성

- 프리셋 딕셔너리 + `requiredTypes` 자동 생성
- `teamOffsets` 기반 "몇 조?" 선택
- 달력 미리보기, 기준일 역산 온보딩, 반복 횟수·순서 편집
- **멤버 여러 명 근무표 동시 표시** — 이게 되는 순간 공유 캘린더가 된다

### 3단계: 겹쳐보기 (4.1의 해석 B)

- **ICS 내보내기(구독 URL)** — 근무표를 구글 캘린더에서 보게 한다. 비용 대비 효과가 가장 큰 항목
- ICS 파서를 `ical4j`로 교체 — 주간·월간 반복과 시각 지원
- 규칙 없이 시작하는 온보딩, 제품 이름·문구 중립화
- URL 라우팅 (딥링크 + 안드로이드 뒤로가기)
- 날짜 셀 오버플로 처리

이벤트 레이어는 **여기서 만들지 않는다.** 테스터들이 실제로 요청하면 그때 판단한다 (4.1).

### 4단계: 일상적으로 쓸 만하게

- **알림 (웹푸시 + 스케줄러)** — 매일 앱을 열게 만드는 유일한 훅
- **서비스 워커 / 오프라인 읽기 캐시** — 병동·현장·지하에서 근무표를 본다
- **다크 모드** — 야간 근무자가 새벽에 여는 앱
- 6.9 타임존 근본 해결 (`timestamptz` + 사용자/캘린더 타임존)
- 사용자 삭제 경로 (6.3의 `ON DELETE`)
- actuator + 진짜 헬스체크(6.8), 에러 추적

### 이후 (필요해지면)

- 이벤트 레이어와 주/일 뷰 — 4.1의 해석 A로 넘어갈 때
- CalDAV(`docs/caldav-integration-plan.md`) — ICS 내보내기로 대부분의 수요가 해결되면 필요 없을 수도 있다. 양방향 편집이 실제로 필요해질 때 다시 검토
- 토큰을 `HttpOnly` 쿠키로, 전면 이스케이핑, CSP — 사용자 범위가 넓어지면

---

## 8. 기존 설계 문서와의 관계

`docs/generalization-refactor-plan.md`의 방향(일정 타입 중심, `calendar_rules` 통합, 개인 설정과 캘린더 설정 분리)은 **전부 맞다.** 이 문서는 거기에 세 가지를 더한다.

1. 그 문서에 **멤버 개념이 없다.** `calendar_rules`를 `calendar_id`에 매다는 한 "1 캘린더 = 1인분" 한계가 그대로 남는다. 공유 캘린더 서비스가 목표라면 이게 빠진 조각이다.
2. 그 문서에 **이벤트 레이어가 없다.** 일반 사용자를 타겟에 넣는다면 하루-코드 모델만으로는 부족하다.
3. `calendar_rules`의 `rule_type` 분기(`CYCLIC_PATTERN` / `WEEKLY_REPEAT` / `MANUAL_ONLY`)는 3.1의 단일 원시 타입으로 대체하길 권한다. 타입이 나뉘면 resolver도 나뉘고 테스트도 나뉘는데, 실제로는 같은 계산이다.

`docs/calendar-sharing-implementation-plan.md`의 Phase 3이 "여러 경로로 권한이 들어오면 가장 높은 권한을 적용한다"고 정해뒀는데, 2.5에서 본 것처럼 **이 규칙 자체가 문제의 원인이다.** 거부와 개별 다운그레이드를 표현할 수 없게 만든다. 재작성 시 이 결정을 다시 검토해야 한다.