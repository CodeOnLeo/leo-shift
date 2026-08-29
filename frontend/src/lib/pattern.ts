import { addDays, parseLocalDate } from './date'

/**
 * 근무 주기 계산 — 미리보기 전용.
 *
 * 진짜 계산은 서버가 한다 (`io.github.codeonleo.leoshift.schedule.WorkRule`).
 * 여기 있는 것은 사용자가 패턴을 만드는 동안 왕복 없이 즉시 보여주기 위한
 * 같은 공식의 사본이다. 저장한 뒤 화면에 그려지는 근무는 항상 서버가 계산한 값이다.
 *
 *   code(date) = sequence[ floorMod(date - anchorDate, sequence.length) ]
 */

function floorMod(value: number, modulus: number): number {
  return ((value % modulus) + modulus) % modulus
}

const MS_PER_DAY = 24 * 60 * 60 * 1000

/** 두 날짜의 일수 차이. 로컬 자정 기준이라 서머타임 영향을 받지 않는다. */
function daysBetween(from: string, to: string): number {
  const a = parseLocalDate(from)
  const b = parseLocalDate(to)
  return Math.round((b.getTime() - a.getTime()) / MS_PER_DAY)
}

export function codeAt(sequence: readonly string[], anchorDate: string, date: string): string | null {
  if (sequence.length === 0) return null
  return sequence[floorMod(daysBetween(anchorDate, date), sequence.length)] ?? null
}

/** 시퀀스를 왼쪽으로 offset만큼 회전. 조 선택에 쓴다. */
export function rotate(sequence: readonly string[], offset: number): string[] {
  if (sequence.length === 0) return []
  const shift = floorMod(offset, sequence.length)
  return sequence.map((_, i) => sequence[(i + shift) % sequence.length]!)
}

/**
 * 해당 코드가 연속으로 시작하는 첫 위치. 기준일 역산에 쓴다.
 * 시퀀스는 순환하므로 run의 시작도 순환 기준으로 찾는다.
 * 예: [N,O,O,N,N] 에서 N의 run 시작은 0이 아니라 3이다.
 */
export function firstRunStart(sequence: readonly string[], code: string): number | null {
  const size = sequence.length
  if (size === 0) return null
  let found = false
  for (let i = 0; i < size; i++) {
    if (sequence[i] !== code) continue
    found = true
    if (sequence[floorMod(i - 1, size)] !== code) return i
  }
  // 전부 같은 코드면 run 경계가 없다
  return found ? 0 : null
}

/** "이 근무를 시작한 날이 date였다"는 답으로 기준일을 역산한다. */
export function inferAnchor(
  sequence: readonly string[],
  code: string,
  date: string,
): string | null {
  const index = firstRunStart(sequence, code)
  return index === null ? null : addDays(date, -index)
}

/** 주기가 요일과 다시 맞아떨어지기까지 걸리는 일수. 교대근무자가 궁금해하는 값이다. */
export function weekAlignmentDays(cycleLength: number): number {
  if (cycleLength <= 0) return 0
  const gcd = (a: number, b: number): number => (b === 0 ? a : gcd(b, a % b))
  return (cycleLength * 7) / gcd(cycleLength, 7)
}

/** [{code, count}] 형태로 압축. "주간 ×3" 처럼 보여줄 때 쓴다. */
export function runLengths(sequence: readonly string[]): { code: string; count: number }[] {
  const runs: { code: string; count: number }[] = []
  for (const code of sequence) {
    const last = runs[runs.length - 1]
    if (last && last.code === code) last.count += 1
    else runs.push({ code, count: 1 })
  }
  return runs
}
