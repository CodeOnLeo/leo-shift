import { useState, type FormEvent } from 'react'
import type { EventDetail, EventInstance, MyCalendar, RecurrenceFreq } from '@/api/types'
import type { SaveEventBody, SaveOccurrenceBody } from '@/api/event'
import {
  browserZone,
  instantDate,
  instantTime,
  toInstant,
} from '@/lib/datetime'
import {
  NO_REPEAT,
  describeRecurrence,
  parseRrule,
  toRrule,
  weekdayOptions,
  type RecurrenceDraft,
} from '@/lib/recurrence'
import { isoWeekday } from '@/lib/date'
import shared from '@/styles/shared.module.css'
import styles from './EventForm.module.css'

const FREQS: readonly { value: RecurrenceFreq | 'NONE'; label: string }[] = [
  { value: 'NONE', label: '안 함' },
  { value: 'DAILY', label: '매일' },
  { value: 'WEEKLY', label: '매주' },
  { value: 'MONTHLY', label: '매월' },
  { value: 'YEARLY', label: '매년' },
]

export type SubmitPayload =
  | { scope: 'series'; calendarId: number; body: SaveEventBody }
  | { scope: 'occurrence'; body: SaveOccurrenceBody }

/**
 * 일정 편집.
 *
 * 반복 일정을 고칠 때 <b>"이 회차만"과 "전체"를 반드시 갈라 묻는다.</b> 매주
 * 수업에서 이번 주만 시간을 옮기는 것과 앞으로 계속 시간을 바꾸는 것은 전혀
 * 다른 일인데, 묻지 않고 전체를 고치면 지난 기록까지 조용히 바뀐다.
 */
export function EventForm({
  event,
  instance,
  calendars,
  defaultCalendarId,
  defaultDate,
  defaultMinutes,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  event: EventDetail | null
  instance: EventInstance | null
  calendars: readonly MyCalendar[]
  defaultCalendarId: number | undefined
  defaultDate: string
  defaultMinutes: number
  busy: boolean
  error: string | null
  onSubmit: (payload: SubmitPayload) => void
  onCancel: () => void
}) {
  const editingOccurrence = instance !== null && instance.recurring
  const start = instance?.startsAt ?? event?.startsAt ?? null
  const end = instance?.endsAt ?? event?.endsAt ?? null

  const [calendarId, setCalendarId] = useState<number | undefined>(
    event?.calendarId ?? defaultCalendarId,
  )
  const [title, setTitle] = useState(event?.title ?? instance?.title ?? '')
  const [location, setLocation] = useState(event?.location ?? '')
  const [description, setDescription] = useState(event?.description ?? '')
  const [allDay, setAllDay] = useState(event?.allDay ?? false)

  const [date, setDate] = useState(start ? instantDate(start) : defaultDate)
  const [startTime, setStartTime] = useState(
    start ? instantTime(start) : minutesToTime(defaultMinutes),
  )
  const [endTime, setEndTime] = useState(
    end ? instantTime(end) : minutesToTime(defaultMinutes + 60),
  )

  const [recurrence, setRecurrence] = useState<RecurrenceDraft>(() =>
    event ? parseRrule(event.rrule) : NO_REPEAT,
  )
  const [recurrenceEnd, setRecurrenceEnd] = useState(
    event?.recurrenceEnd ? instantDate(event.recurrenceEnd) : '',
  )

  // 반복 회차를 열었으면 "이 회차만"이 기본이다. 대개 이번 주만 옮기려는 것이고,
  // 전체를 바꾸는 건 되돌리기 어려우므로 일부러 고르게 한다.
  const [scope, setScope] = useState<'series' | 'occurrence'>(
    editingOccurrence ? 'occurrence' : 'series',
  )

  const submit = (formEvent: FormEvent) => {
    formEvent.preventDefault()

    const startsAt = toInstant(date, allDay ? '00:00' : startTime)
    const endsAt = toInstant(date, allDay ? '23:59' : endTime)

    if (scope === 'occurrence' && instance) {
      onSubmit({
        scope: 'occurrence',
        body: {
          originalStart: instance.occurrenceStart,
          startsAt,
          endsAt,
          title: title.trim() || null,
          note: description.trim() || null,
          cancelled: false,
        },
      })
      return
    }
    if (calendarId === undefined) return

    onSubmit({
      scope: 'series',
      calendarId,
      body: {
        title: title.trim(),
        description: description.trim() || null,
        location: location.trim() || null,
        startsAt,
        endsAt,
        allDay,
        timeZone: event?.timeZone ?? browserZone(),
        rrule: toRrule(recurrence),
        recurrenceEnd:
          recurrence.freq && recurrenceEnd ? toInstant(recurrenceEnd, '23:59') : null,
      },
    })
  }

  const toggleWeekday = (day: number) => {
    setRecurrence((draft) => ({
      ...draft,
      weekdays: draft.weekdays.includes(day)
        ? draft.weekdays.filter((value) => value !== day)
        : [...draft.weekdays, day].sort((a, b) => a - b),
    }))
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      {editingOccurrence ? (
        <fieldset className={styles.scope}>
          <legend className={styles.legend}>무엇을 바꿀까요</legend>
          <div className={styles.scopeOptions} role="radiogroup" aria-label="수정 범위">
            <label className={styles.scopeOption}>
              <input
                type="radio"
                name="scope"
                checked={scope === 'occurrence'}
                onChange={() => setScope('occurrence')}
              />
              이 회차만
            </label>
            <label className={styles.scopeOption}>
              <input
                type="radio"
                name="scope"
                checked={scope === 'series'}
                onChange={() => setScope('series')}
              />
              반복 전체
            </label>
          </div>
          {scope === 'series' ? (
            <p className={shared.hint}>
              전체를 바꾸면 지금까지 따로 손댔던 회차는 규칙대로 되돌아갑니다.
            </p>
          ) : null}
        </fieldset>
      ) : null}

      <label className={styles.label}>
        제목
        <input
          className={shared.field}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="김과외 수업"
          maxLength={200}
          required
          autoFocus
        />
      </label>

      {scope === 'series' && !event && calendars.length > 1 ? (
        <label className={styles.label}>
          캘린더
          <select
            className={shared.field}
            value={calendarId ?? ''}
            onChange={(e) => setCalendarId(Number(e.target.value))}
          >
            {calendars.map((calendar) => (
              <option key={calendar.id} value={calendar.id}>
                {calendar.name}
              </option>
            ))}
          </select>
        </label>
      ) : null}

      <label className={styles.label}>
        날짜
        <input
          className={shared.field}
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          required
        />
      </label>

      {scope === 'series' ? (
        <label className={styles.checkbox}>
          <input type="checkbox" checked={allDay} onChange={(e) => setAllDay(e.target.checked)} />
          종일
        </label>
      ) : null}

      {allDay ? null : (
        <div className={styles.times}>
          <label className={styles.label}>
            시작
            <input
              className={shared.field}
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
              required
            />
          </label>
          <label className={styles.label}>
            종료
            <input
              className={shared.field}
              type="time"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
              required
            />
          </label>
        </div>
      )}

      {scope === 'series' ? (
        <>
          <label className={styles.label}>
            장소 (선택)
            <input
              className={shared.field}
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              maxLength={200}
            />
          </label>

          <fieldset className={styles.repeat}>
            <legend className={styles.legend}>반복</legend>
            <select
              className={shared.field}
              value={recurrence.freq ?? 'NONE'}
              aria-label="반복 주기"
              onChange={(e) => {
                const value = e.target.value
                setRecurrence(
                  value === 'NONE'
                    ? NO_REPEAT
                    : {
                        freq: value as RecurrenceFreq,
                        interval: 1,
                        // 매주로 바꾸면 고른 날짜의 요일을 기본으로 잡는다
                        weekdays: value === 'WEEKLY' ? [isoWeekday(date)] : [],
                      },
                )
              }}
            >
              {FREQS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>

            {recurrence.freq ? (
              <>
                <label className={styles.interval}>
                  간격
                  <input
                    className={shared.field}
                    type="number"
                    min={1}
                    max={52}
                    value={recurrence.interval}
                    onChange={(e) =>
                      setRecurrence((draft) => ({
                        ...draft,
                        interval: Math.max(1, Number(e.target.value) || 1),
                      }))
                    }
                  />
                </label>

                {recurrence.freq === 'WEEKLY' ? (
                  <div className={styles.weekdays} role="group" aria-label="반복 요일">
                    {weekdayOptions().map((option) => (
                      <button
                        key={option.value}
                        type="button"
                        className={styles.weekday}
                        data-on={recurrence.weekdays.includes(option.value) ? '' : undefined}
                        aria-pressed={recurrence.weekdays.includes(option.value)}
                        onClick={() => toggleWeekday(option.value)}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                ) : null}

                <label className={styles.label}>
                  반복 종료 (비우면 계속)
                  <input
                    className={shared.field}
                    type="date"
                    value={recurrenceEnd}
                    min={date}
                    onChange={(e) => setRecurrenceEnd(e.target.value)}
                  />
                </label>

                <p className={shared.hint}>{describeRecurrence(recurrence)}</p>
              </>
            ) : null}
          </fieldset>
        </>
      ) : null}

      <label className={styles.label}>
        메모 (선택)
        <textarea
          className={styles.textarea}
          value={description}
          rows={2}
          maxLength={2000}
          onChange={(e) => setDescription(e.target.value)}
        />
      </label>

      {error ? (
        <p className={shared.error} role="alert">
          {error}
        </p>
      ) : null}

      <div className={styles.actions}>
        <button
          type="button"
          className={`${shared.pressable} ${styles.cancel}`}
          onClick={onCancel}
          disabled={busy}
        >
          취소
        </button>
        <button type="submit" className={shared.primaryButton} disabled={busy || !title.trim()}>
          저장
        </button>
      </div>
    </form>
  )
}

function minutesToTime(minutes: number): string {
  const clamped = Math.max(0, Math.min(minutes, 23 * 60 + 30))
  const hours = String(Math.floor(clamped / 60)).padStart(2, '0')
  return `${hours}:${String(clamped % 60).padStart(2, '0')}`
}
