import type { RecurrenceFreq } from '@/api/types'

/**
 * 반복 규칙을 화면 상태와 표준 RRULE 사이에서 옮긴다.
 *
 * 저장은 표준 RRULE 문자열이라 나중에 ICS 내보내기와 바로 맞고, 서버도 같은
 * 문자열을 파싱한다. 화면만 쓰는 별도 형식을 두면 두 벌이 어긋난다.
 */

export interface RecurrenceDraft {
  /** null이면 반복하지 않는다. */
  freq: RecurrenceFreq | null
  interval: number
  /** ISO 요일(월=1 … 일=7). 매주에서만 쓴다. */
  weekdays: number[]
}

/** RFC 5545 BYDAY 약어. 월요일부터다. */
const DAY_CODES = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'] as const
const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'] as const

export const NO_REPEAT: RecurrenceDraft = { freq: null, interval: 1, weekdays: [] }

export function parseRrule(rrule: string | null): RecurrenceDraft {
  if (!rrule) return NO_REPEAT

  const parts = new Map<string, string>()
  for (const chunk of rrule.toUpperCase().split(';')) {
    const [key, value] = chunk.split('=')
    if (key && value) parts.set(key.trim(), value.trim())
  }

  const freq = parts.get('FREQ') as RecurrenceFreq | undefined
  if (!freq) return NO_REPEAT

  return {
    freq,
    interval: Number(parts.get('INTERVAL') ?? 1) || 1,
    weekdays: (parts.get('BYDAY') ?? '')
      .split(',')
      .map((code) => DAY_CODES.indexOf(code.trim() as (typeof DAY_CODES)[number]) + 1)
      .filter((day) => day > 0)
      .sort((a, b) => a - b),
  }
}

export function toRrule(draft: RecurrenceDraft): string | null {
  if (!draft.freq) return null

  let text = `FREQ=${draft.freq}`
  if (draft.interval > 1) text += `;INTERVAL=${draft.interval}`
  // 요일 지정은 매주에서만 의미가 있다. 서버도 다른 주기에서는 거부한다.
  if (draft.freq === 'WEEKLY' && draft.weekdays.length > 0) {
    text += `;BYDAY=${draft.weekdays.map((day) => DAY_CODES[day - 1]).join(',')}`
  }
  return text
}

/** "격주 화, 목"처럼 읽히는 요약. */
export function describeRecurrence(draft: RecurrenceDraft): string {
  if (!draft.freq) return '반복 안 함'

  const every =
    draft.interval === 1
      ? { DAILY: '매일', WEEKLY: '매주', MONTHLY: '매월', YEARLY: '매년' }[draft.freq]
      : { DAILY: '일', WEEKLY: '주', MONTHLY: '개월', YEARLY: '년' }[draft.freq]

  const prefix = draft.interval === 1 ? every : `${draft.interval}${every}마다`

  if (draft.freq === 'WEEKLY' && draft.weekdays.length > 0) {
    return `${prefix} ${draft.weekdays.map((day) => DAY_LABELS[day - 1]).join(', ')}`
  }
  return prefix
}

export function weekdayOptions(): readonly { value: number; label: string }[] {
  return DAY_LABELS.map((label, index) => ({ value: index + 1, label }))
}
