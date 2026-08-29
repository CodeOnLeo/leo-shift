import type { PresetScheduleType } from '@/api/types'
import { CalendarGrid } from '@/components/ui/CalendarGrid'
import { ScheduleCodeBadge } from '@/components/ui/ScheduleCodeBadge'
import { addDays, monthGrid, today } from '@/lib/date'
import { codeAt, runLengths, weekAlignmentDays } from '@/lib/pattern'
import shared from '@/styles/shared.module.css'
import styles from './PatternPreview.module.css'

/**
 * 패턴 미리보기.
 *
 * 만드는 동안 실제 달력에 어떻게 찍히는지 즉시 보여준다. 이전 구현은
 * "총 12개 순서"라는 글자가 전부라, 기준일을 하루 잘못 잡았는지 저장한 뒤에야 알았다.
 */
export function PatternPreview({
  sequence,
  anchorDate,
  typesByCode,
}: {
  sequence: readonly string[]
  anchorDate: string
  typesByCode: ReadonlyMap<string, PresetScheduleType>
}) {
  if (sequence.length === 0) {
    return <p className={shared.hint}>근무 순서를 만들면 여기에 미리보기가 나옵니다.</p>
  }

  const now = today()
  const year = Number(now.slice(0, 4))
  const month = Number(now.slice(5, 7))

  return (
    <div>
      <dl className={styles.facts}>
        <div>
          <dt>주기</dt>
          <dd>{sequence.length}일</dd>
        </div>
        <div>
          <dt>요일과 다시 맞는 주기</dt>
          <dd>{weekAlignmentDays(sequence.length)}일</dd>
        </div>
      </dl>

      <ol className={styles.runs}>
        {runLengths(sequence).map((run, index) => {
          const type = typesByCode.get(run.code)
          return (
            <li key={`${run.code}-${index}`} className={styles.run}>
              <span
                className={styles.dot}
                style={{ background: type?.color ?? 'var(--border-strong)' }}
                aria-hidden="true"
              />
              {type?.name ?? run.code}
              {run.count > 1 ? <span className={styles.count}>×{run.count}</span> : null}
            </li>
          )
        })}
      </ol>

      <p className={shared.caption}>
        {year}년 {month}월 기준
      </p>

      <CalendarGrid
        dates={monthGrid(year, month)}
        month={month}
        size="sm"
        label="패턴 미리보기"
        dayLabel={(date) => {
          const code = codeAt(sequence, anchorDate, date)
          return code ? `${date} ${typesByCode.get(code)?.name ?? code}` : date
        }}
        renderDay={(date) => {
          const code = codeAt(sequence, anchorDate, date)
          if (!code) return null
          const type = typesByCode.get(code)
          return <ScheduleCodeBadge code={code} color={type?.color} size="sm" label={type?.name} />
        }}
      />

      <p className={shared.caption}>
        다음 7일:{' '}
        {Array.from({ length: 7 }, (_, i) => addDays(now, i))
          .map((date) => codeAt(sequence, anchorDate, date) ?? '-')
          .join(' · ')}
      </p>
    </div>
  )
}
