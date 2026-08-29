import type { PresetScheduleType } from '@/api/types'
import { codeAt, runLengths, weekAlignmentDays } from '@/lib/pattern'
import { addDays, isoWeekday, monthGrid, today, weekdayHeaders } from '@/lib/date'
import styles from './PatternPreview.module.css'

/**
 * 패턴 미리보기.
 *
 * 만드는 동안 실제 달력에 어떻게 찍히는지 즉시 보여준다.
 * 이전 구현은 "총 12개 순서"라는 글자가 전부라, 기준일을 하루 잘못 잡았는지
 * 저장한 뒤에야 알 수 있었다.
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
    return <p className={styles.empty}>근무 순서를 만들면 여기에 미리보기가 나옵니다.</p>
  }

  const now = today()
  const year = Number(now.slice(0, 4))
  const month = Number(now.slice(5, 7))
  const dates = monthGrid(year, month)
  const alignment = weekAlignmentDays(sequence.length)

  return (
    <div>
      <dl className={styles.facts}>
        <div>
          <dt>주기</dt>
          <dd>{sequence.length}일</dd>
        </div>
        <div>
          <dt>요일과 다시 맞는 주기</dt>
          <dd>{alignment}일</dd>
        </div>
      </dl>

      <ol className={styles.runs}>
        {runLengths(sequence).map((run, index) => {
          const type = typesByCode.get(run.code)
          return (
            <li key={`${run.code}-${index}`} className={styles.run}>
              <span
                className={styles.chip}
                style={{ background: type?.color ?? 'var(--border-strong)' }}
                aria-hidden="true"
              />
              {type?.name ?? run.code}
              {run.count > 1 ? <span className={styles.count}>×{run.count}</span> : null}
            </li>
          )
        })}
      </ol>

      <p className={styles.caption}>
        {year}년 {month}월 기준
      </p>

      <div className={styles.headers} aria-hidden="true">
        {weekdayHeaders().map((label, index) => (
          <span key={label} data-weekend={index === 5 ? 'sat' : index === 6 ? 'sun' : undefined}>
            {label}
          </span>
        ))}
      </div>

      <div className={styles.grid}>
        {dates.map((date) => {
          const code = codeAt(sequence, anchorDate, date)
          const type = code ? typesByCode.get(code) : undefined
          const inMonth = Number(date.slice(5, 7)) === month
          const weekday = isoWeekday(date)
          return (
            <div
              key={date}
              className={styles.cell}
              data-outside={inMonth ? undefined : ''}
              data-weekend={weekday === 6 ? 'sat' : weekday === 7 ? 'sun' : undefined}
              title={`${date} ${type?.name ?? code ?? ''}`}
            >
              <span className={styles.dayNumber}>{Number(date.slice(8, 10))}</span>
              {code ? (
                <span
                  className={styles.dayCode}
                  style={{ background: type?.color ?? 'var(--border-strong)' }}
                >
                  {code}
                </span>
              ) : null}
            </div>
          )
        })}
      </div>

      <p className={styles.caption}>
        다음 7일: {Array.from({ length: 7 }, (_, i) => addDays(now, i))
          .map((date) => codeAt(sequence, anchorDate, date) ?? '-')
          .join(' · ')}
      </p>
    </div>
  )
}
