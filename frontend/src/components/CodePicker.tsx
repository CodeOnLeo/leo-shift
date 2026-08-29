import type { ScheduleType } from '@/api/types'
import { ScheduleCodeBadge } from '@/components/ui/ScheduleCodeBadge'
import shared from '@/styles/shared.module.css'
import styles from './CodePicker.module.css'

/** 근무 코드 고르기. 시간이 있는 타입은 시간 범위도 함께 보여준다. */
export function CodePicker({
  types,
  selected,
  onSelect,
  disabled,
}: {
  types: readonly ScheduleType[]
  selected: string | null
  onSelect: (code: string | null) => void
  disabled?: boolean
}) {
  return (
    <div className={styles.list} role="radiogroup" aria-label="근무 종류">
      {types.map((type) => (
        <button
          key={type.code}
          type="button"
          role="radio"
          aria-checked={selected === type.code}
          disabled={disabled}
          className={`${shared.pressable} ${styles.option} ${
            selected === type.code ? styles.on : ''
          }`}
          onClick={() => onSelect(selected === type.code ? null : type.code)}
        >
          <ScheduleCodeBadge code={type.code} color={type.color} label={type.name} />
          <span className={styles.name}>{type.name}</span>
          {type.startTime && type.endTime ? (
            <span className={styles.time}>
              {type.startTime.slice(0, 5)}–{type.endTime.slice(0, 5)}
              {type.crossesMidnight ? <span className={styles.overnight}> +1일</span> : null}
            </span>
          ) : null}
        </button>
      ))}
    </div>
  )
}
