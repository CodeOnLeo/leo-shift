import { useMemo } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { fetchTimeline } from '@/api/group'
import { TimelineGrid } from '@/components/TimelineGrid'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { addMonths, today } from '@/lib/date'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './GroupTimelinePage.module.css'

/**
 * 그룹 타임라인. 이 앱의 핵심 차별점이다.
 *
 * 프로젝트 인원의 부재 현황을 한눈에 보려고 만든 화면이고, 그룹 종류만 바꿔서
 * 직장·친구·가족에도 그대로 쓰인다.
 *
 * 보기 전용이다. 여기서 남의 근무를 고칠 수 있게 하면 "각자 자기 일정을 관리한다"는
 * 전제가 깨지고, 결국 팀 근무표 관리가 된다.
 */
export function GroupTimelinePage() {
  const { groupId } = useParams()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  const id = Number(groupId)
  const month = params.get('month') ?? today().slice(0, 7)

  // 달 단위로 본다. 멤버십도 "8월에 있던 사람"처럼 달로 읽히고,
  // 프로젝트에서 휴가를 맞추는 단위도 달이다.
  const [from, to] = useMemo(() => monthRange(month), [month])

  const timeline = useAsync(
    async (signal) =>
      Number.isFinite(id) ? fetchTimeline(id, { from, to }, signal) : null,
    [id, from, to],
  )

  const step = (delta: number) => {
    setParams({ month: addMonths(`${month}-01`, delta).slice(0, 7) }, { replace: true })
  }

  if (timeline.status === 'error') {
    return (
      <p className={shared.error} role="alert">
        {timeline.error.message}
      </p>
    )
  }
  if (timeline.status !== 'ready' || !timeline.data) {
    return <p className={shared.notice}>불러오는 중…</p>
  }

  const data = timeline.data
  const [year, monthNumber] = month.split('-')

  return (
    <div className={styles.page}>
      <PageHeader
        title={data.groupName}
        left={<IconButton label="그룹 목록" onClick={() => navigate('/groups')}>←</IconButton>}
        right={
          <IconButton label="멤버 관리" onClick={() => navigate(`/groups/${id}/members`)}>
            ⋯
          </IconButton>
        }
      />

      <div className={styles.monthBar}>
        <IconButton label="이전 달" onClick={() => step(-1)}>←</IconButton>
        <span className={styles.month}>
          {year}년 {Number(monthNumber)}월
        </span>
        <IconButton label="다음 달" onClick={() => step(1)}>→</IconButton>
      </div>

      {/* 내 줄이 남에게는 비어 보인다는 사실은 여기서 알려주지 않으면 알 길이 없다. */}
      {data.viewerShared ? null : (
        <p className={styles.warn}>
          내 근무를 이 그룹에 공유하지 않았습니다. 지금은 나만 내 줄을 볼 수 있습니다.{' '}
          <Link to="/settings/sharing">공유 설정</Link>
        </p>
      )}

      {data.rows.length === 0 ? (
        <p className={shared.notice}>이 기간에 소속된 멤버가 없습니다.</p>
      ) : (
        <TimelineGrid
          dates={data.dates}
          rows={data.rows}
          workingCount={data.workingCount}
          absentCount={data.absentCount}
        />
      )}

      <p className={shared.caption}>
        보기 전용입니다. 근무와 휴가는 각자 자기 캘린더에서 관리합니다.
      </p>
    </div>
  )
}

/** YYYY-MM → 그 달의 1일과 말일. */
function monthRange(month: string): [string, string] {
  const [year, monthNumber] = month.split('-').map(Number)
  const lastDay = new Date(year!, monthNumber!, 0).getDate()
  return [`${month}-01`, `${month}-${String(lastDay).padStart(2, '0')}`]
}
