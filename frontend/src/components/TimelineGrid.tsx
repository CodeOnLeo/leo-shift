import type { TimelineDay, TimelineRow } from '@/api/types'
import { contrastText } from '@/lib/color'
import { isoWeekday, today, weekdayLabel } from '@/lib/date'
import styles from './TimelineGrid.module.css'

/**
 * 멤버 × 날짜 격자.
 *
 * 한 달 30칸은 휴대폰 폭에 들어가지 않는다. 주 단위로 쪼개면 "이번 달 누가 언제
 * 쉬지?"를 보려고 네다섯 번 넘겨야 하는데, 그건 이 화면을 만든 이유를 잃는 것이다.
 * 그래서 <b>가로로 스크롤하되 이름 칸을 고정</b>한다. 어디까지 밀어도 누구 줄인지
 * 알 수 있고, 데스크톱에서는 한 달이 통째로 보인다.
 *
 * 칸은 색이 먼저고 글자가 나중이다. 사람마다 코드가 달라도("N" / "야간") 색과
 * category는 같은 뜻이라, 훑어볼 때 읽는 건 결국 색이다.
 */
export function TimelineGrid({
  dates,
  rows,
  workingCount,
  absentCount,
}: {
  dates: readonly string[]
  rows: readonly TimelineRow[]
  workingCount: readonly number[]
  absentCount: readonly number[]
}) {
  const currentDay = today()

  return (
    <div className={styles.scroller}>
      <table className={styles.table}>
        <caption className="visually-hidden">
          멤버별 근무 현황. 가로는 날짜, 세로는 사람이다.
        </caption>

        {/*
          열 폭을 고정한다. 자동 레이아웃에 맡기면 "ANNUAL"처럼 긴 코드가 있는 날만
          열이 벌어지고 글자가 옆 칸으로 넘친다. 격자는 폭이 일정해야 훑어진다.
        */}
        <colgroup>
          <col className={styles.nameCol} />
          {dates.map((date) => (
            <col key={date} className={styles.dateCol} />
          ))}
        </colgroup>

        <thead>
          <tr>
            <th scope="col" className={`${styles.nameCell} ${styles.corner}`}>
              멤버
            </th>
            {dates.map((date) => {
              const weekday = isoWeekday(date)
              return (
                <th
                  key={date}
                  scope="col"
                  className={styles.dateCell}
                  data-weekend={weekday === 6 ? 'sat' : weekday === 7 ? 'sun' : undefined}
                  data-today={date === currentDay ? '' : undefined}
                >
                  <span className={styles.dateNumber}>{Number(date.slice(8, 10))}</span>
                  <span className={styles.dateWeekday}>{weekdayLabel(date)}</span>
                </th>
              )
            })}
          </tr>
        </thead>

        <tbody>
          {rows.map((row) => (
            <tr key={row.userId}>
              <th scope="row" className={styles.nameCell} data-self={row.self ? '' : undefined}>
                <span className={styles.nameInner}>
                  <span
                    className={styles.dot}
                    style={row.colorTag ? { background: row.colorTag } : undefined}
                    aria-hidden="true"
                  />
                  <span className={styles.name}>{row.displayName}</span>
                  {/* 줄이 왜 비어 있는지 알려준다. 데이터가 없는 것과 공유를 안 한 것은 다르다. */}
                  {row.shared ? null : <span className={styles.unshared}>미공유</span>}
                </span>
              </th>

              {row.days.map((day) => (
                <Cell key={day.date} day={day} name={row.displayName} />
              ))}
            </tr>
          ))}
        </tbody>

        {/* 프로젝트에서 실제로 보고 싶은 값. 격자보다 이 줄을 먼저 읽는 사람이 많다. */}
        <tfoot>
          <tr>
            <th scope="row" className={`${styles.nameCell} ${styles.footLabel}`}>
              근무
            </th>
            {workingCount.map((count, index) => (
              <td key={dates[index]} className={styles.countCell}>
                {count || ''}
              </td>
            ))}
          </tr>
          <tr>
            <th scope="row" className={`${styles.nameCell} ${styles.footLabel}`}>
              휴가
            </th>
            {absentCount.map((count, index) => (
              <td
                key={dates[index]}
                className={styles.countCell}
                data-absent={count > 0 ? '' : undefined}
              >
                {count || ''}
              </td>
            ))}
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

function Cell({ day, name }: { day: TimelineDay; name: string }) {
  if (!day.member) {
    // 그때 이 그룹에 없던 사람. 빈칸과 구분돼야 "언제부터 합류했는지"가 읽힌다.
    return <td className={styles.cell} data-outside="" aria-label={`${name} 소속 아님`} />
  }
  if (!day.code) {
    return <td className={styles.cell} />
  }

  return (
    <td className={styles.cell} data-category={day.category ?? undefined}>
      <span
        className={styles.chip}
        style={day.color ? { background: day.color, color: contrastText(day.color) } : undefined}
        title={`${name} · ${day.name ?? day.code}`}
      >
        {day.code}
      </span>
    </td>
  )
}
