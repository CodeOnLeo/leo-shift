import { api } from './client'
import type { MyCalendar } from './types'

export interface SaveCalendarBody {
  name: string
  description: string | null
  color: string | null
}

/** 내가 소유한 캘린더만. 공유받은 것은 이름을 바꾸거나 지울 수 없다. */
export function fetchMyCalendars(signal?: AbortSignal) {
  return api.get<MyCalendar[]>('/api/my/calendars', signal)
}

/** 새로 만드는 캘린더는 전부 개인 일정용이다. 근무 캘린더는 하나만 둔다. */
export function createCalendar(body: SaveCalendarBody) {
  return api.post<MyCalendar>('/api/my/calendars', body)
}

export function updateCalendar(calendarId: number, body: SaveCalendarBody) {
  return api.put<MyCalendar>(`/api/my/calendars/${calendarId}`, body)
}

/** 일정을 만들 때 미리 골라져 있는 캘린더. */
export function setDefaultCalendar(calendarId: number) {
  return api.put<void>(`/api/my/calendars/${calendarId}/default`)
}

export function deleteCalendar(calendarId: number) {
  return api.delete<void>(`/api/my/calendars/${calendarId}`)
}
