import type { PresetScheduleType } from '@/api/types'
import { IconButton } from '@/components/ui/IconButton'
import { ScheduleCodeBadge } from '@/components/ui/ScheduleCodeBadge'
import shared from '@/styles/shared.module.css'
import styles from './SequenceEditor.module.css'

/**
 * 근무 순서 편집기.
 *
 * 이전 구현은 추가만 됐다. 되돌리기가 마지막 하나 pop뿐이라 14일 주기의 3번째를
 * 고치려면 12개를 되돌려야 했고, 반복 입력이 없어 25일 주기면 25번 눌러야 했다.
 *
 * 드래그 대신 버튼으로 이동시키는 것은 키보드로도 쓸 수 있게 하기 위해서다.
 */
export function SequenceEditor({
  sequence,
  types,
  repeatCount,
  onRepeatCountChange,
  onChange,
}: {
  sequence: readonly string[]
  types: readonly PresetScheduleType[]
  repeatCount: number
  onRepeatCountChange: (count: number) => void
  onChange: (next: string[]) => void
}) {
  const typesByCode = new Map(types.map((type) => [type.code, type]))

  const append = (code: string) => onChange([...sequence, ...Array<string>(repeatCount).fill(code)])
  const removeAt = (index: number) => onChange(sequence.filter((_, i) => i !== index))
  const duplicateAt = (index: number) =>
    onChange([...sequence.slice(0, index + 1), sequence[index]!, ...sequence.slice(index + 1)])

  const moveBy = (index: number, delta: number) => {
    const target = index + delta
    if (target < 0 || target >= sequence.length) return
    const next = [...sequence]
    const [moved] = next.splice(index, 1)
    next.splice(target, 0, moved!)
    onChange(next)
  }

  return (
    <div>
      <div className={styles.palette}>
        {types.map((type) => (
          <button
            key={type.code}
            type="button"
            className={`${shared.pressable} ${styles.paletteButton}`}
            style={{ borderLeftColor: type.color }}
            onClick={() => append(type.code)}
          >
            <span className={styles.dot} style={{ background: type.color }} aria-hidden="true" />
            {type.name}
            <span className={styles.paletteCode}>{type.code}</span>
          </button>
        ))}
      </div>

      <label className={styles.repeat}>
        한 번에 넣을 일수
        <input
          className={`${shared.field} ${styles.repeatInput}`}
          type="number"
          min={1}
          max={30}
          value={repeatCount}
          onChange={(e) =>
            onRepeatCountChange(Math.max(1, Math.min(30, Number(e.target.value) || 1)))
          }
        />
        일
      </label>

      {sequence.length === 0 ? (
        <p className={shared.hint}>위에서 근무를 눌러 순서를 만드세요.</p>
      ) : (
        <ol className={styles.list}>
          {sequence.map((code, index) => {
            const type = typesByCode.get(code)
            return (
              <li key={`${code}-${index}`} className={styles.item}>
                <span className={styles.index}>{index + 1}</span>
                <ScheduleCodeBadge code={code} color={type?.color} label={type?.name} />
                <span className={styles.name}>{type?.name ?? code}</span>

                <span className={styles.actions}>
                  <IconButton label={`${index + 1}번째를 앞으로`} disabled={index === 0}
                              onClick={() => moveBy(index, -1)}>↑</IconButton>
                  <IconButton label={`${index + 1}번째를 뒤로`} disabled={index === sequence.length - 1}
                              onClick={() => moveBy(index, 1)}>↓</IconButton>
                  <IconButton label={`${index + 1}번째 뒤에 하루 추가`}
                              onClick={() => duplicateAt(index)}>+</IconButton>
                  <IconButton label={`${index + 1}번째 삭제`}
                              onClick={() => removeAt(index)}>×</IconButton>
                </span>
              </li>
            )
          })}
        </ol>
      )}

      {sequence.length > 0 ? (
        <button type="button" className={`${shared.pressable} ${styles.clear}`} onClick={() => onChange([])}>
          전체 지우기
        </button>
      ) : null}
    </div>
  )
}
