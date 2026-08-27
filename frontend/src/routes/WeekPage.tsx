import { useParams } from 'react-router-dom'
import { Placeholder } from '@/components/Placeholder'
import { today, weekOf, formatKoreanDate } from '@/lib/date'

export function WeekPage() {
  const { date } = useParams()
  const week = weekOf(date ?? today())

  return (
    <Placeholder
      title="주간 시간표"
      description={`${formatKoreanDate(week[0]!)} ~ ${formatKoreanDate(week[6]!)}`}
      todo={[
        '시간축 분 단위 절대 배치 — 20:30~21:30 같은 시각을 격자에 맞추지 않는다',
        '겹치는 일정 나란히 놓기',
        '근무 시간대를 배경으로 표시',
        '빈 곳을 눌러 일정 만들기',
        '드래그로 시간 조정',
      ]}
    />
  )
}
