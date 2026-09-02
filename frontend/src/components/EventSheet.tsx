import type { EventInstance } from '@/api/types'
import { formatKoreanDate } from '@/lib/date'
import { formatTimeRange, instantDate } from '@/lib/datetime'
import shared from '@/styles/shared.module.css'
import styles from './EventSheet.module.css'

const CHANGE_LABEL: Record<EventInstance['change'], string> = {
  NONE: '',
  CANCELLED: '취소된 회차',
  MOVED: '옮긴 회차',
  MODIFIED: '이 회차만 수정됨',
}

/**
 * 일정 하나를 눌렀을 때.
 *
 * 반복 일정에서는 <b>회차에 대한 동작과 시리즈에 대한 동작을 갈라 놓는다.</b>
 * "이번 주 휴강"과 "이 수업 그만두기"가 같은 버튼 옆에 있으면 안 된다.
 */
export function EventSheet({
  instance,
  busy,
  onEdit,
  onCancelOccurrence,
  onRestoreOccurrence,
  onDelete,
  onClose,
}: {
  instance: EventInstance
  busy: boolean
  onEdit: () => void
  onCancelOccurrence: () => void
  onRestoreOccurrence: () => void
  onDelete: () => void
  onClose: () => void
}) {
  const changed = instance.change !== 'NONE'

  return (
    <div className={styles.sheet} role="dialog" aria-label={instance.title}>
      <div className={styles.head}>
        <span
          className={styles.color}
          style={instance.color ? { background: instance.color } : undefined}
          aria-hidden="true"
        />
        <span className={styles.text}>
          <span className={styles.title}>{instance.title}</span>
          <span className={styles.when}>
            {formatKoreanDate(instantDate(instance.startsAt))}
            {instance.allDay ? ' · 종일' : ` · ${formatTimeRange(instance.startsAt, instance.endsAt)}`}
          </span>
          <span className={styles.meta}>
            {instance.calendarName}
            {instance.recurring ? ' · 반복' : ''}
            {changed ? ` · ${CHANGE_LABEL[instance.change]}` : ''}
          </span>
        </span>
        <button type="button" className={styles.close} aria-label="닫기" onClick={onClose}>
          ×
        </button>
      </div>

      {instance.location ? <p className={styles.detail}>{instance.location}</p> : null}
      {instance.description ? <p className={styles.detail}>{instance.description}</p> : null}

      {!instance.canEdit ? (
        <p className={shared.hint}>보기 권한만 있어 수정할 수 없습니다.</p>
      ) : (
        <div className={styles.actions}>
          <button
            type="button"
            className={`${shared.pressable} ${styles.action}`}
            disabled={busy}
            onClick={onEdit}
          >
            수정
          </button>

          {instance.recurring ? (
            instance.change === 'CANCELLED' || instance.change === 'MOVED' ? (
              <button
                type="button"
                className={`${shared.pressable} ${styles.action}`}
                disabled={busy}
                onClick={onRestoreOccurrence}
              >
                이 회차 되돌리기
              </button>
            ) : (
              <button
                type="button"
                className={`${shared.pressable} ${styles.action}`}
                disabled={busy}
                onClick={onCancelOccurrence}
              >
                이 회차 취소
              </button>
            )
          ) : null}

          <button
            type="button"
            className={`${shared.pressable} ${styles.danger}`}
            disabled={busy}
            onClick={onDelete}
          >
            {instance.recurring ? '반복 전체 삭제' : '삭제'}
          </button>
        </div>
      )}
    </div>
  )
}
