import { api } from './client'
import type { CalendarSummary, CurrentUser, ScheduleRange } from './types'

export function fetchMe(signal?: AbortSignal) {
  return api.get<CurrentUser>('/api/me', signal)
}

export function fetchCalendars(signal?: AbortSignal) {
  return api.get<CalendarSummary[]>('/api/calendars', signal)
}

/**
 * 기간의 근무를 가져온다. 월·주·일 화면이 전부 이 하나를 쓴다.
 *
 * @param month 지정하면 요약을 그 달 기준으로 계산한다
 */
export function fetchSchedule(
  params: {
    calendarId: number
    from: string
    to: string
    year?: number
    month?: number
  },
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({
    calendarId: String(params.calendarId),
    from: params.from,
    to: params.to,
  })
  if (params.year !== undefined) query.set('year', String(params.year))
  if (params.month !== undefined) query.set('month', String(params.month))

  return api.get<ScheduleRange>(`/api/schedule?${query}`, signal)
}
