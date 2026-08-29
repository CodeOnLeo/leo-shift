import { useCallback, useMemo } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchCalendars, fetchSchedule } from '@/api/calendar'
import type { ResolvedDay, ScheduleType } from '@/api/types'
import { MonthGrid } from '@/components/MonthGrid'
import { ScheduleLegend } from '@/components/ScheduleLegend'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { addMonths, monthGrid, today } from '@/lib/date'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'

export function MonthPage() {
  const params = useParams()
  const navigate = useNavigate()

  const now = today()
  const year = Number(params.year ?? now.slice(0, 4))
  const month = Number(params.month ?? now.slice(5, 7))

  const dates = useMemo(() => monthGrid(year, month), [year, month])

  const calendars = useAsync((signal) => fetchCalendars(signal), [])
  const calendarId =
    calendars.status === 'ready'
      ? (calendars.data.find((calendar) => calendar.isDefault) ?? calendars.data[0])?.id
      : undefined

  const schedule = useAsync(
    async (signal) => {
      if (calendarId === undefined) return null
      return fetchSchedule(
        { calendarId, from: dates[0]!, to: dates[dates.length - 1]!, year, month },
        signal,
      )
    },
    [calendarId, dates[0], dates[dates.length - 1], year, month],
  )

  // 이동 목표를 URL에서 계산한다. 이전 구현은 비동기로 갱신되는 상태에서 계산해서
  // 월 이동 버튼을 빠르게 누르면 클릭이 씹혔다.
  const step = useCallback(
    (delta: number) => {
      const target = addMonths(`${year}-${String(month).padStart(2, '0')}-01`, delta)
      navigate(`/month/${target.slice(0, 4)}/${Number(target.slice(5, 7))}`)
    },
    [year, month, navigate],
  )

  const byDate = useMemo(() => {
    const map = new Map<string, ResolvedDay>()
    if (schedule.status === 'ready' && schedule.data) {
      for (const day of schedule.data.days) map.set(day.date, day)
    }
    return map
  }, [schedule])

  const typesByCode = useMemo(() => {
    const map = new Map<string, ScheduleType>()
    if (schedule.status === 'ready' && schedule.data) {
      for (const type of schedule.data.scheduleTypes) map.set(type.code, type)
    }
    return map
  }, [schedule])

  return (
    <div>
      <PageHeader
        title={`${year}년 ${month}월`}
        left={<IconButton label="이전 달" onClick={() => step(-1)}>←</IconButton>}
        right={<IconButton label="다음 달" onClick={() => step(1)}>→</IconButton>}
      />

      {/* 상태 변화를 스크린리더에도 알린다 */}
      <p className="visually-hidden" role="status">
        {schedule.status === 'loading' ? '근무를 불러오는 중' : ''}
      </p>

      <MonthGrid
        dates={dates}
        month={month}
        byDate={byDate}
        typesByCode={typesByCode}
        onSelect={(date) => navigate(`/day/${date}`)}
      />

      {schedule.status === 'ready' && schedule.data ? (
        <ScheduleLegend types={schedule.data.scheduleTypes} summary={schedule.data.summary} />
      ) : null}

      {calendars.status === 'ready' && calendars.data.length === 0 ? (
        <p className={shared.notice}>아직 캘린더가 없습니다.</p>
      ) : null}

      {schedule.status === 'error' ? (
        <p className={shared.error} role="alert">{schedule.error.message}</p>
      ) : null}
      {calendars.status === 'error' ? (
        <p className={shared.error} role="alert">{calendars.error.message}</p>
      ) : null}
    </div>
  )
}
