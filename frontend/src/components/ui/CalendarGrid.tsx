import type { ReactNode } from 'react'
import { isoWeekday, today, weekdayHeaders } from '@/lib/date'
import styles from './CalendarGrid.module.css'

/**
 * 7열 달력 격자.
 *
 * 월 달력과 패턴 미리보기가 각자 구현하던 것을 하나로 합쳤다. 주말 색, 이번 달
 * 밖 흐리게, 오늘 강조, 요일 머리글은 전부 여기가 책임진다. 칸 안에 무엇을
 * 넣을지만 {@link renderDay}로 받는다.
 *
 * 월요일 시작으로 통일한다. 이전 구현은 달력은 일요일, 설정 화면은 월요일이라
 * 두 화면의 요일 순서가 서로 달랐다.
 *
 * @param month       이 달에 속하지 않는 날짜를 흐리게 한다. 없으면 전부 진하게
 * @param onSelect    주면 각 칸이 button이 된다. 키보드로도 고를 수 있다
 * @param dayLabel    스크린리더가 읽을 문구. onSelect가 있으면 사실상 필수
 */
export function CalendarGrid({
  dates,
  month,
  size = 'md',
  label,
  onSelect,
  dayLabel,
  renderDay,
}: {
  dates: readonly string[]
  month?: number
  size?: 'sm' | 'md'
  label?: string
  onSelect?: (date: string) => void
  dayLabel?: (date: string) => string
  renderDay?: (date: string) => ReactNode
}) {
  const currentDay = today()

  return (
    <div className={styles.root} data-size={size}>
      <div className={styles.headers} aria-hidden="true">
        {weekdayHeaders().map((weekday, index) => (
          <span
            key={weekday}
            className={styles.headerCell}
            data-weekend={index === 5 ? 'sat' : index === 6 ? 'sun' : undefined}
          >
            {weekday}
          </span>
        ))}
      </div>

      <div className={styles.grid} role="grid" aria-label={label}>
        {dates.map((date) => {
          const weekday = isoWeekday(date)
          const attributes = {
            className: styles.cell,
            'data-outside':
              month !== undefined && Number(date.slice(5, 7)) !== month ? '' : undefined,
            'data-today': date === currentDay ? '' : undefined,
            'data-weekend': weekday === 6 ? 'sat' : weekday === 7 ? 'sun' : undefined,
          } as const

          const content = (
            <>
              <span className={styles.dayNumber}>{Number(date.slice(8, 10))}</span>
              {renderDay?.(date)}
            </>
          )

          return onSelect ? (
            <button
              key={date}
              type="button"
              role="gridcell"
              onClick={() => onSelect(date)}
              aria-label={dayLabel?.(date)}
              aria-current={date === currentDay ? 'date' : undefined}
              {...attributes}
            >
              {content}
            </button>
          ) : (
            <div key={date} role="gridcell" title={dayLabel?.(date)} {...attributes}>
              {content}
            </div>
          )
        })}
      </div>
    </div>
  )
}
