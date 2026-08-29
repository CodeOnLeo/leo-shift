import { contrastText } from '@/lib/color'
import styles from './ScheduleCodeBadge.module.css'

/**
 * 근무 코드 배지.
 *
 * 이전에는 달력·미리보기·편집기가 각자 다르게 그렸다. 달력만 대비를 계산하고
 * 나머지는 흰 글자에 그림자를 깔아서, 밝은 색 코드가 화면마다 다르게 읽혔다.
 */
export function ScheduleCodeBadge({
  code,
  color,
  size = 'md',
  label,
}: {
  code: string
  color: string | undefined
  size?: 'sm' | 'md'
  /** 배지 대신 읽히는 이름. 없으면 코드가 그대로 읽힌다. */
  label?: string | undefined
}) {
  const background = color ?? 'var(--border-strong)'
  return (
    <span
      className={styles.badge}
      data-size={size}
      style={color ? { background, color: contrastText(color) } : { background }}
      title={label ?? code}
    >
      {code}
    </span>
  )
}
