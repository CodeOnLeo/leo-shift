/**
 * 서버와 주고받는 타입.
 *
 * 백엔드 API가 아직 없으므로 docs/domain-design.md의 모델을 그대로 옮긴 초안이다.
 * API를 만들면서 맞춰 나간다.
 */

export type Iso8601Date = string      // 2026-03-10
export type Iso8601DateTime = string  // 2026-03-10T20:30:00+09:00

export type ScheduleCategory = 'WORK' | 'OFF' | 'LEAVE'

export interface ScheduleType {
  code: string
  name: string
  color: string
  category: ScheduleCategory
  startTime: string | null
  endTime: string | null
  crossesMidnight: boolean
  halfDay: boolean
}

/** 코드가 어디서 왔는지. 화면에서 "왜 이 근무인지" 보여주거나 편집 대상을 정할 때 쓴다. */
export type DaySource = 'RULE' | 'LEAVE' | 'OVERRIDE' | 'NONE'

export interface ResolvedDay {
  date: Iso8601Date
  code: string | null
  source: DaySource
  sourceId: number | null
  note: string | null
}

export interface CalendarSummary {
  id: number
  name: string
  color: string | null
  kind: 'WORK' | 'GENERAL'
  isDefault: boolean
  /** 내가 소유한 캘린더인가. 공유받은 캘린더도 그 사람에게는 기본이라 isDefault로는 못 고른다. */
  mine: boolean
  /** 공유받은 캘린더는 이름이 겹치므로("내 근무") 목록에서 이걸로 구분한다. */
  ownerName: string | null
  ownedByGroup: boolean
  canEdit: boolean
  visibility: 'FULL' | 'BUSY_ONLY'
}

/** 내가 소유한 캘린더. 공유받은 것은 여기 나오지 않는다. */
export interface MyCalendar {
  id: number
  name: string
  description: string | null
  color: string | null
  kind: 'WORK' | 'GENERAL'
  isDefault: boolean
  /** 마지막 캘린더는 지울 수 없다. */
  removable: boolean
}

export type RecurrenceFreq = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'

/** 이 회차에 손댄 흔적. 취소된 회차도 지우지 않고 내려온다. */
export type EventChange = 'NONE' | 'CANCELLED' | 'MOVED' | 'MODIFIED'

/** 달력에 실제로 그려지는 한 칸. 단발도 회차가 하나인 반복으로 취급한다. */
export interface EventInstance {
  eventId: number
  calendarId: number
  calendarName: string
  color: string | null
  /** 이 회차의 원래 시각. 옮긴 회차를 다시 가리키는 열쇠다. */
  occurrenceStart: Iso8601DateTime
  startsAt: Iso8601DateTime
  endsAt: Iso8601DateTime
  allDay: boolean
  recurring: boolean
  title: string
  description: string | null
  location: string | null
  change: EventChange
  canEdit: boolean
}

export interface EventRange {
  from: Iso8601DateTime
  to: Iso8601DateTime
  instances: EventInstance[]
}

/** 편집 화면이 쓰는 원본. 회차가 아니라 시리즈 전체다. */
export interface EventDetail {
  id: number
  calendarId: number
  title: string
  description: string | null
  location: string | null
  startsAt: Iso8601DateTime
  endsAt: Iso8601DateTime
  allDay: boolean
  timeZone: string
  /** FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH 형태. 단발이면 null. */
  rrule: string | null
  recurrenceEnd: Iso8601DateTime | null
  version: number
}

export interface ScheduleRange {
  calendarId: number
  from: Iso8601Date
  to: Iso8601Date
  days: ResolvedDay[]
  scheduleTypes: ScheduleType[]
  /** 코드별 일수. 요청에 year/month를 주면 그 달만 센다. */
  summary: Record<string, number>
}

/**
 * 구독한 캘린더를 달력에 어떻게 그릴지.
 *
 * BADGE는 있다는 표시만, INLINE은 제목까지, HIDDEN은 아예 가져오지 않는다.
 * 구독을 지우지 않고 잠깐 치우고 싶을 때가 있어서 HIDDEN이 따로 있다.
 */
export type ExternalDisplayMode = 'BADGE' | 'INLINE' | 'HIDDEN'

export interface ExternalSource {
  id: number
  calendarId: number
  calendarName: string
  name: string
  feedUrl: string
  color: string | null
  displayMode: ExternalDisplayMode
  active: boolean
  syncIntervalMinutes: number
  lastSyncedAt: Iso8601DateTime | null
  /** 원격 서버가 준 문구가 섞여 있다. 반드시 텍스트로만 넣을 것. */
  lastError: string | null
  /** 가져온 일정 수. 동기화가 실제로 됐는지 아는 유일한 신호다. */
  eventCount: number
}

/** 구독해 온 일정. 우리 일정과 달리 편집할 수 없다. */
export interface ExternalEvent {
  sourceId: number
  sourceName: string
  calendarId: number
  color: string | null
  displayMode: ExternalDisplayMode
  startsAt: Iso8601DateTime
  endsAt: Iso8601DateTime
  allDay: boolean
  title: string | null
  description: string | null
  location: string | null
}

export interface ExternalRange {
  from: Iso8601DateTime
  to: Iso8601DateTime
  events: ExternalEvent[]
}

export interface SyncResult {
  source: ExternalSource
  imported: number
  /** 실패했을 때만 채워진다. 성공이면 null. */
  error: string | null
}

/**
 * 내보내기용 구독 주소.
 *
 * 이 주소를 아는 사람은 누구나 캘린더를 볼 수 있다. 화면에서 그 사실을 반드시
 * 함께 알려야 한다.
 */
export interface FeedToken {
  id: number
  calendarId: number
  url: string
  visibility: 'FULL' | 'BUSY_ONLY'
  createdAt: Iso8601DateTime
  /** 마지막으로 누가 읽어간 시각. 구독이 실제로 걸렸는지 아는 신호다. */
  lastUsedAt: Iso8601DateTime | null
}

export type GroupKind = 'PROJECT' | 'WORKPLACE' | 'FAMILY' | 'FRIENDS' | 'OTHER'

export interface GroupSummary {
  id: number
  name: string
  kind: GroupKind
  color: string | null
  description: string | null
  memberCount: number
  owner: boolean
}

export interface GroupMember {
  memberId: number
  userId: number
  name: string
  nickname: string | null
  email: string
  colorTag: string | null
  role: 'OWNER' | 'MEMBER'
  joinedOn: Iso8601Date
  /** null이면 현재 소속 중. 내보내도 행은 남고 이 날짜만 채워진다. */
  leftOn: Iso8601Date | null
  active: boolean
  self: boolean
  /** 이 사람이 캘린더를 이 그룹에 공유했는가. 아니면 타임라인 줄이 비어 있다. */
  shared: boolean
}

export interface GroupDetail {
  id: number
  name: string
  kind: GroupKind
  color: string | null
  description: string | null
  owner: boolean
  members: GroupMember[]
}

/** 그룹 타임라인 격자의 한 칸. */
export interface TimelineDay {
  date: Iso8601Date
  code: string | null
  name: string | null
  color: string | null
  /** 사람마다 코드가 달라도 이 값으로 집계된다. */
  category: ScheduleCategory | null
  /** 그날 이 그룹에 소속돼 있었는가. */
  member: boolean
}

export interface TimelineRow {
  userId: number
  displayName: string
  colorTag: string | null
  self: boolean
  shared: boolean
  days: TimelineDay[]
}

export interface GroupTimeline {
  groupId: number
  groupName: string
  groupKind: GroupKind
  from: Iso8601Date
  to: Iso8601Date
  dates: Iso8601Date[]
  rows: TimelineRow[]
  /** 날짜별 근무 인원. 프로젝트에서 실제로 보고 싶은 값이다. */
  workingCount: number[]
  absentCount: number[]
  /** 내 캘린더가 이 그룹에 공유돼 있는가. */
  viewerShared: boolean
}

/** 화면이 다루는 공개 단계. 저장은 두 축이지만 그 번역은 서버가 한다. */
export type ShareLevel = 'WORK_ONLY' | 'FULL'

export interface ShareTarget {
  targetType: 'GROUP' | 'USER'
  targetId: number
  name: string
  email: string | null
  level: ShareLevel
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  pending: boolean
  memberCount: number
  calendarCount: number
}

export interface IncomingShare {
  id: number
  calendarId: number
  calendarName: string
  ownerName: string
  ownerEmail: string | null
  permission: 'VIEW' | 'EDIT'
  visibility: 'FULL' | 'BUSY_ONLY'
}

export interface ShareOverview {
  targets: ShareTarget[]
  incoming: IncomingShare[]
  workCalendarCount: number
  /** 0이면 "근무만"과 "전체"가 같은 것을 공유한다. 그때는 단계를 묻지 않는다. */
  personalCalendarCount: number
}

export interface CurrentUser {
  id: number
  name: string
  nickname: string | null
  email: string
  colorTag: string | null
  timeZone: string
}

export interface PresetScheduleType {
  code: string
  name: string
  color: string
  category: ScheduleCategory
  startTime: string | null
  endTime: string | null
  crossesMidnight: boolean
}

export interface PresetTeam {
  label: string
  offset: number
}

export interface Preset {
  id: string
  name: string
  category: 'REGULAR' | 'SHIFT'
  tags: string[]
  description: string | null
  /** REGULAR은 기준일이 이 요일로 맞춰진다. SHIFT는 null. */
  anchorWeekday: string | null
  cycleLength: number
  sequence: string[]
  scheduleTypes: PresetScheduleType[]
  teams: PresetTeam[]
  /** 기준일을 묻는 방법. "가장 최근 야간을 시작한 날은?" */
  anchorCode: string | null
  anchorQuestion: string | null
}

export interface WorkRule {
  id: number
  anchorDate: Iso8601Date
  cycleLength: number
  sequence: string[]
  effectiveFrom: Iso8601Date
  effectiveTo: Iso8601Date | null
  sourcePresetId: string | null
}

export interface DayOverride {
  id: number
  code: string | null
  note: string | null
  /** 낙관적 잠금. 저장할 때 되돌려줘야 다른 사람 수정을 덮어쓰지 않는다. */
  version: number
}

export interface DayLeave {
  id: number
  startDate: Iso8601Date
  endDate: Iso8601Date
  code: string
  note: string | null
}

export interface DayDetail {
  date: Iso8601Date
  code: string | null
  source: DaySource
  note: string | null
  /** 예외를 지우면 돌아갈 코드. "원래 야간입니다"를 보여준다. */
  baseCode: string | null
  baseSource: DaySource
  override: DayOverride | null
  leave: DayLeave | null
  scheduleType: ScheduleType | null
  scheduleTypes: ScheduleType[]
  canEdit: boolean
}

export interface ScheduleTypeUsage {
  code: string
  inUse: boolean
  usedByRule: boolean
  usedByLeave: boolean
  usedByOverride: boolean
}
