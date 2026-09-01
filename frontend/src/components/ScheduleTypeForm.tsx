import { useState } from 'react'
import type { ScheduleType } from '@/api/types'
import type { SaveScheduleTypeBody } from '@/api/scheduleType'
import { ColorPicker } from '@/components/ColorPicker'
import { ScheduleCodeBadge } from '@/components/ui/ScheduleCodeBadge'
import shared from '@/styles/shared.module.css'
import styles from './ScheduleTypeForm.module.css'

const CATEGORIES = [
  { value: 'WORK', label: '근무', hint: '실제로 일하는 시간' },
  { value: 'OFF', label: '휴무', hint: '쉬는 날. 비번 포함' },
  { value: 'LEAVE', label: '휴가', hint: '연차·반차 등' },
] as const

type Category = (typeof CATEGORIES)[number]['value']

function toDraft(type: ScheduleType | null): SaveScheduleTypeBody {
  return {
    code: type?.code ?? '',
    name: type?.name ?? '',
    color: type?.color ?? '#2563EB',
    category: type?.category ?? 'WORK',
    startTime: type?.startTime?.slice(0, 5) ?? '09:00',
    endTime: type?.endTime?.slice(0, 5) ?? '18:00',
    halfDay: type?.halfDay ?? false,
  }
}

export function ScheduleTypeForm({
  type,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  /** null이면 새로 만들기 */
  type: ScheduleType | null
  busy: boolean
  error: string | null
  onSubmit: (body: SaveScheduleTypeBody) => void
  onCancel: () => void
}) {
  const [draft, setDraft] = useState<SaveScheduleTypeBody>(() => toDraft(type))
  const patch = (next: Partial<SaveScheduleTypeBody>) => setDraft((prev) => ({ ...prev, ...next }))

  const isWork = draft.category === 'WORK'
  // 야간 22:00~06:00처럼 종료가 시작보다 이르면 자정을 넘는다. 서버도 같게 계산한다.
  const overnight =
    isWork && draft.startTime && draft.endTime && draft.endTime <= draft.startTime

  const valid =
    draft.code.trim().length > 0 &&
    draft.name.trim().length > 0 &&
    (!isWork || (!!draft.startTime && !!draft.endTime))

  return (
    <form
      className={styles.form}
      onSubmit={(e) => {
        e.preventDefault()
        if (valid && !busy) {
          onSubmit({
            ...draft,
            code: draft.code.trim().toUpperCase(),
            name: draft.name.trim(),
            startTime: isWork ? draft.startTime : null,
            endTime: isWork ? draft.endTime : null,
            halfDay: draft.category === 'LEAVE' && draft.halfDay,
          })
        }
      }}
    >
      <div className={styles.preview}>
        <ScheduleCodeBadge
          code={draft.code.trim().toUpperCase() || '?'}
          color={draft.color}
          label={draft.name}
        />
        <span className={styles.previewName}>{draft.name || '이름 없음'}</span>
      </div>

      <div className={styles.row}>
        <label className={styles.label}>
          코드
          <input
            className={shared.field}
            value={draft.code}
            maxLength={32}
            placeholder="N"
            disabled={busy}
            onChange={(e) => patch({ code: e.target.value.toUpperCase() })}
          />
        </label>
        <label className={styles.label}>
          이름
          <input
            className={shared.field}
            value={draft.name}
            maxLength={100}
            placeholder="야간"
            disabled={busy}
            onChange={(e) => patch({ name: e.target.value })}
          />
        </label>
      </div>

      {type ? (
        <p className={shared.hint}>
          코드를 바꾸면 이미 만든 근무표와 휴가의 표시도 함께 바뀝니다.
        </p>
      ) : null}

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>종류</legend>
        <div className={styles.categories} role="radiogroup" aria-label="종류">
          {CATEGORIES.map((option) => (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={draft.category === option.value}
              disabled={busy}
              className={`${shared.pressable} ${styles.category} ${
                draft.category === option.value ? styles.categoryOn : ''
              }`}
              onClick={() => patch({ category: option.value as Category })}
            >
              <span>{option.label}</span>
              <span className={styles.categoryHint}>{option.hint}</span>
            </button>
          ))}
        </div>
      </fieldset>

      {isWork ? (
        <div className={styles.row}>
          <label className={styles.label}>
            시작
            <input
              className={shared.field}
              type="time"
              value={draft.startTime ?? ''}
              disabled={busy}
              onChange={(e) => patch({ startTime: e.target.value })}
            />
          </label>
          <label className={styles.label}>
            종료
            <input
              className={shared.field}
              type="time"
              value={draft.endTime ?? ''}
              disabled={busy}
              onChange={(e) => patch({ endTime: e.target.value })}
            />
          </label>
        </div>
      ) : null}

      {overnight ? <p className={shared.hint}>자정을 넘는 근무로 처리됩니다.</p> : null}

      {draft.category === 'LEAVE' ? (
        <label className={styles.checkbox}>
          <input
            type="checkbox"
            checked={draft.halfDay}
            disabled={busy}
            onChange={(e) => patch({ halfDay: e.target.checked })}
          />
          반차 (반나절만 쉼)
        </label>
      ) : null}

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>색</legend>
        <ColorPicker value={draft.color} disabled={busy} onChange={(color) => patch({ color })} />
      </fieldset>

      {error ? <p className={shared.error} role="alert">{error}</p> : null}

      <div className={styles.actions}>
        <button type="submit" className={shared.primaryButton} disabled={busy || !valid}>
          {busy ? '저장 중…' : '저장'}
        </button>
        <button
          type="button"
          className={`${shared.pressable} ${styles.cancel}`}
          disabled={busy}
          onClick={onCancel}
        >
          취소
        </button>
      </div>
    </form>
  )
}
