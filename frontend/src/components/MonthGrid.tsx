import type { ResolvedDay, ScheduleType } from '@/api/types'
import { CalendarGrid } from '@/components/ui/CalendarGrid'
import { ScheduleCodeBadge } from '@/components/ui/ScheduleCodeBadge'
import { formatKoreanDate } from '@/lib/date'
import styles from './MonthGrid.module.css'

/** 월 달력. 격자는 CalendarGrid가 그리고, 여기는 칸 안에 무엇을 넣을지만 정한다. */
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
  const typeOf = (date: string) => {
    const code = byDate.get(date)?.code
    return code ? typesByCode.get(code) : undefined
  }

  return (
    <CalendarGrid
      dates={dates}
      month={month}
      label="월 달력"
      onSelect={onSelect}
      // 스크린리더에는 "8월 10일 (월), 주간" 처럼 읽힌다
      dayLabel={(date) => {
        const type = typeOf(date)
        return type ? `${formatKoreanDate(date)}, ${type.name}` : formatKoreanDate(date)
      }}
      renderDay={(date) => {
        const day = byDate.get(date)
        const type = typeOf(date)
        return (
          <>
            {/* 색만으로 구분하지 않는다. 코드 글자가 항상 함께 보인다. */}
            {type ? <ScheduleCodeBadge code={type.code} color={type.color} label={type.name} /> : null}
            {day?.note ? <span className={styles.noteDot} aria-hidden="true" /> : null}
          </>
        )
      }}
    />
  )
}
