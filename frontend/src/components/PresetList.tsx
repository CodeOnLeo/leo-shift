import type { Preset } from '@/api/types'
import shared from '@/styles/shared.module.css'
import styles from './PresetList.module.css'

export function PresetList({
  title,
  presets,
  onChoose,
}: {
  title: string
  presets: readonly Preset[]
  onChoose: (preset: Preset) => void
}) {
  if (presets.length === 0) return null

  return (
    <section className={styles.section}>
      <h2 className={styles.title}>{title}</h2>
      <ul className={styles.list}>
        {presets.map((preset) => (
          <li key={preset.id}>
            <button
              type="button"
              className={`${shared.pressable} ${styles.card}`}
              onClick={() => onChoose(preset)}
            >
              <span className={styles.name}>{preset.name}</span>
              <span className={styles.meta}>
                {preset.cycleLength}일 주기
                {preset.teams.length > 0 ? ` · ${preset.teams.length}개 조` : ''}
              </span>
              {preset.tags.length > 0 ? (
                <span className={styles.tags}>{preset.tags.join(' · ')}</span>
              ) : null}
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
