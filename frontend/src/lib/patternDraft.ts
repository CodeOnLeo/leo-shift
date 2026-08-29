import type { Preset } from '@/api/types'
import { today } from './date'
import { inferAnchor, rotate } from './pattern'

/**
 * 패턴 설정 화면이 다루는 값.
 *
 * 화면에서 상태를 열 개씩 들고 있으면 조를 바꿀 때 시퀀스와 기준일이 함께
 * 움직여야 한다는 규칙이 흩어진다. 여기 순수 함수로 모아 두면 규칙이 한 곳에 있고
 * 나중에 테스트하기도 쉽다.
 */
export interface PatternDraft {
  preset: Preset | null
  team: string | null
  sequence: string[]
  /** 사용자가 답한 날짜. "야간을 시작한 날" 같은 질문의 답 */
  anchorAnswer: string
  /** 답에서 역산한 실제 기준일 */
  anchorDate: string
  effectiveFrom: string
  repeatCount: number
}

export function emptyDraft(): PatternDraft {
  const now = today()
  return {
    preset: null,
    team: null,
    sequence: [],
    anchorAnswer: now,
    anchorDate: now,
    effectiveFrom: now,
    repeatCount: 1,
  }
}

function offsetOf(preset: Preset, team: string | null): number {
  return preset.teams.find((option) => option.label === team)?.offset ?? 0
}

/** 답한 날짜로 기준일을 다시 계산한다. 질문이 없는 프리셋이면 답이 곧 기준일이다. */
function withAnchor(draft: PatternDraft, answer: string, sequence: string[]): PatternDraft {
  const code = draft.preset?.anchorCode
  const anchorDate = code ? (inferAnchor(sequence, code, answer) ?? answer) : answer
  return { ...draft, anchorAnswer: answer, anchorDate, sequence }
}

export function selectPreset(preset: Preset): PatternDraft {
  const base = { ...emptyDraft(), preset }
  const team = preset.teams[0]?.label ?? null
  const sequence = team ? rotate(preset.sequence, offsetOf(preset, team)) : [...preset.sequence]
  return withAnchor({ ...base, team }, base.anchorAnswer, sequence)
}

/** 조를 바꾸면 시퀀스가 회전하고 기준일도 함께 다시 계산된다. */
export function selectTeam(draft: PatternDraft, team: string): PatternDraft {
  if (!draft.preset) return draft
  const sequence = rotate(draft.preset.sequence, offsetOf(draft.preset, team))
  return withAnchor({ ...draft, team }, draft.anchorAnswer, sequence)
}

export function answerAnchor(draft: PatternDraft, answer: string): PatternDraft {
  return withAnchor(draft, answer, draft.sequence)
}

/** 순서를 직접 고치면 기준일도 다시 계산해야 한다. run의 시작 위치가 바뀌기 때문이다. */
export function editSequence(draft: PatternDraft, sequence: string[]): PatternDraft {
  return withAnchor(draft, draft.anchorAnswer, sequence)
}
