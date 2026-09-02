import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchCalendars, fetchSchedule } from '@/api/calendar'
import {
  createEvent,
  deleteEvent,
  fetchEvent,
  fetchEvents,
  restoreOccurrence,
  saveOccurrence,
  updateEvent,
} from '@/api/event'
import { fetchMyCalendars } from '@/api/myCalendar'
import type { EventInstance } from '@/api/types'
import { EventForm, type SubmitPayload } from '@/components/EventForm'
import { EventSheet } from '@/components/EventSheet'
import { WeekTimetable } from '@/components/WeekTimetable'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { addDays, today, weekOf } from '@/lib/date'
import { toInstant } from '@/lib/datetime'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './WeekPage.module.css'

/** 열려 있는 편집기. 새 일정이면 어디를 눌렀는지도 들고 있다. */
type Editing =
  | { kind: 'new'; date: string; minutes: number }
  | { kind: 'edit'; instance: EventInstance }
  | null

export function WeekPage() {
  const { date } = useParams()
  const navigate = useNavigate()

  const anchor = date ?? today()
  const week = useMemo(() => weekOf(anchor), [anchor])
  const from = toInstant(week[0]!, '00:00')
  const to = toInstant(addDays(week[6]!, 1), '00:00')

  const [reloadKey, setReloadKey] = useState(0)
  const [selected, setSelected] = useState<EventInstance | null>(null)
  const [editing, setEditing] = useState<Editing>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const events = useAsync(
    (signal) => fetchEvents({ from, to }, signal),
    [from, to, reloadKey],
  )

  // 편집할 때는 시리즈 원본을 따로 받는다. 회차 정보에는 반복 규칙이 없어서,
  // 그것만으로 폼을 채우면 "반복 전체"로 저장할 때 규칙이 비어 나간다.
  const editingEventId = editing?.kind === 'edit' ? editing.instance.eventId : null
  const source = useAsync(
    async (signal) => (editingEventId === null ? null : fetchEvent(editingEventId, signal)),
    [editingEventId],
  )
  const myCalendars = useAsync((signal) => fetchMyCalendars(signal), [reloadKey])

  // 근무 코드를 같이 보여준다. 교대근무자에게는 이게 없으면 한 주가 안 읽힌다.
  const calendars = useAsync((signal) => fetchCalendars(signal), [])
  const workCalendarId =
    calendars.status === 'ready'
      ? calendars.data.find((calendar) => calendar.mine && calendar.kind === 'WORK')?.id
      : undefined

  const schedule = useAsync(
    async (signal) =>
      workCalendarId === undefined
        ? null
        : fetchSchedule({ calendarId: workCalendarId, from: week[0]!, to: week[6]! }, signal),
    [workCalendarId, week[0], week[6]],
  )

  const codeByDate = useMemo(() => {
    const map = new Map<string, { code: string; color: string | null }>()
    if (schedule.status === 'ready' && schedule.data) {
      const colors = new Map(schedule.data.scheduleTypes.map((type) => [type.code, type.color]))
      for (const day of schedule.data.days) {
        if (day.code) map.set(day.date, { code: day.code, color: colors.get(day.code) ?? null })
      }
    }
    return map
  }, [schedule])

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      setEditing(null)
      setSelected(null)
      setReloadKey((key) => key + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  function submit(payload: SubmitPayload) {
    if (payload.scope === 'occurrence') {
      if (editingEventId === null) return
      void run(() => saveOccurrence(editingEventId, payload.body))
      return
    }
    void run(() =>
      editingEventId === null
        ? createEvent(payload.calendarId, payload.body)
        : updateEvent(editingEventId, payload.body),
    )
  }

  const step = (delta: number) => navigate(`/week/${addDays(anchor, delta * 7)}`)

  if (events.status === 'error') {
    return (
      <p className={shared.error} role="alert">
        {events.error.message}
      </p>
    )
  }

  const instances = events.status === 'ready' ? events.data.instances : []
  const editable = myCalendars.status === 'ready' ? myCalendars.data : []
  // 새 일정은 개인 캘린더로 간다.
  //
  // 기본 캘린더를 그대로 쓰면 안 된다. 근무 캘린더가 기본인 사람이 흔한데,
  // 거기에 "14:00 병원"이 쌓이면 "근무만"으로 공유한 직장 동료에게 그대로 나간다.
  // 개인 일정을 별도 캘린더에 두는 것이 이 설계의 공개 범위 전체를 떠받친다.
  const defaultCalendarId =
    editable.find((calendar) => calendar.isDefault && calendar.kind === 'GENERAL')?.id ??
    editable.find((calendar) => calendar.kind === 'GENERAL')?.id ??
    editable.find((calendar) => calendar.isDefault)?.id ??
    editable[0]?.id

  return (
    <div className={styles.page}>
      <PageHeader
        title={`${Number(week[0]!.slice(5, 7))}월 ${Number(week[0]!.slice(8, 10))}일 주`}
        left={<IconButton label="이전 주" onClick={() => step(-1)}>←</IconButton>}
        right={<IconButton label="다음 주" onClick={() => step(1)}>→</IconButton>}
      />

      <p className="visually-hidden" role="status">
        {events.status === 'loading' ? '일정을 불러오는 중' : ''}
      </p>

      <WeekTimetable
        dates={week}
        instances={instances}
        codeByDate={codeByDate}
        onSelect={(instance) => {
          setSelected(instance)
          setEditing(null)
          setError(null)
        }}
        onPick={(pickedDate, minutes) => {
          setSelected(null)
          setEditing({ kind: 'new', date: pickedDate, minutes })
          setError(null)
        }}
      />

      {/*
        열려 있을 때는 화면 아래에 붙는다. 시간표가 화면보다 길어서 흐름대로 두면
        아래쪽 일정을 눌렀을 때 편집기가 접힌 자리에 열리고, 스크롤로 맞추려 해도
        문서 끝이라 더 내려가지 않는다.
      */}
      <div className={styles.panel} data-open={selected || editing ? '' : undefined}>
        {editing && editingEventId !== null && source.status !== 'ready' ? (
          <p className={shared.notice}>불러오는 중…</p>
        ) : editing ? (
          <EventForm
            key={editingEventId ?? 'new'}
            event={source.status === 'ready' ? source.data : null}
            instance={editing.kind === 'edit' ? editing.instance : null}
            calendars={editable}
            defaultCalendarId={defaultCalendarId}
            defaultDate={editing.kind === 'new' ? editing.date : today()}
            defaultMinutes={editing.kind === 'new' ? editing.minutes : 9 * 60}
            busy={busy}
            error={error}
            onSubmit={submit}
            onCancel={() => {
              setEditing(null)
              setError(null)
            }}
          />
        ) : selected ? (
          <EventSheet
            instance={selected}
            busy={busy}
            onEdit={() => {
              setEditing({ kind: 'edit', instance: selected })
              setError(null)
            }}
            onCancelOccurrence={() =>
              void run(() =>
                saveOccurrence(selected.eventId, {
                  originalStart: selected.occurrenceStart,
                  startsAt: null,
                  endsAt: null,
                  title: null,
                  note: null,
                  cancelled: true,
                }),
              )
            }
            onRestoreOccurrence={() =>
              void run(() => restoreOccurrence(selected.eventId, selected.occurrenceStart))
            }
            onDelete={() => {
              const message = selected.recurring
                ? `"${selected.title}" 반복 전체를 지울까요?`
                : `"${selected.title}"을(를) 지울까요?`
              if (!window.confirm(message)) return
              void run(() => deleteEvent(selected.eventId))
            }}
            onClose={() => setSelected(null)}
          />
        ) : (
          <button
            type="button"
            className={`${shared.pressable} ${styles.add}`}
            onClick={() => setEditing({ kind: 'new', date: anchor, minutes: 9 * 60 })}
          >
            + 일정 추가
          </button>
        )}

        {error && !editing ? (
          <p className={shared.error} role="alert">
            {error}
          </p>
        ) : null}
      </div>
    </div>
  )
}
