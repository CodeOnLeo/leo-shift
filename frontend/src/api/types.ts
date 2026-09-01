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

export interface CalendarEvent {
  id: number
  calendarId: number
  title: string
  description: string | null
  location: string | null
  startsAt: Iso8601DateTime
  endsAt: Iso8601DateTime
  allDay: boolean
  /** 반복 일정의 한 회차라면 원래 시각. 개별 회차 수정에 쓴다. */
  occurrenceOf: Iso8601DateTime | null
  cancelled: boolean
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
