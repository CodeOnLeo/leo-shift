import { api } from './client'
import type { DayDetail } from './types'

export function fetchDay(calendarId: number, date: string, signal?: AbortSignal) {
  return api.get<DayDetail>(`/api/calendars/${calendarId}/days/${date}`, signal)
}

/** 코드도 메모도 비우면 예외가 지워지고 규칙으로 돌아간다. */
export function saveDay(
  calendarId: number,
  date: string,
  body: { code: string | null; note: string | null; version: number | null },
) {
  return api.put<DayDetail>(`/api/calendars/${calendarId}/days/${date}`, body)
}

export function clearDay(calendarId: number, date: string) {
  return api.delete<DayDetail>(`/api/calendars/${calendarId}/days/${date}`)
}

export function addLeave(
  calendarId: number,
  body: { startDate: string; endDate: string; code: string; note?: string },
) {
  return api.post<void>(`/api/calendars/${calendarId}/leaves`, body)
}

export function deleteLeave(calendarId: number, leaveId: number) {
  return api.delete<void>(`/api/calendars/${calendarId}/leaves/${leaveId}`)
}
