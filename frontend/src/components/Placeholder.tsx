import type { ReactNode } from 'react'
import styles from './Placeholder.module.css'

/**
 * 아직 만들지 않은 화면. 골격 단계에서 무엇이 들어올 자리인지 표시한다.
 * 백엔드 API가 생기면 하나씩 실제 화면으로 바꾼다.
 */
export function Placeholder({
  title,
  description,
  todo,
  children,
}: {
  title: string
  description: string
  todo: readonly string[]
  children?: ReactNode
}) {
  return (
    <section className={styles.root}>
      <h1 className={styles.title}>{title}</h1>
      <p className={styles.description}>{description}</p>
      {children}
      <h2 className={styles.todoTitle}>여기 들어올 것</h2>
      <ul className={styles.todo}>
        {todo.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  )
}
