import shared from '@/styles/shared.module.css'
import styles from './ColorPicker.module.css'

/**
 * 색 고르기.
 *
 * 견본을 먼저 주는 이유는 사용자가 배경과 구분이 안 되는 색을 고르는 것을
 * 줄이기 위해서다. 임의의 색도 고를 수 있게 두되, 글자색은 대비를 계산해서
 * 자동으로 정해지므로 어떤 색을 골라도 코드가 읽힌다.
 */
const SWATCHES = [
  '#2563EB', '#0EA5E9', '#14B8A6', '#22C55E',
  '#F97316', '#EF4444', '#EC4899', '#7C3AED',
  '#64748B', '#94A3B8',
] as const

export function ColorPicker({
  value,
  onChange,
  disabled,
}: {
  value: string
  onChange: (color: string) => void
  disabled?: boolean
}) {
  return (
    <div className={styles.root}>
      <div className={styles.swatches} role="radiogroup" aria-label="색">
        {SWATCHES.map((color) => (
          <button
            key={color}
            type="button"
            role="radio"
            aria-checked={value.toUpperCase() === color}
            aria-label={color}
            disabled={disabled}
            className={styles.swatch}
            data-on={value.toUpperCase() === color ? '' : undefined}
            style={{ background: color }}
            onClick={() => onChange(color)}
          />
        ))}
      </div>
      <label className={styles.custom}>
        직접 고르기
        <input
          className={shared.field}
          type="color"
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value.toUpperCase())}
        />
      </label>
    </div>
  )
}
