import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  addMember,
  deleteGroup,
  endMembership,
  fetchGroup,
  leaveGroup,
  updateGroup,
  updateMember,
  type SaveGroupBody,
} from '@/api/group'
import type { GroupMember } from '@/api/types'
import { GroupForm } from '@/components/GroupForm'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { today } from '@/lib/date'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './GroupMembersPage.module.css'

/**
 * 그룹 관리.
 *
 * 멤버를 내보내도 <b>행이 사라지지 않고 종료일이 적힌다.</b> 그래야 지난달
 * 타임라인에 그 사람이 그대로 남는다. 화면에서도 나간 사람을 계속 보여주는 이유가
 * 같다 — 감추면 "저 사람 언제까지 있었지?"를 확인할 수 없고 기간을 잘못 적었을 때
 * 되돌릴 방법도 없다.
 */
export function GroupMembersPage() {
  const { groupId } = useParams()
  const navigate = useNavigate()
  const id = Number(groupId)

  const [reloadKey, setReloadKey] = useState(0)
  const group = useAsync(
    async (signal) => (Number.isFinite(id) ? fetchGroup(id, signal) : null),
    [id, reloadKey],
  )

  const [editingGroup, setEditingGroup] = useState(false)
  const [editingMember, setEditingMember] = useState<number | null>(null)
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<unknown>, after?: () => void) {
    setBusy(true)
    setError(null)
    try {
      await action()
      setEditingGroup(false)
      setEditingMember(null)
      if (after) after()
      else setReloadKey((key) => key + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : '처리하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  const invite = (event: FormEvent) => {
    event.preventDefault()
    const value = email.trim()
    if (!value) return
    void run(async () => {
      await addMember(id, value)
      setEmail('')
    })
  }

  if (group.status === 'error') {
    return (
      <p className={shared.error} role="alert">
        {group.error.message}
      </p>
    )
  }
  if (group.status !== 'ready' || !group.data) {
    return <p className={shared.notice}>불러오는 중…</p>
  }

  const data = group.data
  const active = data.members.filter((member) => member.active)
  const past = data.members.filter((member) => !member.active)

  return (
    <div className={styles.page}>
      <PageHeader
        title="그룹 관리"
        left={<IconButton label="타임라인" onClick={() => navigate(`/groups/${id}`)}>←</IconButton>}
      />

      <div className={styles.body}>
        <section className={styles.section}>
          {editingGroup ? (
            <GroupForm
              group={data}
              busy={busy}
              error={error}
              onSubmit={(body: SaveGroupBody) => void run(() => updateGroup(id, body))}
              onCancel={() => {
                setEditingGroup(false)
                setError(null)
              }}
            />
          ) : (
            <div className={styles.groupRow}>
              <span
                className={styles.bar}
                style={data.color ? { background: data.color } : undefined}
                aria-hidden="true"
              />
              <span className={styles.groupText}>
                <span className={styles.groupName}>{data.name}</span>
                {data.description ? (
                  <span className={styles.meta}>{data.description}</span>
                ) : null}
              </span>
              {data.owner ? (
                <IconButton
                  label="그룹 정보 편집"
                  disabled={busy}
                  onClick={() => {
                    setEditingGroup(true)
                    setError(null)
                  }}
                >
                  ✎
                </IconButton>
              ) : null}
            </div>
          )}
        </section>

        <section className={styles.section}>
          <h2 className={styles.heading}>멤버 {active.length}명</h2>
          <p className={shared.hint}>
            그룹에 넣는 것만으로는 그 사람의 일정이 보이지 않습니다. 각자 자기
            캘린더를 이 그룹에 공유해야 타임라인에 나옵니다.
          </p>

          <ul className={styles.list}>
            {active.map((member) => (
              <MemberRow
                key={member.memberId}
                member={member}
                canManage={data.owner}
                busy={busy}
                editing={editingMember === member.memberId}
                onEdit={() => {
                  setEditingMember(member.memberId)
                  setError(null)
                }}
                onCancel={() => setEditingMember(null)}
                onSave={(body) => void run(() => updateMember(id, member.memberId, body))}
                onRemove={() => {
                  if (!window.confirm(`${member.name} 님을 오늘까지로 내보낼까요?`)) return
                  void run(() => endMembership(id, member.memberId, today()))
                }}
              />
            ))}
          </ul>

          {data.owner ? (
            <form className={styles.invite} onSubmit={invite}>
              <input
                className={shared.field}
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="이메일로 추가"
                aria-label="추가할 사람의 이메일"
              />
              <button
                type="submit"
                className={`${shared.pressable} ${styles.inviteButton}`}
                disabled={busy || !email.trim()}
              >
                추가
              </button>
            </form>
          ) : null}
        </section>

        {past.length > 0 ? (
          <section className={styles.section}>
            <h2 className={styles.heading}>지난 멤버</h2>
            <p className={shared.hint}>
              나간 사람도 소속돼 있던 기간의 타임라인에는 그대로 나옵니다.
            </p>
            <ul className={styles.list}>
              {past.map((member) => (
                <MemberRow
                  key={member.memberId}
                  member={member}
                  canManage={data.owner}
                  busy={busy}
                  editing={editingMember === member.memberId}
                  onEdit={() => {
                    setEditingMember(member.memberId)
                    setError(null)
                  }}
                  onCancel={() => setEditingMember(null)}
                  onSave={(body) => void run(() => updateMember(id, member.memberId, body))}
                />
              ))}
            </ul>
          </section>
        ) : null}

        {error && editingMember === null && !editingGroup ? (
          <p className={shared.error} role="alert">
            {error}
          </p>
        ) : null}

        <section className={styles.section}>
          {data.owner ? (
            <button
              type="button"
              className={`${shared.pressable} ${styles.danger}`}
              disabled={busy}
              onClick={() => {
                if (!window.confirm(`${data.name} 그룹을 지울까요?`)) return
                void run(() => deleteGroup(id), () => navigate('/groups'))
              }}
            >
              그룹 지우기
            </button>
          ) : (
            <button
              type="button"
              className={`${shared.pressable} ${styles.danger}`}
              disabled={busy}
              onClick={() => {
                if (!window.confirm(`${data.name} 그룹에서 나갈까요?`)) return
                void run(() => leaveGroup(id), () => navigate('/groups'))
              }}
            >
              그룹에서 나가기
            </button>
          )}
        </section>
      </div>
    </div>
  )
}

function MemberRow({
  member,
  canManage,
  busy,
  editing,
  onEdit,
  onCancel,
  onSave,
  onRemove,
}: {
  member: GroupMember
  canManage: boolean
  busy: boolean
  editing: boolean
  onEdit: () => void
  onCancel: () => void
  onSave: (body: { joinedOn: string; leftOn: string | null }) => void
  onRemove?: () => void
}) {
  const [joinedOn, setJoinedOn] = useState(member.joinedOn)
  const [leftOn, setLeftOn] = useState(member.leftOn ?? '')

  if (editing) {
    return (
      <li className={styles.editing}>
        <span className={styles.name}>{member.name}</span>
        <label className={styles.dateField}>
          시작
          <input
            className={shared.field}
            type="date"
            value={joinedOn}
            onChange={(event) => setJoinedOn(event.target.value)}
          />
        </label>
        <label className={styles.dateField}>
          종료 (비우면 소속 중)
          <input
            className={shared.field}
            type="date"
            value={leftOn}
            onChange={(event) => setLeftOn(event.target.value)}
          />
        </label>
        <div className={styles.editActions}>
          <button
            type="button"
            className={`${shared.pressable} ${styles.small}`}
            onClick={onCancel}
            disabled={busy}
          >
            취소
          </button>
          <button
            type="button"
            className={`${shared.pressable} ${styles.small}`}
            disabled={busy || !joinedOn}
            onClick={() => onSave({ joinedOn, leftOn: leftOn || null })}
          >
            저장
          </button>
        </div>
      </li>
    )
  }

  return (
    <li className={styles.row} data-inactive={member.active ? undefined : ''}>
      <span
        className={styles.dot}
        style={member.colorTag ? { background: member.colorTag } : undefined}
        aria-hidden="true"
      />
      <span className={styles.text}>
        <span className={styles.name}>
          {member.name}
          {member.self ? ' (나)' : ''}
          {member.role === 'OWNER' ? <span className={styles.tag}>소유자</span> : null}
          {/* 공유하지 않은 사람은 타임라인 줄이 비어 있다. 여기서 미리 보인다. */}
          {member.shared ? null : <span className={styles.tagMuted}>미공유</span>}
        </span>
        <span className={styles.meta}>
          {member.email} · {member.joinedOn}
          {member.leftOn ? ` ~ ${member.leftOn}` : ' ~'}
        </span>
      </span>
      {canManage ? (
        <span className={styles.actions}>
          <IconButton label={`${member.name} 기간 수정`} disabled={busy} onClick={onEdit}>
            ✎
          </IconButton>
          {onRemove && member.role !== 'OWNER' ? (
            <IconButton label={`${member.name} 내보내기`} disabled={busy} onClick={onRemove}>
              ×
            </IconButton>
          ) : null}
        </span>
      ) : null}
    </li>
  )
}
