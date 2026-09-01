import { useState, type FormEvent } from 'react'
import type { GroupDetail, GroupKind, GroupSummary } from '@/api/types'
import type { SaveGroupBody } from '@/api/group'
import { ColorPicker } from '@/components/ColorPicker'
import shared from '@/styles/shared.module.css'
import styles from './GroupForm.module.css'

/** 그룹 종류는 화면을 바꾸지 않는다. 목록에서 성격을 구분하고 기본 색을 고르는 데 쓴다. */
export const GROUP_KINDS: readonly { value: GroupKind; label: string; hint: string }[] = [
  { value: 'PROJECT', label: '프로젝트', hint: '인원이 바뀌는 단위' },
  { value: 'WORKPLACE', label: '직장', hint: '같은 회사·부서' },
  { value: 'FAMILY', label: '가족', hint: '가족·커플' },
  { value: 'FRIENDS', label: '친구', hint: '약속 잡기' },
  { value: 'OTHER', label: '기타', hint: '' },
]

const DEFAULT_COLOR = '#2563EB'

export function GroupForm({
  group,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  group: GroupSummary | GroupDetail | null
  busy: boolean
  error: string | null
  onSubmit: (body: SaveGroupBody) => void
  onCancel: () => void
}) {
  const [name, setName] = useState(group?.name ?? '')
  const [kind, setKind] = useState<GroupKind>(group?.kind ?? 'PROJECT')
  const [description, setDescription] = useState(group?.description ?? '')
  const [color, setColor] = useState(group?.color ?? DEFAULT_COLOR)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSubmit({
      name: name.trim(),
      kind,
      description: description.trim() || null,
      color,
    })
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      <label className={styles.label}>
        이름
        <input
          className={shared.field}
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="OO프로젝트"
          maxLength={100}
          required
          autoFocus
        />
      </label>

      <fieldset className={styles.kinds}>
        <legend className={styles.legend}>종류</legend>
        <div className={styles.kindOptions} role="radiogroup" aria-label="그룹 종류">
          {GROUP_KINDS.map((option) => (
            <label key={option.value} className={styles.kindOption}>
              <input
                type="radio"
                name="kind"
                value={option.value}
                checked={kind === option.value}
                onChange={() => setKind(option.value)}
              />
              {option.label}
            </label>
          ))}
        </div>
      </fieldset>

      <label className={styles.label}>
        설명 (선택)
        <input
          className={shared.field}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="무엇을 같이 보는 그룹인지"
          maxLength={500}
        />
      </label>

      <div className={styles.label}>
        색
        <ColorPicker value={color} onChange={setColor} />
      </div>

      {error ? (
        <p className={shared.error} role="alert">
          {error}
        </p>
      ) : null}

      <div className={styles.actions}>
        <button
          type="button"
          className={`${shared.pressable} ${styles.cancel}`}
          onClick={onCancel}
          disabled={busy}
        >
          취소
        </button>
        <button type="submit" className={shared.primaryButton} disabled={busy || !name.trim()}>
          {group ? '저장' : '만들기'}
        </button>
      </div>
    </form>
  )
}
