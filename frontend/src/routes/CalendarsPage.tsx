import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  createCalendar,
  deleteCalendar,
  fetchMyCalendars,
  setDefaultCalendar,
  updateCalendar,
} from '@/api/myCalendar'
import type { MyCalendar } from '@/api/types'
import { ColorPicker } from '@/components/ColorPicker'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './CalendarsPage.module.css'

/**
 * 내 캘린더.
 *
 * 캘린더를 나누는 이유는 정리가 아니라 <b>공개 범위</b>다. 근무 캘린더만 직장에
 * 공유하면 개인 일정은 애초에 나가지 않는다. 그래서 개인 일정은 근무 캘린더가
 * 아니라 여기서 만든 캘린더에 쌓여야 한다.
 */
export function CalendarsPage() {
  const navigate = useNavigate()
  const [reloadKey, setReloadKey] = useState(0)
  const calendars = useAsync((signal) => fetchMyCalendars(signal), [reloadKey])

  const [editing, setEditing] = useState<number | 'new' | null>(null)
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

  if (calendars.status === 'error') {
    return (
      <p className={shared.error} role="alert">
        불러오지 못했습니다.
      </p>
    )
  }
  if (calendars.status !== 'ready') {
    return <p className={shared.notice}>불러오는 중…</p>
  }

  return (
    <div className={styles.page}>
      <PageHeader
        title="내 캘린더"
        left={<IconButton label="설정으로" onClick={() => navigate('/settings')}>←</IconButton>}
      />

      <div className={styles.body}>
        <p className={shared.hint}>
          개인 일정은 근무 캘린더가 아니라 여기서 만든 캘린더에 넣으세요. 직장에는
          근무 캘린더만 공유되므로 개인 일정은 애초에 나가지 않습니다.
        </p>

        <ul className={styles.list}>
          {calendars.data.map((calendar) => (
            <li key={calendar.id}>
              {editing === calendar.id ? (
                <CalendarForm
                  calendar={calendar}
                  busy={busy}
                  error={error}
                  onSubmit={(body) => void run(() => updateCalendar(calendar.id, body))}
                  onCancel={() => {
                    setEditing(null)
                    setError(null)
                  }}
                />
              ) : (
                <div className={styles.row}>
                  <span
                    className={styles.bar}
                    style={calendar.color ? { background: calendar.color } : undefined}
                    aria-hidden="true"
                  />
                  <span className={styles.text}>
                    <span className={styles.name}>
                      {calendar.name}
                      {calendar.isDefault ? <span className={styles.tag}>기본</span> : null}
                    </span>
                    <span className={styles.meta}>
                      {calendar.kind === 'WORK' ? '근무 캘린더' : '개인 일정'}
                      {calendar.description ? ` · ${calendar.description}` : ''}
                    </span>
                  </span>
                  <span className={styles.actions}>
                    {calendar.isDefault ? null : (
                      <IconButton
                        label={`${calendar.name}을(를) 기본으로`}
                        disabled={busy}
                        onClick={() => void run(() => setDefaultCalendar(calendar.id))}
                      >
                        ☆
                      </IconButton>
                    )}
                    <IconButton
                      label={`${calendar.name} 편집`}
                      disabled={busy}
                      onClick={() => {
                        setEditing(calendar.id)
                        setError(null)
                      }}
                    >
                      ✎
                    </IconButton>
                    <IconButton
                      label={`${calendar.name} 삭제`}
                      disabled={busy || !calendar.removable}
                      onClick={() => {
                        if (
                          !window.confirm(
                            `${calendar.name} 캘린더를 지울까요? 담긴 일정도 함께 보이지 않게 됩니다.`,
                          )
                        )
                          return
                        void run(() => deleteCalendar(calendar.id))
                      }}
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
          <CalendarForm
            calendar={null}
            busy={busy}
            error={error}
            onSubmit={(body) => void run(() => createCalendar(body))}
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
            + 캘린더 추가
          </button>
        )}

        <p className={shared.caption}>
          근무 캘린더는 하나만 둡니다. 새로 만드는 캘린더는 개인 일정용입니다.
        </p>

        {error && editing === null ? (
          <p className={shared.error} role="alert">
            {error}
          </p>
        ) : null}
      </div>
    </div>
  )
}

function CalendarForm({
  calendar,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  calendar: MyCalendar | null
  busy: boolean
  error: string | null
  onSubmit: (body: { name: string; description: string | null; color: string | null }) => void
  onCancel: () => void
}) {
  const [name, setName] = useState(calendar?.name ?? '')
  const [description, setDescription] = useState(calendar?.description ?? '')
  const [color, setColor] = useState(calendar?.color ?? '#7C3AED')

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSubmit({
      name: name.trim(),
      description: description.trim() || null,
      color,
    })
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      <label className={styles.label}>
        이름
        <input
          className={shared.field}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="개인 일정"
          maxLength={100}
          required
          autoFocus
        />
      </label>

      <label className={styles.label}>
        설명 (선택)
        <input
          className={shared.field}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={500}
        />
      </label>

      <div className={styles.label}>
        색
        <ColorPicker value={color} onChange={setColor} disabled={busy} />
      </div>

      {error ? (
        <p className={shared.error} role="alert">
          {error}
        </p>
      ) : null}

      <div className={styles.formActions}>
        <button
          type="button"
          className={`${shared.pressable} ${styles.cancel}`}
          onClick={onCancel}
          disabled={busy}
        >
          취소
        </button>
        <button type="submit" className={shared.primaryButton} disabled={busy || !name.trim()}>
          저장
        </button>
      </div>
    </form>
  )
}
