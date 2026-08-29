import type { PresetScheduleType } from '@/api/types'
import styles from './SequenceEditor.module.css'

/**
 * 근무 순서 편집기.
 *
 * 이전 구현은 추가만 됐다. 되돌리기는 마지막 하나 pop뿐이라 14일 주기의
 * 3번째 칸을 고치려면 12개를 되돌려야 했고, "주간 ×4" 같은 반복 입력이 없어
 * 25일 주기면 25번 눌러야 했다.
 *
 * 여기서는 위치마다 삭제·이동·삽입이 되고, 코드를 누를 때 반복 횟수를 고를 수 있다.
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
            className={styles.paletteButton}
            style={{ borderColor: type.color }}
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
          type="number"
          min={1}
          max={30}
          value={repeatCount}
          onChange={(e) => onRepeatCountChange(Math.max(1, Math.min(30, Number(e.target.value) || 1)))}
        />
        일
      </label>

      {sequence.length === 0 ? (
        <p className={styles.empty}>위에서 근무를 눌러 순서를 만드세요.</p>
      ) : (
        <ol className={styles.list}>
          {sequence.map((code, index) => {
            const type = typesByCode.get(code)
            return (
              <li key={`${code}-${index}`} className={styles.item}>
                <span className={styles.index}>{index + 1}</span>
                <span
                  className={styles.badge}
                  style={{ background: type?.color ?? 'var(--border-strong)' }}
                >
                  {code}
                </span>
                <span className={styles.name}>{type?.name ?? code}</span>

                <span className={styles.actions}>
                  <button type="button" onClick={() => moveBy(index, -1)}
                          disabled={index === 0} aria-label={`${index + 1}번째를 앞으로`}>↑</button>
                  <button type="button" onClick={() => moveBy(index, 1)}
                          disabled={index === sequence.length - 1} aria-label={`${index + 1}번째를 뒤로`}>↓</button>
                  <button type="button" onClick={() => duplicateAt(index)}
                          aria-label={`${index + 1}번째 뒤에 하루 추가`}>+</button>
                  <button type="button" onClick={() => removeAt(index)}
                          aria-label={`${index + 1}번째 삭제`}>×</button>
                </span>
              </li>
            )
          })}
        </ol>
      )}

      {sequence.length > 0 ? (
        <button type="button" className={styles.clear} onClick={() => onChange([])}>
          전체 지우기
        </button>
      ) : null}
    </div>
  )
}
