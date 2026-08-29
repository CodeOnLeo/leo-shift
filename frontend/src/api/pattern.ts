import { api } from './client'
import type { Preset, WorkRule } from './types'

export function fetchPresets(signal?: AbortSignal) {
  return api.get<Preset[]>('/api/presets', signal)
}

export function fetchWorkRule(calendarId: number, signal?: AbortSignal) {
  return api.get<WorkRule | null>(`/api/calendars/${calendarId}/work-rule`, signal)
}

export function applyPattern(
  calendarId: number,
  body: {
    presetId?: string
    teamLabel?: string
    sequence?: string[]
    anchorDate: string
    effectiveFrom: string
  },
) {
  return api.put<WorkRule>(`/api/calendars/${calendarId}/work-rule`, body)
}
