import { useMemo } from 'react'
import type { PresetScheduleType } from '@/api/types'
import { PatternPreview } from '@/components/PatternPreview'
import { SequenceEditor } from '@/components/SequenceEditor'
import {
  answerAnchor,
  editSequence,
  selectTeam,
  type PatternDraft,
} from '@/lib/patternDraft'
import shared from '@/styles/shared.module.css'
import styles from './PatternDetailForm.module.css'

/** 프리셋을 고른 뒤의 세부 설정. 조 선택 → 기준일 → 미리보기 → 순서 → 적용일. */
export function PatternDetailForm({
  draft,
  onChange,
  onSave,
  saving,
  error,
}: {
  draft: PatternDraft
  onChange: (next: PatternDraft) => void
  onSave: () => void
  saving: boolean
  error: string | null
}) {
  const { preset } = draft

  const typesByCode = useMemo(() => {
    const map = new Map<string, PresetScheduleType>()
    preset?.scheduleTypes.forEach((type) => map.set(type.code, type))
    return map
  }, [preset])

  return (
    <>
      <h2 className={styles.presetName}>{preset?.name}</h2>
      {preset?.description ? <p className={styles.description}>{preset.description}</p> : null}

      {preset && preset.teams.length > 0 ? (
        <section className={styles.section}>
          <h3 className={styles.sectionTitle}>몇 조인가요?</h3>
          <div className={styles.teams} role="radiogroup" aria-label="교대조">
            {preset.teams.map((option) => (
              <button
                key={option.label}
                type="button"
                role="radio"
                aria-checked={draft.team === option.label}
                className={`${shared.pressable} ${styles.team} ${
                  draft.team === option.label ? styles.teamOn : ''
                }`}
                onClick={() => onChange(selectTeam(draft, option.label))}
              >
                {option.label}
              </button>
            ))}
          </div>
        </section>
      ) : null}

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>
          {preset?.anchorQuestion ?? '이 패턴을 시작한 날은 언제인가요?'}
        </h3>
        <input
          className={`${shared.field} ${styles.date}`}
          type="date"
          value={draft.anchorAnswer}
          onChange={(e) => onChange(answerAnchor(draft, e.target.value))}
        />
        <p className={shared.hint}>
          계산된 기준일: {draft.anchorDate}
          {preset?.anchorWeekday ? ' (요일에 맞춰 조정됩니다)' : ''}
        </p>
      </section>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>미리보기</h3>
        <PatternPreview
          sequence={draft.sequence}
          anchorDate={draft.anchorDate}
          typesByCode={typesByCode}
        />
      </section>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>순서 고치기</h3>
        <SequenceEditor
          sequence={draft.sequence}
          types={preset?.scheduleTypes ?? []}
          repeatCount={draft.repeatCount}
          onRepeatCountChange={(repeatCount) => onChange({ ...draft, repeatCount })}
          onChange={(sequence) => onChange(editSequence(draft, sequence))}
        />
      </section>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>언제부터 적용할까요?</h3>
        <input
          className={`${shared.field} ${styles.date}`}
          type="date"
          value={draft.effectiveFrom}
          onChange={(e) => onChange({ ...draft, effectiveFrom: e.target.value })}
        />
        <p className={shared.hint}>이 날 전까지의 근무는 지금 패턴 그대로 남습니다.</p>
      </section>

      {error ? <p className={shared.error} role="alert">{error}</p> : null}

      <button
        type="button"
        className={shared.primaryButton}
        onClick={onSave}
        disabled={saving || draft.sequence.length === 0}
      >
        {saving ? '저장 중…' : '이 패턴으로 저장'}
      </button>
    </>
  )
}
