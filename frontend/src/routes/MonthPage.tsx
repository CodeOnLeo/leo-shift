import { useNavigate, useParams } from 'react-router-dom'
import { Placeholder } from '@/components/Placeholder'
import { MonthGrid } from '@/components/MonthGrid'
import { addMonths, today } from '@/lib/date'
import styles from './MonthPage.module.css'

export function MonthPage() {
  const params = useParams()
  const navigate = useNavigate()

  const now = today()
  const year = Number(params.year ?? now.slice(0, 4))
  const month = Number(params.month ?? now.slice(5, 7))

  // 이동 목표를 현재 URL에서 계산한다. 이전 구현은 비동기로 갱신되는 상태에서
  // 계산해서, 월 이동 버튼을 빠르게 누르면 클릭이 씹혔다.
  const step = (delta: number) => {
    const target = addMonths(`${year}-${String(month).padStart(2, '0')}-01`, delta)
    navigate(`/month/${target.slice(0, 4)}/${Number(target.slice(5, 7))}`)
  }

  return (
    <div>
      <header className={styles.header}>
        <button type="button" onClick={() => step(-1)} aria-label="이전 달">←</button>
        <h1 className={styles.title}>
          {year}년 {month}월
        </h1>
        <button type="button" onClick={() => step(1)} aria-label="다음 달">→</button>
      </header>

      <MonthGrid
        year={year}
        month={month}
        onSelect={(date) => navigate(`/day/${date}`)}
      />

      <Placeholder
        title=""
        description=""
        todo={[
          '근무 코드와 색 표시 (schedule_types)',
          '일정 · 휴가 · 외부 캘린더 겹쳐 보기',
          '이번 달 근무 요약',
          '겹쳐 볼 사람 선택',
        ]}
      />
    </div>
  )
}
