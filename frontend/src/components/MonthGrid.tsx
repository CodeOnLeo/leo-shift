import { formatKoreanDate, isoWeekday, monthGrid, today, weekdayHeaders } from '@/lib/date'
import styles from './MonthGrid.module.css'

/**
 * 월 달력 격자.
 *
 * 월요일 시작으로 통일한다. 이전 구현은 달력은 일요일 시작, 요일 설정 화면은
 * 월요일 시작이라 두 화면의 요일 순서가 서로 달랐다.
 *
 * 날짜 칸은 버튼이다. 이전 구현은 div에 클릭 핸들러만 달아서 키보드로
 * 날짜를 고를 수 없었다.
 */
export function MonthGrid({
  year,
  month,
  onSelect,
}: {
  year: number
  month: number
  onSelect: (date: string) => void
}) {
  const dates = monthGrid(year, month)
  const currentDay = today()

  return (
    <div>
      <div className={styles.headers} aria-hidden="true">
        {weekdayHeaders().map((label, index) => (
          <div
            key={label}
            className={styles.headerCell}
            data-weekend={index >= 5 ? (index === 5 ? 'sat' : 'sun') : undefined}
          >
            {label}
          </div>
        ))}
      </div>

      <div className={styles.grid} role="grid" aria-label={`${year}년 ${month}월`}>
        {dates.map((date) => {
          const inMonth = Number(date.slice(5, 7)) === month
          const weekday = isoWeekday(date)
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
              aria-label={formatKoreanDate(date)}
              aria-current={date === currentDay ? 'date' : undefined}
            >
              <span className={styles.day}>{Number(date.slice(8, 10))}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
