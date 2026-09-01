import { api } from './client'
import type { ShareLevel, ShareOverview, ShareTarget } from './types'

export function fetchShares(signal?: AbortSignal) {
  return api.get<ShareOverview>('/api/shares', signal)
}

/**
 * 대상 하나의 공개 단계를 정한다.
 *
 * 처음 공유하는 것과 단계를 바꾸는 것이 같은 호출이다. 서버가 매번 내 캘린더
 * 전부를 다시 훑어 상태를 통째로 만들므로, 단계를 낮췄을 때 예전 공유가 남지 않는다.
 */
export function setShare(
  target: { targetType: 'GROUP'; groupId: number } | { targetType: 'USER'; email: string },
  level: ShareLevel,
) {
  return api.put<ShareTarget>('/api/shares', { ...target, level })
}

export function revokeShare(targetType: 'GROUP' | 'USER', targetId: number) {
  return api.delete<void>(`/api/shares/${targetType}/${targetId}`)
}

export function respondToShare(shareId: number, accept: boolean) {
  return api.post<void>(`/api/shares/incoming/${shareId}/${accept ? 'accept' : 'reject'}`)
}
