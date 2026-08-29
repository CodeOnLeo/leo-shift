import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchCalendars } from '@/api/calendar'
import { applyPattern, fetchPresets, fetchWorkRule } from '@/api/pattern'
import type { Preset, PresetScheduleType } from '@/api/types'
import { PatternPreview } from '@/components/PatternPreview'
import { SequenceEditor } from '@/components/SequenceEditor'
import { today } from '@/lib/date'
import { inferAnchor, rotate } from '@/lib/pattern'
import { useAsync } from '@/lib/useAsync'
import styles from './PatternSetupPage.module.css'

type Step = 'preset' | 'detail'

export function PatternSetupPage() {
  const navigate = useNavigate()

  const calendars = useAsync((signal) => fetchCalendars(signal), [])
  const presets = useAsync((signal) => fetchPresets(signal), [])

  const workCalendar =
    calendars.status === 'ready'
      ? calendars.data.find((c) => c.kind === 'WORK' && c.canEdit)
      : undefined

  const current = useAsync(
    async (signal) => (workCalendar ? fetchWorkRule(workCalendar.id, signal) : null),
    [workCalendar?.id],
  )

  const [step, setStep] = useState<Step>('preset')
  const [preset, setPreset] = useState<Preset | null>(null)
  const [team, setTeam] = useState<string | null>(null)
  const [sequence, setSequence] = useState<string[]>([])
  const [anchorDate, setAnchorDate] = useState<string>(today())
  const [anchorAnswer, setAnchorAnswer] = useState<string>(today())
  const [effectiveFrom, setEffectiveFrom] = useState<string>(today())
  const [repeatCount, setRepeatCount] = useState(1)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const typesByCode = useMemo(() => {
    const map = new Map<string, PresetScheduleType>()
    preset?.scheduleTypes.forEach((type) => map.set(type.code, type))
    return map
  }, [preset])

  function choosePreset(next: Preset) {
    setPreset(next)
    setStep('detail')
    setSaveError(null)

    const firstTeam = next.teams[0]?.label ?? null
    setTeam(firstTeam)
    const initial = firstTeam
      ? rotate(next.sequence, next.teams.find((t) => t.label === firstTeam)?.offset ?? 0)
      : [...next.sequence]
    setSequence(initial)

    // 기준일 질문이 있으면 오늘을 답으로 두고 역산해 초기값을 만든다
    if (next.anchorCode) {
      setAnchorDate(inferAnchor(initial, next.anchorCode, today()) ?? today())
    } else {
      setAnchorDate(today())
    }
    setAnchorAnswer(today())
  }

  function chooseTeam(label: string) {
    if (!preset) return
    setTeam(label)
    const offset = preset.teams.find((t) => t.label === label)?.offset ?? 0
    const next = rotate(preset.sequence, offset)
    setSequence(next)
    if (preset.anchorCode) {
      setAnchorDate(inferAnchor(next, preset.anchorCode, anchorAnswer) ?? anchorDate)
    }
  }

  function answerAnchor(date: string) {
    setAnchorAnswer(date)
    if (preset?.anchorCode) {
      setAnchorDate(inferAnchor(sequence, preset.anchorCode, date) ?? date)
    } else {
      setAnchorDate(date)
    }
  }

  async function save() {
    if (!workCalendar || sequence.length === 0) return
    setSaving(true)
    setSaveError(null)
    try {
      await applyPattern(workCalendar.id, {
        ...(preset ? { presetId: preset.id } : {}),
        ...(team ? { teamLabel: team } : {}),
        sequence,
        anchorDate,
        effectiveFrom,
      })
      navigate('/month')
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : '저장하지 못했습니다')
    } finally {
      setSaving(false)
    }
  }

  if (calendars.status === 'error' || presets.status === 'error') {
    return <p className={styles.error} role="alert">설정을 불러오지 못했습니다.</p>
  }
  if (presets.status !== 'ready' || calendars.status !== 'ready') {
    return <p className={styles.notice}>불러오는 중…</p>
  }
  if (!workCalendar) {
    return <p className={styles.notice}>근무 캘린더가 없습니다. 먼저 캘린더를 만들어 주세요.</p>
  }

  const regular = presets.data.filter((p) => p.category === 'REGULAR')
  const shift = presets.data.filter((p) => p.category === 'SHIFT')

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        {step === 'detail' ? (
          <button type="button" onClick={() => setStep('preset')} aria-label="프리셋 다시 고르기">←</button>
        ) : (
          <span />
        )}
        <h1 className={styles.title}>근무 패턴</h1>
        <span />
      </header>

      {step === 'preset' ? (
        <div className={styles.body}>
          {current.status === 'ready' && current.data ? (
            <p className={styles.current}>
              지금은 {current.data.cycleLength}일 주기 ({current.data.effectiveFrom}부터).
              새로 고르면 그 전까지의 근무는 그대로 남습니다.
            </p>
          ) : null}

          <PresetList title="일반 근무" presets={regular} onChoose={choosePreset} />
          <PresetList title="교대 근무" presets={shift} onChoose={choosePreset} />

          <p className={styles.hint}>
            프리셋은 출발점입니다. 고른 뒤 순서를 직접 고칠 수 있습니다.
          </p>
        </div>
      ) : (
        <div className={styles.body}>
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
                    aria-checked={team === option.label}
                    className={team === option.label ? `${styles.team} ${styles.teamOn}` : styles.team}
                    onClick={() => chooseTeam(option.label)}
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
              className={styles.date}
              type="date"
              value={anchorAnswer}
              onChange={(e) => answerAnchor(e.target.value)}
            />
            <p className={styles.hint}>
              계산된 기준일: {anchorDate}
              {preset?.anchorWeekday ? ' (요일에 맞춰 조정됩니다)' : ''}
            </p>
          </section>

          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>미리보기</h3>
            <PatternPreview sequence={sequence} anchorDate={anchorDate} typesByCode={typesByCode} />
          </section>

          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>순서 고치기</h3>
            <SequenceEditor
              sequence={sequence}
              types={preset?.scheduleTypes ?? []}
              repeatCount={repeatCount}
              onRepeatCountChange={setRepeatCount}
              onChange={setSequence}
            />
          </section>

          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>언제부터 적용할까요?</h3>
            <input
              className={styles.date}
              type="date"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
            />
            <p className={styles.hint}>이 날 전까지의 근무는 지금 패턴 그대로 남습니다.</p>
          </section>

          {saveError ? <p className={styles.error} role="alert">{saveError}</p> : null}

          <button
            type="button"
            className={styles.save}
            onClick={save}
            disabled={saving || sequence.length === 0}
          >
            {saving ? '저장 중…' : '이 패턴으로 저장'}
          </button>
        </div>
      )}
    </div>
  )
}

function PresetList({
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
      <h2 className={styles.sectionTitle}>{title}</h2>
      <ul className={styles.presetList}>
        {presets.map((preset) => (
          <li key={preset.id}>
            <button type="button" className={styles.presetCard} onClick={() => onChoose(preset)}>
              <span className={styles.presetTitle}>{preset.name}</span>
              <span className={styles.presetMeta}>
                {preset.cycleLength}일 주기
                {preset.teams.length > 0 ? ` · ${preset.teams.length}개 조` : ''}
              </span>
              {preset.tags.length > 0 ? (
                <span className={styles.presetTags}>{preset.tags.join(' · ')}</span>
              ) : null}
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
