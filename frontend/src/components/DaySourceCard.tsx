import type { DayDetail } from '@/api/types'
import { ScheduleCodeBadge } from '@/components/ui/ScheduleCodeBadge'
import { sourceHint, sourceLabel } from '@/lib/scheduleSource'
import styles from './DaySourceCard.module.css'

/**
 * 그날의 근무와 그게 어디서 왔는지.
 *
 * 출처를 보여주는 게 핵심이다. "이 날만 변경"인지 "반복 근무"인지 알아야
 * 무엇을 고쳐야 하는지 판단할 수 있다.
 */
export function DaySourceCard({ detail }: { detail: DayDetail }) {
  const type = detail.scheduleType
  const changed = detail.source === 'OVERRIDE' && detail.baseCode !== detail.code

  return (
    <div className={styles.card}>
      <div className={styles.headline}>
        {type ? (
          <ScheduleCodeBadge code={type.code} color={type.color} label={type.name} />
        ) : null}
        <span className={styles.name}>{type?.name ?? '근무 없음'}</span>
        {type?.startTime && type.endTime ? (
          <span className={styles.time}>
            {type.startTime.slice(0, 5)}–{type.endTime.slice(0, 5)}
            {type.crossesMidnight ? ' +1일' : ''}
          </span>
        ) : null}
      </div>

      <p className={styles.source}>
        <span className={styles.badge}>{sourceLabel(detail.source)}</span>
        {sourceHint(detail.source)}
      </p>

      {changed ? (
        <p className={styles.base}>지우면 원래 근무({detail.baseCode})로 돌아갑니다.</p>
      ) : null}

      {detail.leave?.startDate && detail.leave.startDate !== detail.leave.endDate ? (
        <p className={styles.base}>
          {detail.leave.startDate} ~ {detail.leave.endDate} 휴가 중 하루입니다.
        </p>
      ) : null}
    </div>
  )
}
