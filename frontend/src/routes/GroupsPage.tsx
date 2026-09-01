import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createGroup, fetchGroups, type SaveGroupBody } from '@/api/group'
import { GROUP_KINDS } from '@/components/GroupForm'
import { GroupForm } from '@/components/GroupForm'
import { PageHeader } from '@/components/ui/PageHeader'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './GroupsPage.module.css'

const KIND_LABEL = new Map(GROUP_KINDS.map((kind) => [kind.value, kind.label]))

/**
 * 그룹 목록.
 *
 * 그룹은 데이터를 담지 않는다. "이 사람들의 캘린더를 겹쳐서 보여줘"라는 화면의
 * 정의일 뿐이라, 그룹을 만드는 것과 일정이 보이는 것은 별개다. 그 사실을
 * 화면에서도 계속 말해줘야 한다 — 아니면 "그룹을 만들었는데 왜 아무것도 안 보이지?"가 된다.
 */
export function GroupsPage() {
  const navigate = useNavigate()
  const [reloadKey, setReloadKey] = useState(0)
  const groups = useAsync((signal) => fetchGroups(signal), [reloadKey])

  const [creating, setCreating] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function create(body: SaveGroupBody) {
    setBusy(true)
    setError(null)
    try {
      const group = await createGroup(body)
      setCreating(false)
      // 바로 멤버 관리로 보낸다. 빈 그룹만 있는 목록으로 돌아가면 다음에 뭘 할지 알 수 없다.
      navigate(`/groups/${group.id}/members`)
    } catch (e) {
      setError(e instanceof Error ? e.message : '만들지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  if (groups.status === 'error') {
    return (
      <p className={shared.error} role="alert">
        불러오지 못했습니다.
      </p>
    )
  }
  if (groups.status !== 'ready') {
    return <p className={shared.notice}>불러오는 중…</p>
  }

  return (
    <div className={styles.page}>
      <PageHeader title="그룹" />

      <div className={styles.body}>
        {groups.data.length === 0 && !creating ? (
          <p className={shared.hint}>
            프로젝트·직장·가족처럼 일정을 겹쳐 보고 싶은 사람들을 묶습니다.
            그룹에 넣는 것만으로는 서로의 일정이 보이지 않고, 각자 자기 캘린더를
            그 그룹에 공유해야 보입니다.
          </p>
        ) : null}

        <ul className={styles.list}>
          {groups.data.map((group) => (
            <li key={group.id}>
              <Link className={`${shared.pressable} ${styles.item}`} to={`/groups/${group.id}`}>
                <span
                  className={styles.bar}
                  style={group.color ? { background: group.color } : undefined}
                  aria-hidden="true"
                />
                <span className={styles.text}>
                  <span className={styles.name}>{group.name}</span>
                  <span className={styles.meta}>
                    {KIND_LABEL.get(group.kind) ?? group.kind} · {group.memberCount}명
                    {group.owner ? ' · 내가 만든 그룹' : ''}
                  </span>
                </span>
                <span className={styles.chevron} aria-hidden="true">
                  ›
                </span>
              </Link>
            </li>
          ))}
        </ul>

        {creating ? (
          <GroupForm
            group={null}
            busy={busy}
            error={error}
            onSubmit={create}
            onCancel={() => {
              setCreating(false)
              setError(null)
            }}
          />
        ) : (
          <button
            type="button"
            className={`${shared.pressable} ${styles.add}`}
            onClick={() => {
              setCreating(true)
              setError(null)
              setReloadKey((key) => key)
            }}
          >
            + 그룹 만들기
          </button>
        )}
      </div>
    </div>
  )
}
