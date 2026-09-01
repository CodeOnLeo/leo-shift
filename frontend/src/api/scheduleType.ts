import { api } from './client'
import type { ScheduleType, ScheduleTypeUsage } from './types'

export interface SaveScheduleTypeBody {
  code: string
  name: string
  color: string
  category: 'WORK' | 'OFF' | 'LEAVE'
  startTime: string | null
  endTime: string | null
  halfDay: boolean
  sortOrder?: number
}

export function fetchScheduleTypes(calendarId: number, signal?: AbortSignal) {
  return api.get<ScheduleType[]>(`/api/calendars/${calendarId}/schedule-types`, signal)
}

export function fetchUsage(calendarId: number, code: string, signal?: AbortSignal) {
  return api.get<ScheduleTypeUsage>(
    `/api/calendars/${calendarId}/schedule-types/${code}/usage`,
    signal,
  )
}

export function createScheduleType(calendarId: number, body: SaveScheduleTypeBody) {
  return api.post<ScheduleType>(`/api/calendars/${calendarId}/schedule-types`, body)
}

/** 코드를 바꾸면 반복 근무·휴가·날짜별 변경의 참조가 서버에서 함께 따라간다. */
export function updateScheduleType(calendarId: number, code: string, body: SaveScheduleTypeBody) {
  return api.put<ScheduleType>(`/api/calendars/${calendarId}/schedule-types/${code}`, body)
}

export function deleteScheduleType(calendarId: number, code: string) {
  return api.delete<void>(`/api/calendars/${calendarId}/schedule-types/${code}`)
}
