import type { ResolvedDay, ScheduleType } from '@/api/types'
import { formatKoreanDate, isoWeekday, today, weekdayHeaders } from '@/lib/date'
import styles from './MonthGrid.module.css'

/**
 * 월 달력 격자.
 *
 * 월요일 시작으로 통일한다. 이전 구현은 달력은 일요일 시작, 요일 설정 화면은
 * 월요일 시작이라 두 화면의 요일 순서가 서로 달랐다.
 *
 * 날짜 칸은 button이다. div에 클릭 핸들러만 달면 키보드로 날짜를 고를 수 없다.
 */
export function MonthGrid({
  dates,
  month,
  byDate,
  typesByCode,
  onSelect,
}: {
  dates: readonly string[]
  month: number
  byDate: ReadonlyMap<string, ResolvedDay>
  typesByCode: ReadonlyMap<string, ScheduleType>
  onSelect: (date: string) => void
}) {
  const currentDay = today()

  return (
    <div>
      <div className={styles.headers} aria-hidden="true">
        {weekdayHeaders().map((label, index) => (
          <div
            key={label}
            className={styles.headerCell}
            data-weekend={index === 5 ? 'sat' : index === 6 ? 'sun' : undefined}
          >
            {label}
          </div>
        ))}
      </div>

      <div className={styles.grid} role="grid" aria-label="월 달력">
        {dates.map((date) => {
          const inMonth = Number(date.slice(5, 7)) === month
          const weekday = isoWeekday(date)
          const day = byDate.get(date)
          const type = day?.code ? typesByCode.get(day.code) : undefined

          // 스크린리더에는 "8월 10일 (월), 주간" 처럼 읽힌다.
          const label = type
            ? `${formatKoreanDate(date)}, ${type.name}`
            : formatKoreanDate(date)

          return (
            <button
              key={date}
              type="button"
              role="gridcell"
              className={styles.cell}
              data-outside={inMonth ? undefined : ''}
              data-today={date === currentDay ? '' : undefined}
              data-weekend={weekday === 6 ? 'sat' : weekday === 7 ? 'sun' : undefined}
              onClick={() => onSelect(date)}
              aria-label={label}
              aria-current={date === currentDay ? 'date' : undefined}
            >
              <span className={styles.day}>{Number(date.slice(8, 10))}</span>

              {type ? (
                <span
                  className={styles.code}
                  style={{ background: type.color, color: contrastText(type.color) }}
                  /* 색만으로 구분하지 않는다. 코드 글자가 항상 함께 보인다. */
                >
                  {type.code}
                </span>
              ) : null}

              {day?.note ? <span className={styles.noteDot} aria-hidden="true" /> : null}
            </button>
          )
        })}
      </div>
    </div>
  )
}

/** 배경색 위에서 읽히는 글자색. 대비를 계산해서 고른다. */
function contrastText(hex: string): string {
  const value = hex.replace('#', '')
  if (value.length !== 6) return '#ffffff'
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(value.slice(i, i + 2), 16) / 255)
  const channel = (c: number) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4)
  const luminance = 0.2126 * channel(r!) + 0.7152 * channel(g!) + 0.0722 * channel(b!)
  // 흰 글자와 검은 글자 중 대비가 큰 쪽
  return (1.05 / (luminance + 0.05)) >= ((luminance + 0.05) / 0.05) ? '#ffffff' : '#0f172a'
}
