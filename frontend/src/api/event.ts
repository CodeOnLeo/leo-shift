import { api } from './client'
import type { EventDetail, EventRange } from './types'

export interface SaveEventBody {
  title: string
  description: string | null
  location: string | null
  startsAt: string
  endsAt: string
  allDay: boolean
  timeZone: string
  /** 단발이면 null. */
  rrule: string | null
  recurrenceEnd: string | null
}

export interface SaveOccurrenceBody {
  /** 어느 회차인지. 옮겨도 이 값으로 계속 가리킨다. */
  originalStart: string
  startsAt: string | null
  endsAt: string | null
  title: string | null
  note: string | null
  cancelled: boolean
}

/** 기간에 걸치는 회차 전부. 월·주·일 화면이 전부 이 하나를 쓴다. */
export function fetchEvents(
  params: { from: string; to: string; calendarIds?: number[] },
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ from: params.from, to: params.to })
  for (const id of params.calendarIds ?? []) query.append('calendarId', String(id))
  return api.get<EventRange>(`/api/events?${query}`, signal)
}

export function createEvent(calendarId: number, body: SaveEventBody) {
  return api.post<EventDetail>(`/api/calendars/${calendarId}/events`, body)
}

/** 시리즈 전체를 고친다. 시작이나 반복이 바뀌면 손댔던 회차는 버려진다. */
export function updateEvent(eventId: number, body: SaveEventBody) {
  return api.put<EventDetail>(`/api/events/${eventId}`, body)
}

export function deleteEvent(eventId: number) {
  return api.delete<void>(`/api/events/${eventId}`)
}

/** 반복 중 한 회차만. 휴강·보강·이번 주만 제목 변경. */
export function saveOccurrence(eventId: number, body: SaveOccurrenceBody) {
  return api.put<void>(`/api/events/${eventId}/occurrences`, body)
}

/** 손댄 회차를 규칙대로 되돌린다. */
export function restoreOccurrence(eventId: number, originalStart: string) {
  return api.delete<void>(
    `/api/events/${eventId}/occurrences?originalStart=${encodeURIComponent(originalStart)}`,
  )
}

/** 시리즈 원본. 편집 폼이 반복 규칙까지 그대로 받아야 한다. */
export function fetchEvent(eventId: number, signal?: AbortSignal) {
  return api.get<EventDetail>(`/api/events/${eventId}`, signal)
}
