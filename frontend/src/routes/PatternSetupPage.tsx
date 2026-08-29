import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchCalendars } from '@/api/calendar'
import { applyPattern, fetchPresets, fetchWorkRule } from '@/api/pattern'
import type { Preset } from '@/api/types'
import { PatternDetailForm } from '@/components/PatternDetailForm'
import { PresetList } from '@/components/PresetList'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { emptyDraft, selectPreset, type PatternDraft } from '@/lib/patternDraft'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './PatternSetupPage.module.css'

export function PatternSetupPage() {
  const navigate = useNavigate()

  const calendars = useAsync((signal) => fetchCalendars(signal), [])
  const presets = useAsync((signal) => fetchPresets(signal), [])

  const workCalendar =
    calendars.status === 'ready'
      ? calendars.data.find((calendar) => calendar.kind === 'WORK' && calendar.canEdit)
      : undefined

  const current = useAsync(
    async (signal) => (workCalendar ? fetchWorkRule(workCalendar.id, signal) : null),
    [workCalendar?.id],
  )

  const [draft, setDraft] = useState<PatternDraft>(emptyDraft)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  function choose(preset: Preset) {
    setSaveError(null)
    setDraft(selectPreset(preset))
  }

  async function save() {
    if (!workCalendar || draft.sequence.length === 0) return
    setSaving(true)
    setSaveError(null)
    try {
      await applyPattern(workCalendar.id, {
        ...(draft.preset ? { presetId: draft.preset.id } : {}),
        ...(draft.team ? { teamLabel: draft.team } : {}),
        sequence: draft.sequence,
        anchorDate: draft.anchorDate,
        effectiveFrom: draft.effectiveFrom,
      })
      navigate('/month')
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : '저장하지 못했습니다')
    } finally {
      setSaving(false)
    }
  }

  if (calendars.status === 'error' || presets.status === 'error') {
    return <p className={shared.error} role="alert">설정을 불러오지 못했습니다.</p>
  }
  if (presets.status !== 'ready' || calendars.status !== 'ready') {
    return <p className={shared.notice}>불러오는 중…</p>
  }
  if (!workCalendar) {
    return <p className={shared.notice}>근무 캘린더가 없습니다. 먼저 캘린더를 만들어 주세요.</p>
  }

  return (
    <div className={styles.page}>
      <PageHeader
        title="근무 패턴"
        left={
          draft.preset ? (
            <IconButton label="프리셋 다시 고르기" onClick={() => setDraft(emptyDraft())}>←</IconButton>
          ) : null
        }
      />

      <div className={styles.body}>
        {draft.preset ? (
          <PatternDetailForm
            draft={draft}
            onChange={setDraft}
            onSave={save}
            saving={saving}
            error={saveError}
          />
        ) : (
          <>
            {current.status === 'ready' && current.data ? (
              <p className={styles.current}>
                지금은 {current.data.cycleLength}일 주기 ({current.data.effectiveFrom}부터).
                새로 고르면 그 전까지의 근무는 그대로 남습니다.
              </p>
            ) : null}

            <PresetList
              title="일반 근무"
              presets={presets.data.filter((preset) => preset.category === 'REGULAR')}
              onChoose={choose}
            />
            <PresetList
              title="교대 근무"
              presets={presets.data.filter((preset) => preset.category === 'SHIFT')}
              onChoose={choose}
            />

            <p className={shared.hint}>
              프리셋은 출발점입니다. 고른 뒤 순서를 직접 고칠 수 있습니다.
            </p>
          </>
        )}
      </div>
    </div>
  )
}
