import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchCalendars } from '@/api/calendar'
import { fetchEvents } from '@/api/event'
import { fetchExternalEvents } from '@/api/external'
import { addLeave, clearDay, deleteLeave, fetchDay, saveDay } from '@/api/day'
import { ApiError } from '@/api/client'
import type { DayDetail } from '@/api/types'
import { CodePicker } from '@/components/CodePicker'
import { DaySourceCard } from '@/components/DaySourceCard'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { addDays, formatKoreanDate, today } from '@/lib/date'
import { formatTimeRange } from '@/lib/datetime'
import { toInstant } from '@/lib/datetime'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './DayPage.module.css'

export function DayPage() {
  const { date = today() } = useParams()
  const navigate = useNavigate()

  const calendars = useAsync((signal) => fetchCalendars(signal), [])

  // 이 화면이 다루는 것은 근무다. isDefault로 고르면 개인 일정 캘린더를 기본으로
  // 잡아둔 사람에게 엉뚱한 캘린더가 잡힌다.
  const calendarId =
    calendars.status === 'ready'
      ? calendars.data.find((calendar) => calendar.mine && calendar.kind === 'WORK')?.id
      : undefined

  // 일정은 캘린더를 가리지 않고 모은다. 그날 무엇이 있는지가 이 화면의 절반이다.
  const events = useAsync(
    (signal) =>
      fetchEvents({ from: toInstant(date, '00:00'), to: toInstant(addDays(date, 1), '00:00') }, signal),
    [date],
  )

  // 구독해 온 일정. 편집할 수 없으므로 우리 일정과 한 줄에 섞지 않고 따로 놓는다.
  const externals = useAsync(
    (signal) =>
      fetchExternalEvents(
        { from: toInstant(date, '00:00'), to: toInstant(addDays(date, 1), '00:00') },
        signal,
      ),
    [date],
  )

  const loaded = useAsync(
    async (signal) => (calendarId === undefined ? null : fetchDay(calendarId, date, signal)),
    [calendarId, date],
  )

  // 저장하면 서버가 다시 계산한 결과로 갈아끼운다.
  // 화면에서 직접 계산하지 않는 이유는 해석 순서(규칙 → 휴가 → 예외)가
  // 서버에만 있어야 두 곳이 어긋나지 않기 때문이다.
  const [saved, setSaved] = useState<DayDetail | null>(null)
  const detail = saved ?? (loaded.status === 'ready' ? loaded.data : null)

  const [code, setCode] = useState<string | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 날짜가 바뀌거나 새로 불러오면 편집 상태를 서버 값으로 되돌린다
  useEffect(() => {
    setSaved(null)
    setError(null)
  }, [date, calendarId])

  useEffect(() => {
    if (!detail) return
    setCode(detail.override?.code ?? null)
    setNote(detail.override?.note ?? '')
  }, [detail?.date, detail?.override?.id, detail?.override?.version])

  async function run(action: () => Promise<DayDetail | void>) {
    setBusy(true)
    setError(null)
    try {
      const next = await action()
      if (next) setSaved(next)
      else if (calendarId !== undefined) setSaved(await fetchDay(calendarId, date))
    } catch (e) {
      if (e instanceof ApiError && e.isConflict) {
        setError('다른 곳에서 먼저 수정되었습니다. 아래 내용을 다시 확인해 주세요.')
        if (calendarId !== undefined) setSaved(await fetchDay(calendarId, date))
      } else {
        setError(e instanceof Error ? e.message : '저장하지 못했습니다')
      }
    } finally {
      setBusy(false)
    }
  }

  if (calendars.status === 'error' || loaded.status === 'error') {
    return <p className={shared.error} role="alert">불러오지 못했습니다.</p>
  }
  if (!detail || calendarId === undefined) {
    return <p className={shared.notice}>불러오는 중…</p>
  }

  const leaveTypes = detail.scheduleTypes.filter((type) => type.category === 'LEAVE')
  const dirty =
    code !== (detail.override?.code ?? null) || note !== (detail.override?.note ?? '')

  return (
    <div className={styles.page}>
      <PageHeader
        title={formatKoreanDate(date)}
        left={
          <IconButton label="전날" onClick={() => navigate(`/day/${addDays(date, -1)}`)}>←</IconButton>
        }
        right={
          <IconButton label="다음 날" onClick={() => navigate(`/day/${addDays(date, 1)}`)}>→</IconButton>
        }
      />

      <div className={styles.body}>
        <DaySourceCard detail={detail} />

        <section className={styles.section}>
          <h2 className={styles.title}>일정</h2>
          {events.status === 'ready' && events.data.instances.length > 0 ? (
            <ul className={styles.events}>
              {events.data.instances.map((instance) => (
                <li
                  key={`${instance.eventId}-${instance.occurrenceStart}`}
                  className={styles.event}
                  data-cancelled={instance.change === 'CANCELLED' ? '' : undefined}
                >
                  <span
                    className={styles.eventBar}
                    style={instance.color ? { background: instance.color } : undefined}
                    aria-hidden="true"
                  />
                  <span className={styles.eventText}>
                    <span className={styles.eventTitle}>
                      {instance.title}
                      {instance.change === 'CANCELLED' ? ' (취소됨)' : ''}
                    </span>
                    <span className={styles.eventMeta}>
                      {instance.allDay ? '종일' : formatTimeRange(instance.startsAt, instance.endsAt)}
                      {instance.location ? ` · ${instance.location}` : ''}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className={shared.hint}>이 날은 등록된 일정이 없습니다.</p>
          )}
          {/* 편집은 주간 시간표에서 한다. 같은 편집기를 두 곳에 두면 갈라진다. */}
          <button
            type="button"
            className={`${shared.pressable} ${styles.eventLink}`}
            onClick={() => navigate(`/week/${date}`)}
          >
            주간 시간표에서 일정 관리
          </button>
        </section>

        {externals.status === 'ready' && externals.data.events.length > 0 ? (
          <section className={styles.section}>
            <h2 className={styles.title}>구독한 캘린더</h2>
            <ul className={styles.events}>
              {externals.data.events.map((event) => (
                <li
                  key={`${event.sourceId}-${event.startsAt}-${event.title ?? ''}`}
                  className={styles.event}
                >
                  <span
                    className={styles.eventBar}
                    style={event.color ? { background: event.color } : undefined}
                    aria-hidden="true"
                  />
                  <span className={styles.eventText}>
                    <span className={styles.eventTitle}>
                      {event.displayMode === 'INLINE' ? (event.title ?? '(제목 없음)') : event.sourceName}
                    </span>
                    <span className={styles.eventMeta}>
                      {event.allDay ? '종일' : formatTimeRange(event.startsAt, event.endsAt)}
                      {event.displayMode === 'INLINE' ? ` · ${event.sourceName}` : ''}
                      {event.location ? ` · ${event.location}` : ''}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
            <p className={shared.hint}>
              다른 캘린더에서 가져온 일정이라 여기서 고칠 수 없습니다.
            </p>
          </section>
        ) : null}

        {!detail.canEdit ? (
          <p className={shared.hint}>보기 권한만 있어 수정할 수 없습니다.</p>
        ) : (
          <>
            <section className={styles.section}>
              <h2 className={styles.title}>이 날만 근무 변경</h2>
              <CodePicker
                types={detail.scheduleTypes.filter((type) => type.category !== 'LEAVE')}
                selected={code}
                onSelect={setCode}
                disabled={busy}
              />
              <p className={shared.hint}>
                다시 누르면 선택이 풀리고 반복 근무로 돌아갑니다.
              </p>
            </section>

            <section className={styles.section}>
              <h2 className={styles.title}>메모</h2>
              <textarea
                className={styles.note}
                value={note}
                maxLength={2000}
                rows={3}
                placeholder="이 날에 남길 메모"
                disabled={busy}
                onChange={(e) => setNote(e.target.value)}
              />
            </section>

            {error ? <p className={shared.error} role="alert">{error}</p> : null}

            <div className={styles.actions}>
              <button
                type="button"
                className={shared.primaryButton}
                disabled={busy || !dirty}
                onClick={() =>
                  run(() =>
                    saveDay(calendarId, date, {
                      code,
                      note: note.trim() || null,
                      version: detail.override?.version ?? null,
                    }),
                  )
                }
              >
                {busy ? '저장 중…' : '저장'}
              </button>

              {detail.override ? (
                <button
                  type="button"
                  className={`${shared.pressable} ${styles.revert}`}
                  disabled={busy}
                  onClick={() => run(() => clearDay(calendarId, date))}
                >
                  이 날 변경 지우기
                </button>
              ) : null}
            </div>

            <section className={styles.section}>
              <h2 className={styles.title}>휴가</h2>
              {detail.leave ? (
                <div className={styles.leave}>
                  <span>
                    {detail.leave.startDate === detail.leave.endDate
                      ? '이 날 휴가'
                      : `${detail.leave.startDate} ~ ${detail.leave.endDate}`}
                  </span>
                  <button
                    type="button"
                    className={`${shared.pressable} ${styles.revert}`}
                    disabled={busy}
                    onClick={() => run(() => deleteLeave(calendarId, detail.leave!.id))}
                  >
                    취소
                  </button>
                </div>
              ) : leaveTypes.length === 0 ? (
                <p className={shared.hint}>등록된 휴가 종류가 없습니다.</p>
              ) : (
                <div className={styles.leaveButtons}>
                  {leaveTypes.map((type) => (
                    <button
                      key={type.code}
                      type="button"
                      className={`${shared.pressable} ${styles.leaveButton}`}
                      disabled={busy}
                      onClick={() =>
                        run(() =>
                          addLeave(calendarId, {
                            startDate: date,
                            endDate: date,
                            code: type.code,
                          }),
                        )
                      }
                    >
                      {type.name}
                    </button>
                  ))}
                </div>
              )}
            </section>
          </>
        )}
      </div>
    </div>
  )
}
