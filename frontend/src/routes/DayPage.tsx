import { useParams } from 'react-router-dom'
import { Placeholder } from '@/components/Placeholder'
import { formatKoreanDate, today } from '@/lib/date'

export function DayPage() {
  const { date } = useParams()
  const target = date ?? today()

  return (
    <Placeholder
      title={formatKoreanDate(target)}
      description="그날의 근무, 일정, 휴가, 메모"
      todo={[
        '근무 코드와 시간 (규칙 → 휴가 → 예외 순으로 해석된 결과)',
        '이 근무가 어디서 왔는지 표시 (source) — 눌러서 해당 규칙·휴가 편집',
        '시간 있는 일정 목록',
        '메모',
        '겹쳐 보는 사람들의 그날 근무',
      ]}
    />
  )
}
