import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchCalendars } from '@/api/calendar'
import { addLeave, clearDay, deleteLeave, fetchDay, saveDay } from '@/api/day'
import { ApiError } from '@/api/client'
import type { DayDetail } from '@/api/types'
import { CodePicker } from '@/components/CodePicker'
import { DaySourceCard } from '@/components/DaySourceCard'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { addDays, formatKoreanDate, today } from '@/lib/date'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './DayPage.module.css'

export function DayPage() {
  const { date = today() } = useParams()
  const navigate = useNavigate()

  const calendars = useAsync((signal) => fetchCalendars(signal), [])
  const calendarId =
    calendars.status === 'ready'
      ? (calendars.data.find((calendar) => calendar.isDefault) ?? calendars.data[0])?.id
      : undefined

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
