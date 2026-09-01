import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchCalendars } from '@/api/calendar'
import {
  createScheduleType,
  deleteScheduleType,
  fetchScheduleTypes,
  updateScheduleType,
  type SaveScheduleTypeBody,
} from '@/api/scheduleType'
import type { ScheduleType } from '@/api/types'
import { ScheduleTypeForm } from '@/components/ScheduleTypeForm'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { ScheduleCodeBadge } from '@/components/ui/ScheduleCodeBadge'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './ScheduleTypesPage.module.css'

const CATEGORY_LABEL: Record<ScheduleType['category'], string> = {
  WORK: '근무',
  OFF: '휴무',
  LEAVE: '휴가',
}

/** null이면 닫힘, 'new'면 추가, 문자열이면 그 코드를 편집 중 */
type Editing = null | 'new' | string

export function ScheduleTypesPage() {
  const navigate = useNavigate()

  const calendars = useAsync((signal) => fetchCalendars(signal), [])
  const calendar =
    calendars.status === 'ready'
      ? calendars.data.find((item) => item.kind === 'WORK' && item.canEdit)
      : undefined

  const [reloadKey, setReloadKey] = useState(0)
  const loaded = useAsync(
    async (signal) => (calendar ? fetchScheduleTypes(calendar.id, signal) : []),
    [calendar?.id, reloadKey],
  )

  const [editing, setEditing] = useState<Editing>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      setEditing(null)
      setReloadKey((key) => key + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  function save(code: string | null, body: SaveScheduleTypeBody) {
    if (!calendar) return
    void run(() =>
      code === null
        ? createScheduleType(calendar.id, body)
        : updateScheduleType(calendar.id, code, body),
    )
  }

  function remove(type: ScheduleType) {
    if (!calendar) return
    if (!window.confirm(`${type.name}(${type.code}) 코드를 지울까요?`)) return
    void run(() => deleteScheduleType(calendar.id, type.code))
  }

  if (calendars.status === 'error' || loaded.status === 'error') {
    return <p className={shared.error} role="alert">불러오지 못했습니다.</p>
  }
  if (calendars.status !== 'ready' || loaded.status !== 'ready') {
    return <p className={shared.notice}>불러오는 중…</p>
  }
  if (!calendar) {
    return <p className={shared.notice}>근무 캘린더가 없습니다.</p>
  }

  const types = loaded.data

  return (
    <div className={styles.page}>
      <PageHeader
        title="근무 코드"
        left={<IconButton label="설정으로" onClick={() => navigate('/settings')}>←</IconButton>}
      />

      <div className={styles.body}>
        <p className={shared.hint}>
          달력에 표시할 근무 종류입니다. 이름과 색은 언제든 바꿀 수 있습니다.
        </p>

        <ul className={styles.list}>
          {types.map((type) => (
            <li key={type.code} className={styles.item}>
              {editing === type.code ? (
                <ScheduleTypeForm
                  type={type}
                  busy={busy}
                  error={error}
                  onSubmit={(body) => save(type.code, body)}
                  onCancel={() => {
                    setEditing(null)
                    setError(null)
                  }}
                />
              ) : (
                <div className={styles.row}>
                  <ScheduleCodeBadge code={type.code} color={type.color} label={type.name} />
                  <span className={styles.name}>{type.name}</span>
                  <span className={styles.meta}>
                    {CATEGORY_LABEL[type.category]}
                    {type.halfDay ? ' · 반차' : ''}
                    {type.startTime && type.endTime
                      ? ` · ${type.startTime.slice(0, 5)}–${type.endTime.slice(0, 5)}${
                          type.crossesMidnight ? ' +1일' : ''
                        }`
                      : ''}
                  </span>
                  <span className={styles.actions}>
                    <IconButton
                      label={`${type.name} 편집`}
                      disabled={busy}
                      onClick={() => {
                        setEditing(type.code)
                        setError(null)
                      }}
                    >
                      ✎
                    </IconButton>
                    <IconButton
                      label={`${type.name} 삭제`}
                      disabled={busy}
                      onClick={() => remove(type)}
                    >
                      ×
                    </IconButton>
                  </span>
                </div>
              )}
            </li>
          ))}
        </ul>

        {editing === 'new' ? (
          <ScheduleTypeForm
            type={null}
            busy={busy}
            error={error}
            onSubmit={(body) => save(null, body)}
            onCancel={() => {
              setEditing(null)
              setError(null)
            }}
          />
        ) : (
          <button
            type="button"
            className={`${shared.pressable} ${styles.add}`}
            disabled={busy}
            onClick={() => {
              setEditing('new')
              setError(null)
            }}
          >
            + 근무 코드 추가
          </button>
        )}

        {/* 목록에서 삭제가 실패한 경우. 폼 안에서는 폼이 직접 보여준다. */}
        {error && editing === null ? (
          <p className={shared.error} role="alert">{error}</p>
        ) : null}
      </div>
    </div>
  )
}
