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

export interface GroupSummary {
  id: number
  name: string
  kind: 'PROJECT' | 'WORKPLACE' | 'FAMILY' | 'FRIENDS' | 'OTHER'
  memberCount: number
}

/** 그룹 타임라인 한 사람의 한 줄. */
export interface TimelineRow {
  userId: number
  displayName: string
  colorTag: string | null
  days: ResolvedDay[]
}

export interface GroupTimeline {
  groupId: number
  from: Iso8601Date
  to: Iso8601Date
  rows: TimelineRow[]
  /** 날짜별 가용 인원. 프로젝트에서 실제로 보고 싶은 값이다. */
  availableCount: number[]
  scheduleTypes: ScheduleType[]
}

export interface CurrentUser {
  id: number
  name: string
  nickname: string | null
  email: string
  colorTag: string | null
  timeZone: string
}
