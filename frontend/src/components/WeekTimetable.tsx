import type { EventInstance } from '@/api/types'
import { contrastText } from '@/lib/color'
import { formatMinutes, instantTime, nowMinutes } from '@/lib/datetime'
import {
  allDayOn,
  daySegments,
  entryKey,
  hourMarks,
  placement,
  timeWindow,
  type TimetableEntry,
} from '@/lib/eventLayout'
import { isoWeekday, today, weekdayLabel } from '@/lib/date'
import styles from './WeekTimetable.module.css'

/**
 * 주간 시간표.
 *
 * <b>격자에 맞추지 않는다.</b> 20:30~21:30 같은 시각이 그대로 표현돼야 하므로
 * 분 단위 절대 배치이고, 겹치는 일정은 나란히 놓인다. 시간을 30분 칸에
 * 스냅시키면 과외·병원 예약처럼 정각이 아닌 일정이 전부 틀어진다.
 *
 * 시간축 구간은 일정에 맞춰 늘어난다. 늘 0~24시를 그리면 대부분이 빈칸이라
 * 정작 일정이 몰린 시간대가 눌린다.
 *
 * 구독해 온 일정도 같은 격자에 놓인다. 따로 그리면 회의와 수업이 서로 겹쳐
 * 보이는데, 겹치는지 아닌지를 보려고 이 화면을 여는 것이다.
 */
export function WeekTimetable({
  dates,
  entries,
  codeByDate,
  onSelect,
  onPick,
}: {
  dates: readonly string[]
  entries: readonly TimetableEntry[]
  /** 그날의 근무 코드. 시프트 근무자는 이걸 같이 봐야 주가 읽힌다. */
  codeByDate: ReadonlyMap<string, { code: string; color: string | null }>
  onSelect: (instance: EventInstance) => void
  /** 빈 곳을 눌렀을 때. 그 날짜와 시각으로 새 일정을 연다. */
  onPick: (date: string, minutes: number) => void
}) {
  const [from, to] = timeWindow(entries, dates)
  const marks = hourMarks(from, to)
  const currentDay = today()
  const nowLine = nowMinutes()

  const hasAllDay = dates.some((date) => allDayOn(entries, date).length > 0)

  return (
    <div className={styles.root}>
      <div className={styles.headers}>
        <span className={styles.axisHead} aria-hidden="true" />
        {dates.map((date) => {
          const weekday = isoWeekday(date)
          const work = codeByDate.get(date)
          return (
            <div
              key={date}
              className={styles.dayHead}
              data-weekend={weekday === 6 ? 'sat' : weekday === 7 ? 'sun' : undefined}
              data-today={date === currentDay ? '' : undefined}
            >
              <span className={styles.weekday}>{weekdayLabel(date)}</span>
              <span className={styles.dayNumber}>{Number(date.slice(8, 10))}</span>
              {work ? (
                <span
                  className={styles.workCode}
                  style={
                    work.color
                      ? { background: work.color, color: contrastText(work.color) }
                      : undefined
                  }
                >
                  {work.code}
                </span>
              ) : null}
            </div>
          )
        })}
      </div>

      {/* 종일 일정은 시간축에 자리가 없다. 위쪽 띠에 따로 놓는다. */}
      {hasAllDay ? (
        <div className={styles.allDayRow}>
          <span className={styles.axisHead}>종일</span>
          {dates.map((date) => (
            <div key={date} className={styles.allDayCell}>
              {allDayOn(entries, date).map((entry) =>
                entry.kind === 'event' ? (
                  <button
                    key={entryKey(entry)}
                    type="button"
                    className={styles.allDayChip}
                    style={
                      entry.color
                        ? { background: entry.color, color: contrastText(entry.color) }
                        : undefined
                    }
                    onClick={() => onSelect(entry)}
                  >
                    {entry.title}
                  </button>
                ) : (
                  <span
                    key={entryKey(entry)}
                    className={`${styles.allDayChip} ${styles.external}`}
                    style={entry.color ? { borderColor: entry.color } : undefined}
                    title={`${entry.sourceName}에서 구독`}
                  >
                    {label(entry)}
                  </span>
                ),
              )}
            </div>
          ))}
        </div>
      ) : null}

      <div className={styles.body}>
        <div className={styles.axis} aria-hidden="true">
          {marks.map((minute) => (
            <span
              key={minute}
              className={styles.mark}
              style={{ top: `${((minute - from) / (to - from)) * 100}%` }}
            >
              {formatMinutes(minute)}
            </span>
          ))}
        </div>

        {dates.map((date) => {
          const segments = daySegments(entries, date)
          return (
            <div
              key={date}
              className={styles.column}
              data-today={date === currentDay ? '' : undefined}
            >
              {marks.map((minute) => (
                <span
                  key={minute}
                  className={styles.gridLine}
                  style={{ top: `${((minute - from) / (to - from)) * 100}%` }}
                  aria-hidden="true"
                />
              ))}

              {/* 빈 곳을 누르면 그 시각으로 새 일정. 위치를 눌러 만드는 게 가장 빠르다. */}
              <button
                type="button"
                className={styles.canvas}
                aria-label={`${date} 새 일정`}
                onClick={(event) => {
                  const box = event.currentTarget.getBoundingClientRect()
                  const ratio = (event.clientY - box.top) / box.height
                  const minute = from + ratio * (to - from)
                  // 새로 만들 때만 30분으로 맞춘다. 저장된 시각을 건드리지는 않는다.
                  onPick(date, Math.max(0, Math.round(minute / 30) * 30))
                }}
              />

              {date === currentDay && nowLine >= from && nowLine <= to ? (
                <span
                  className={styles.now}
                  style={{ top: `${((nowLine - from) / (to - from)) * 100}%` }}
                  aria-hidden="true"
                />
              ) : null}

              {segments.map((segment) => {
                const entry = segment.instance
                const clipped = segment.continuesBefore || segment.continuesAfter

                // 구독해 온 일정은 누를 수 없다. 여기서 고칠 방법이 없는 것을
                // 버튼으로 만들면 눌러 보고 나서야 알게 된다.
                if (entry.kind === 'external') {
                  return (
                    <div
                      key={entryKey(entry)}
                      className={`${styles.event} ${styles.external}`}
                      data-clipped={clipped ? '' : undefined}
                      style={{
                        ...placement(segment, from, to),
                        ...(entry.color ? { borderColor: entry.color } : {}),
                      }}
                      title={`${entry.sourceName}에서 구독`}
                    >
                      <span className={styles.eventTime}>{instantTime(entry.startsAt)}</span>
                      <span className={styles.eventTitle}>{label(entry)}</span>
                    </div>
                  )
                }

                const cancelled = entry.change === 'CANCELLED'
                return (
                  <button
                    key={entryKey(entry)}
                    type="button"
                    className={styles.event}
                    data-cancelled={cancelled ? '' : undefined}
                    data-clipped={clipped ? '' : undefined}
                    style={{
                      ...placement(segment, from, to),
                      ...(entry.color && !cancelled
                        ? { background: entry.color, color: contrastText(entry.color) }
                        : {}),
                    }}
                    onClick={() => onSelect(entry)}
                  >
                    <span className={styles.eventTime}>{instantTime(entry.startsAt)}</span>
                    <span className={styles.eventTitle}>
                      {entry.title}
                      {cancelled ? ' (취소됨)' : ''}
                    </span>
                  </button>
                )
              })}
            </div>
          )
        })}
      </div>
    </div>
  )
}

/**
 * 구독 일정에 보일 글자.
 *
 * BADGE로 둔 구독은 제목 대신 출처만 보인다. 구글 캘린더에 적힌 사적인 제목이
 * 근무표를 같이 보는 사람 화면에 그대로 뜨는 것을 피하려는 설정이다.
 */
function label(entry: { displayMode: string; title: string | null; sourceName: string }): string {
  return entry.displayMode === 'INLINE' ? (entry.title ?? entry.sourceName) : entry.sourceName
}
