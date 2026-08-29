import type { ReactNode } from 'react'
import styles from './PageHeader.module.css'

/**
 * 화면 상단 머리글. `← 제목 →` 형태로 좌우에 동작을 둔다.
 *
 * 월 · 주 · 일 · 그룹 타임라인이 모두 같은 모양이라 한 곳에 둔다.
 */
export function PageHeader({
  title,
  left,
  right,
}: {
  title: string
  left?: ReactNode
  right?: ReactNode
}) {
  return (
    <header className={styles.header}>
      <span className={styles.slot}>{left}</span>
      <h1 className={styles.title}>{title}</h1>
      <span className={styles.slot}>{right}</span>
    </header>
  )
}
