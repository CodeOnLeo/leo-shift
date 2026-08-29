import type { DaySource } from '@/api/types'

/**
 * 근무가 어디서 왔는지 사람 말로.
 *
 * 화면에 이걸 보여주는 이유는, 사용자가 "왜 이 날이 야간이지?"를 알고
 * 무엇을 고쳐야 하는지 판단할 수 있게 하기 위해서다.
 */
export function sourceLabel(source: DaySource): string {
  switch (source) {
    case 'RULE':
      return '반복 근무'
    case 'LEAVE':
      return '휴가'
    case 'OVERRIDE':
      return '이 날만 변경'
    case 'NONE':
      return '지정 없음'
  }
}

export function sourceHint(source: DaySource): string {
  switch (source) {
    case 'RULE':
      return '근무 패턴에서 계산된 근무입니다.'
    case 'LEAVE':
      return '휴가 기간에 포함된 날입니다.'
    case 'OVERRIDE':
      return '이 날만 따로 지정했습니다.'
    case 'NONE':
      return '적용되는 근무 패턴이 없습니다.'
  }
}
