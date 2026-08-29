import type { ScheduleType } from '@/api/types'
import styles from './ScheduleLegend.module.css'

/**
 * 범례와 이번 달 요약.
 *
 * 하드코딩하지 않는다. 캘린더마다 코드가 다르므로 서버가 내려준 목록으로 그린다.
 * 이전 구현은 D/A/N/O가 HTML에 박혀 있었다.
 */
export function ScheduleLegend({
  types,
  summary,
}: {
  types: readonly ScheduleType[]
  summary: Readonly<Record<string, number>>
}) {
  // 한 번도 쓰이지 않은 코드는 숨긴다. 연차·반차까지 늘 보일 필요는 없다.
  const shown = types.filter((type) => (summary[type.code] ?? 0) > 0)
  if (shown.length === 0) return null

  return (
    <ul className={styles.list}>
      {shown.map((type) => (
        <li key={type.code} className={styles.item}>
          <span className={styles.swatch} style={{ background: type.color }} aria-hidden="true" />
          <span className={styles.name}>{type.name}</span>
          <span className={styles.count}>{summary[type.code]}일</span>
        </li>
      ))}
    </ul>
  )
}
