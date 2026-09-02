/**
 * 시각 유틸.
 *
 * {@link ./date.ts}가 다루는 "날짜 문자열"과 구분해야 한다. 저기서는
 * `new Date("2026-03-10")`을 금지했는데, 그건 시간대 정보가 없어서 UTC 자정으로
 * 해석되기 때문이다. 여기서 다루는 값은 시간대가 붙은 완전한 ISO 시각이라
 * Date 생성자에 넣어도 모호하지 않다.
 */

/** 서버가 주는 ISO 시각(예: 2026-03-10T11:30:00Z). */
export type Instant = string

export function parseInstant(iso: Instant): Date {
  return new Date(iso)
}

/** 이 브라우저의 시간대. 반복을 벽시계 기준으로 펼치려면 서버가 이걸 알아야 한다. */
export function browserZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

/** 화면에서 고른 날짜·시각을 서버가 받는 절대 시각으로. */
export function toInstant(date: string, time: string): Instant {
  const [y, m, d] = date.split('-').map(Number)
  const [hh, mm] = time.split(':').map(Number)
  if (!y || !m || !d) throw new Error(`날짜 형식이 아닙니다: ${date}`)
  return new Date(y, m - 1, d, hh ?? 0, mm ?? 0, 0, 0).toISOString()
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** 로컬 기준 YYYY-MM-DD. */
export function instantDate(iso: Instant): string {
  const at = parseInstant(iso)
  return `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}`
}

/** 로컬 기준 HH:mm. */
export function instantTime(iso: Instant): string {
  const at = parseInstant(iso)
  return `${pad(at.getHours())}:${pad(at.getMinutes())}`
}

/** 자정부터의 분. 시간표의 세로 위치가 이 값에서 나온다. */
export function minutesOfDay(iso: Instant): number {
  const at = parseInstant(iso)
  return at.getHours() * 60 + at.getMinutes()
}

export function formatTimeRange(start: Instant, end: Instant): string {
  return `${instantTime(start)}–${instantTime(end)}`
}

/** 분을 HH:mm으로. 시간축 눈금에 쓴다. */
export function formatMinutes(minutes: number): string {
  return `${pad(Math.floor(minutes / 60) % 24)}:${pad(minutes % 60)}`
}

/** 지금 시각이 그날의 몇 분인지. 오늘 열에 "지금" 선을 긋는 데 쓴다. */
export function nowMinutes(): number {
  const now = new Date()
  return now.getHours() * 60 + now.getMinutes()
}
