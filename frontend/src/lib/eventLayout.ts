import type { EventInstance } from '@/api/types'
import { instantDate, minutesOfDay, parseInstant } from '@/lib/datetime'

/**
 * 시간표 배치 계산.
 *
 * 화면 코드에서 떼어 둔 이유는 <b>겹침 배치가 눈으로 검산되지 않기 때문</b>이다.
 * 세 개가 부분적으로 겹칠 때 몇 칸으로 나뉘어야 하는지는 그려 보고 알기 어렵다.
 */

/** 시간표에 놓인 한 조각. 하루를 넘는 일정은 날마다 조각이 하나씩 생긴다. */
export interface Segment {
  instance: EventInstance
  /** 이 날 기준 시작·끝 분. 하루 경계에서 잘린다. */
  startMin: number
  endMin: number
  /** 왼쪽에서 몇 번째 칸인지와, 이 덩어리가 몇 칸으로 나뉘는지. */
  column: number
  columns: number
  /** 앞뒤 날로 이어지는 일정. 잘렸다는 표시를 하는 데 쓴다. */
  continuesBefore: boolean
  continuesAfter: boolean
}

const DAY_MINUTES = 24 * 60

/** 화면에서 너무 얇아 못 누르는 조각을 막는 최소 높이(분). */
const MIN_SPAN = 20

/**
 * 하루치 조각.
 *
 * 자정을 넘는 일정은 날마다 잘라서 각 날에 그린다. 자르지 않으면 야간 근무 중
 * 일정이 시작한 날에만 나오고 끝나는 날에는 사라진다.
 */
export function daySegments(instances: readonly EventInstance[], date: string): Segment[] {
  const dayStart = new Date(`${date}T00:00:00`).getTime()
  const dayEnd = dayStart + DAY_MINUTES * 60_000

  const segments: Segment[] = []

  for (const instance of instances) {
    if (instance.allDay) continue

    const start = parseInstant(instance.startsAt).getTime()
    const end = parseInstant(instance.endsAt).getTime()
    if (end <= dayStart || start >= dayEnd) continue

    const clippedStart = Math.max(start, dayStart)
    const clippedEnd = Math.min(end, dayEnd)

    segments.push({
      instance,
      startMin: Math.round((clippedStart - dayStart) / 60_000),
      endMin: Math.round((clippedEnd - dayStart) / 60_000),
      column: 0,
      columns: 1,
      continuesBefore: start < dayStart,
      continuesAfter: end > dayEnd,
    })
  }

  segments.sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin)
  return assignColumns(segments)
}

/**
 * 겹치는 것끼리 나란히 놓는다.
 *
 * 서로 겹치는 조각들을 한 덩어리로 묶고, 덩어리 안에서 자리가 빈 칸에 넣는다.
 * 덩어리 전체가 같은 칸 수를 쓰므로 폭이 들쭉날쭉해지지 않는다.
 */
function assignColumns(segments: Segment[]): Segment[] {
  let cluster: Segment[] = []
  let clusterEnd = -1

  const flush = () => {
    if (cluster.length === 0) return
    const columnEnds: number[] = []

    for (const segment of cluster) {
      let column = columnEnds.findIndex((end) => end <= segment.startMin)
      if (column < 0) {
        column = columnEnds.length
        columnEnds.push(0)
      }
      columnEnds[column] = displayEnd(segment)
      segment.column = column
    }
    for (const segment of cluster) segment.columns = columnEnds.length
    cluster = []
  }

  for (const segment of segments) {
    // 화면상 높이를 기준으로 겹침을 판단한다. 5분짜리 두 개가 붙어 있으면
    // 시각으로는 안 겹쳐도 그려 놓으면 겹쳐 보인다.
    if (cluster.length > 0 && segment.startMin >= clusterEnd) flush()
    cluster.push(segment)
    clusterEnd = Math.max(clusterEnd, displayEnd(segment))
  }
  flush()

  return segments
}

function displayEnd(segment: Segment): number {
  return Math.max(segment.endMin, segment.startMin + MIN_SPAN)
}

/** 조각의 화면상 위·아래 위치(%). 창의 시작·끝 분을 기준으로 한다. */
export function placement(segment: Segment, windowFrom: number, windowTo: number) {
  const span = Math.max(windowTo - windowFrom, 1)
  const top = ((segment.startMin - windowFrom) / span) * 100
  const height = ((displayEnd(segment) - segment.startMin) / span) * 100
  return {
    top: `${Math.max(top, 0)}%`,
    height: `${Math.min(height, 100 - Math.max(top, 0))}%`,
    left: `${(segment.column / segment.columns) * 100}%`,
    width: `${100 / segment.columns}%`,
  }
}

/**
 * 시간축에 보여줄 구간.
 *
 * 늘 0~24시를 그리면 대부분이 빈칸이라 정작 일정이 몰린 저녁이 눌린다.
 * 기본 창을 두되 <b>일정이 창 밖에 있으면 반드시 넓힌다</b> — 안 보이는 일정이
 * 생기는 것보다 화면이 길어지는 편이 낫다.
 */
export function timeWindow(
  instances: readonly EventInstance[],
  dates: readonly string[],
  fallback: [number, number] = [8 * 60, 22 * 60],
): [number, number] {
  let from = fallback[0]
  let to = fallback[1]

  for (const date of dates) {
    for (const segment of daySegments(instances, date)) {
      from = Math.min(from, floorHour(segment.startMin))
      to = Math.max(to, ceilHour(segment.endMin))
    }
  }
  return [Math.max(from, 0), Math.min(to, DAY_MINUTES)]
}

const floorHour = (minutes: number) => Math.floor(minutes / 60) * 60
const ceilHour = (minutes: number) => Math.ceil(minutes / 60) * 60

/** 시간축 눈금. 한 시간 간격이다. */
export function hourMarks(from: number, to: number): number[] {
  const marks: number[] = []
  for (let minute = ceilHour(from); minute <= to; minute += 60) marks.push(minute)
  return marks
}

/** 종일 일정만. 시간축이 아니라 위쪽 띠에 놓인다. */
export function allDayOn(instances: readonly EventInstance[], date: string): EventInstance[] {
  return instances.filter(
    (instance) => instance.allDay && instantDate(instance.startsAt) <= date
      && date <= instantDate(instance.endsAt),
  )
}

/** 그날 시작하는 일정 (월 달력·날짜 상세에서 쓴다). */
export function startingOn(instances: readonly EventInstance[], date: string): EventInstance[] {
  return instances
    .filter((instance) => instantDate(instance.startsAt) === date)
    .sort((a, b) => minutesOfDay(a.startsAt) - minutesOfDay(b.startsAt))
}
