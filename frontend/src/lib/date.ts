/**
 * 날짜 유틸.
 *
 * 이전 구현은 `new Date("2026-03-10")`을 쓰는 곳이 있었는데, 이건 UTC 자정으로
 * 파싱돼서 UTC보다 서쪽 시간대에서 하루가 밀렸다. 기념일 삭제 확인 창이 틀린
 * 연도를 보여주고 데이터를 지운 적도 있다.
 *
 * 그래서 날짜 문자열은 절대 Date 생성자에 직접 넣지 않는다.
 */

/** YYYY-MM-DD를 로컬 자정의 Date로. */
export function parseLocalDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) throw new Error(`날짜 형식이 아닙니다: ${iso}`)
  return new Date(y, m - 1, d)
}

/** Date를 YYYY-MM-DD로. 로컬 기준이다. */
export function formatLocalDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 오늘. 서버가 아니라 사용자의 로컬 날짜다. */
export function today(): string {
  return formatLocalDate(new Date())
}

export function addDays(iso: string, days: number): string {
  const date = parseLocalDate(iso)
  date.setDate(date.getDate() + days)
  return formatLocalDate(date)
}

export function addMonths(iso: string, months: number): string {
  const date = parseLocalDate(iso)
  date.setDate(1)
  date.setMonth(date.getMonth() + months)
  return formatLocalDate(date)
}

/** ISO-8601 요일. 월=1 ... 일=7 */
export function isoWeekday(iso: string): number {
  const day = parseLocalDate(iso).getDay()
  return day === 0 ? 7 : day
}

export function isWeekend(iso: string): boolean {
  return isoWeekday(iso) >= 6
}

const WEEKDAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'] as const

export function weekdayLabel(iso: string): string {
  return WEEKDAY_LABELS[isoWeekday(iso) - 1] ?? ''
}

/** 월요일 시작 기준 요일 머리글. 앱 전체가 같은 순서를 쓴다. */
export function weekdayHeaders(): readonly string[] {
  return WEEKDAY_LABELS
}

export function formatKoreanDate(iso: string): string {
  const date = parseLocalDate(iso)
  return `${date.getMonth() + 1}월 ${date.getDate()}일 (${weekdayLabel(iso)})`
}

/**
 * 월 달력 격자에 들어갈 날짜들. 월요일 시작, 필요한 만큼의 주만 만든다.
 * 이전 구현은 42칸으로 고정해서 마지막 줄이 비어도 그대로 그렸다.
 */
export function monthGrid(year: number, month: number): string[] {
  const first = new Date(year, month - 1, 1)
  const start = new Date(first)
  start.setDate(first.getDate() - ((first.getDay() + 6) % 7))

  const last = new Date(year, month, 0)
  const end = new Date(last)
  end.setDate(last.getDate() + ((7 - ((last.getDay() + 6) % 7) - 1) % 7))

  const dates: string[] = []
  for (const cursor = new Date(start); cursor <= end; cursor.setDate(cursor.getDate() + 1)) {
    dates.push(formatLocalDate(cursor))
  }
  return dates
}

/** 해당 날짜가 속한 주(월~일). */
export function weekOf(iso: string): string[] {
  const monday = addDays(iso, -(isoWeekday(iso) - 1))
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i))
}
