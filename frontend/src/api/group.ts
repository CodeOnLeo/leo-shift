import { api } from './client'
import type { GroupDetail, GroupKind, GroupSummary, GroupMember, GroupTimeline } from './types'

export interface SaveGroupBody {
  name: string
  kind: GroupKind
  description: string | null
  color: string | null
}

export function fetchGroups(signal?: AbortSignal) {
  return api.get<GroupSummary[]>('/api/groups', signal)
}

export function fetchGroup(groupId: number, signal?: AbortSignal) {
  return api.get<GroupDetail>(`/api/groups/${groupId}`, signal)
}

export function createGroup(body: SaveGroupBody) {
  return api.post<GroupSummary>('/api/groups', body)
}

export function updateGroup(groupId: number, body: SaveGroupBody) {
  return api.put<GroupSummary>(`/api/groups/${groupId}`, body)
}

export function deleteGroup(groupId: number) {
  return api.delete<void>(`/api/groups/${groupId}`)
}

/** 가입한 사람만 추가할 수 있다. 그룹에 넣어도 일정은 그 사람이 공유해야 보인다. */
export function addMember(groupId: number, email: string, joinedOn?: string) {
  return api.post<GroupMember>(`/api/groups/${groupId}/members`, {
    email,
    joinedOn: joinedOn ?? null,
  })
}

export function updateMember(
  groupId: number,
  memberId: number,
  body: { joinedOn: string; leftOn: string | null },
) {
  return api.put<GroupMember>(`/api/groups/${groupId}/members/${memberId}`, body)
}

/** 내보내기. 행을 지우는 게 아니라 종료일을 적으므로 지난달 화면에는 그대로 남는다. */
export function endMembership(groupId: number, memberId: number, leftOn?: string) {
  const query = leftOn ? `?leftOn=${leftOn}` : ''
  return api.delete<void>(`/api/groups/${groupId}/members/${memberId}${query}`)
}

export function leaveGroup(groupId: number) {
  return api.post<void>(`/api/groups/${groupId}/leave`)
}

export function fetchTimeline(
  groupId: number,
  params: { from: string; to: string },
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ from: params.from, to: params.to })
  return api.get<GroupTimeline>(`/api/groups/${groupId}/timeline?${query}`, signal)
}
