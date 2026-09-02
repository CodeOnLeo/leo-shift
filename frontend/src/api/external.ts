import { api } from './client'
import type {
  ExternalDisplayMode,
  ExternalRange,
  ExternalSource,
  FeedToken,
  SyncResult,
} from './types'

export interface SaveExternalSourceBody {
  name: string
  feedUrl: string
  color: string | null
  displayMode: ExternalDisplayMode
  active: boolean
  syncIntervalMinutes: number
}

// ------------------------------------------------------------------ 가져오기

export function fetchExternalSources(calendarId: number, signal?: AbortSignal) {
  return api.get<ExternalSource[]>(`/api/calendars/${calendarId}/external-sources`, signal)
}

/**
 * 구독을 만들면 서버가 <b>그 자리에서 한 번 가져온다.</b>
 *
 * 그래서 응답이 구독이 아니라 동기화 결과다. 주소를 잘못 넣었는지 몇 시간 뒤에
 * 알게 되면 안 된다.
 */
export function createExternalSource(calendarId: number, body: SaveExternalSourceBody) {
  return api.post<SyncResult>(`/api/calendars/${calendarId}/external-sources`, body)
}

export function updateExternalSource(sourceId: number, body: SaveExternalSourceBody) {
  return api.put<SyncResult>(`/api/external-sources/${sourceId}`, body)
}

export function deleteExternalSource(sourceId: number) {
  return api.delete<void>(`/api/external-sources/${sourceId}`)
}

export function syncExternalSource(sourceId: number) {
  return api.post<SyncResult>(`/api/external-sources/${sourceId}/sync`)
}

/**
 * 기간에 걸치는 외부 일정. 월·주·일 화면이 전부 이 하나를 쓴다.
 *
 * @param calendarId 비우면 볼 수 있는 캘린더 전부
 */
export function fetchExternalEvents(
  params: { from: string; to: string; calendarId?: number[] },
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ from: params.from, to: params.to })
  for (const id of params.calendarId ?? []) query.append('calendarId', String(id))

  return api.get<ExternalRange>(`/api/external/events?${query}`, signal)
}

// ------------------------------------------------------------------ 내보내기

export function fetchFeedTokens(calendarId: number, signal?: AbortSignal) {
  return api.get<FeedToken[]>(`/api/calendars/${calendarId}/feed-tokens`, signal)
}

export function createFeedToken(calendarId: number, visibility: 'FULL' | 'BUSY_ONLY') {
  return api.post<FeedToken>(`/api/calendars/${calendarId}/feed-tokens`, { visibility })
}

/** 폐기하면 그 주소로 구독 중인 앱에서 근무표가 사라진다. */
export function revokeFeedToken(tokenId: number) {
  return api.delete<void>(`/api/feed-tokens/${tokenId}`)
}
