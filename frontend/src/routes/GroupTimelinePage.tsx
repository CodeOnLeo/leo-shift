import { useParams } from 'react-router-dom'
import { Placeholder } from '@/components/Placeholder'

/**
 * 그룹 타임라인. 이 앱의 핵심 차별점이다.
 *
 * 프로젝트 인원의 부재 현황을 한눈에 보려고 만든 화면이고,
 * 그룹 종류만 바꿔서 직장·친구·가족에도 그대로 쓰인다.
 */
export function GroupTimelinePage() {
  const { groupId } = useParams()

  return (
    <Placeholder
      title="그룹 타임라인"
      description={`그룹 ${groupId ?? ''} · 멤버 × 날짜 격자. 보기 전용이다.`}
      todo={[
        '멤버를 행, 날짜를 열로 배치',
        '하단에 날짜별 가용 인원 — 프로젝트에서 실제로 보고 싶은 값',
        '멤버십 기간 반영 — 그 달에 소속됐던 사람만 표시',
        '휴가·부재를 눈에 띄게 강조',
        '주 단위 이동',
      ]}
    />
  )
}
