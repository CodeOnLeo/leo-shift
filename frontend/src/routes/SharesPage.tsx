import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchGroups } from '@/api/group'
import { fetchShares, respondToShare, revokeShare, setShare } from '@/api/share'
import type { ShareLevel, ShareTarget } from '@/api/types'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './SharesPage.module.css'

const LEVEL_LABEL: Record<ShareLevel, string> = {
  WORK_ONLY: '근무만',
  FULL: '전체',
}

/**
 * 공유 관리.
 *
 * 개인 일정까지 담기는 캘린더라면 <b>"지금 누가 내 뭘 보고 있지?"</b>에 언제든
 * 답할 수 있어야 하고, 한 줄에서 바로 단계를 바꾸거나 끊을 수 있어야 한다.
 * 이전 구현에는 공유를 취소하는 기능 자체가 없었다.
 */
export function SharesPage() {
  const navigate = useNavigate()
  const [reloadKey, setReloadKey] = useState(0)

  const shares = useAsync((signal) => fetchShares(signal), [reloadKey])
  const groups = useAsync((signal) => fetchGroups(signal), [reloadKey])

  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      setReloadKey((key) => key + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : '처리하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  if (shares.status === 'error' || groups.status === 'error') {
    return (
      <p className={shared.error} role="alert">
        불러오지 못했습니다.
      </p>
    )
  }
  if (shares.status !== 'ready' || groups.status !== 'ready') {
    return <p className={shared.notice}>불러오는 중…</p>
  }

  const data = shares.data
  // 단계를 고를 수 있으려면 근무 말고 다른 캘린더가 있어야 한다.
  // 없으면 "근무만"과 "전체"가 같은 것을 공유하므로 고르게 하지 않는다.
  const levelMatters = data.personalCalendarCount > 0

  const sharedGroupIds = new Set(
    data.targets.filter((target) => target.targetType === 'GROUP').map((target) => target.targetId),
  )
  const unsharedGroups = groups.data.filter((group) => !sharedGroupIds.has(group.id))

  const submitEmail = (event: FormEvent) => {
    event.preventDefault()
    const value = email.trim()
    if (!value) return
    void run(async () => {
      await setShare({ targetType: 'USER', email: value }, 'FULL')
      setEmail('')
    })
  }

  return (
    <div className={styles.page}>
      <PageHeader
        title="공유 관리"
        left={<IconButton label="설정으로" onClick={() => navigate('/settings')}>←</IconButton>}
      />

      <div className={styles.body}>
        {data.incoming.length > 0 ? (
          <section className={styles.section}>
            <h2 className={styles.heading}>받은 공유</h2>
            <ul className={styles.list}>
              {data.incoming.map((invite) => (
                <li key={invite.id} className={styles.row}>
                  <span className={styles.text}>
                    <span className={styles.name}>{invite.ownerName}</span>
                    <span className={styles.meta}>
                      {invite.calendarName}
                      {invite.ownerEmail ? ` · ${invite.ownerEmail}` : ''}
                    </span>
                  </span>
                  <span className={styles.actions}>
                    <button
                      type="button"
                      className={`${shared.pressable} ${styles.small}`}
                      disabled={busy}
                      onClick={() => void run(() => respondToShare(invite.id, false))}
                    >
                      거절
                    </button>
                    <button
                      type="button"
                      className={`${shared.pressable} ${styles.small} ${styles.accept}`}
                      disabled={busy}
                      onClick={() => void run(() => respondToShare(invite.id, true))}
                    >
                      수락
                    </button>
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ) : null}

        <section className={styles.section}>
          <h2 className={styles.heading}>내 캘린더를 보는 사람</h2>

          {data.targets.length === 0 ? (
            <p className={shared.hint}>아직 아무에게도 공유하지 않았습니다.</p>
          ) : (
            <ul className={styles.list}>
              {data.targets.map((target) => (
                <TargetRow
                  key={`${target.targetType}-${target.targetId}`}
                  target={target}
                  levelMatters={levelMatters}
                  busy={busy}
                  onLevel={(level) =>
                    void run(() =>
                      setShare(
                        target.targetType === 'GROUP'
                          ? { targetType: 'GROUP', groupId: target.targetId }
                          : { targetType: 'USER', email: target.email ?? '' },
                        level,
                      ),
                    )
                  }
                  onRevoke={() => {
                    if (!window.confirm(`${target.name}에 대한 공유를 끊을까요?`)) return
                    void run(() => revokeShare(target.targetType, target.targetId))
                  }}
                />
              ))}
            </ul>
          )}

          {levelMatters ? null : (
            <p className={shared.caption}>
              지금은 근무 캘린더만 있어서 어느 단계로 공유해도 근무 일정만 나갑니다.
            </p>
          )}
        </section>

        <section className={styles.section}>
          <h2 className={styles.heading}>새로 공유</h2>

          {unsharedGroups.length > 0 ? (
            <ul className={styles.list}>
              {unsharedGroups.map((group) => (
                <li key={group.id} className={styles.row}>
                  <span className={styles.text}>
                    <span className={styles.name}>{group.name}</span>
                    <span className={styles.meta}>그룹 · {group.memberCount}명</span>
                  </span>
                  <button
                    type="button"
                    className={`${shared.pressable} ${styles.small}`}
                    disabled={busy}
                    onClick={() =>
                      void run(() =>
                        setShare({ targetType: 'GROUP', groupId: group.id }, 'WORK_ONLY'),
                      )
                    }
                  >
                    공유
                  </button>
                </li>
              ))}
            </ul>
          ) : null}

          <form className={styles.invite} onSubmit={submitEmail}>
            <input
              className={shared.field}
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="이메일로 개인 공유"
              aria-label="공유할 사람의 이메일"
            />
            <button
              type="submit"
              className={`${shared.pressable} ${styles.inviteButton}`}
              disabled={busy || !email.trim()}
            >
              공유
            </button>
          </form>
          <p className={shared.hint}>
            개인 공유는 상대가 수락해야 유효합니다. 그룹 공유는 바로 적용됩니다.
          </p>
        </section>

        {error ? (
          <p className={shared.error} role="alert">
            {error}
          </p>
        ) : null}
      </div>
    </div>
  )
}

function TargetRow({
  target,
  levelMatters,
  busy,
  onLevel,
  onRevoke,
}: {
  target: ShareTarget
  levelMatters: boolean
  busy: boolean
  onLevel: (level: ShareLevel) => void
  onRevoke: () => void
}) {
  return (
    <li className={styles.row}>
      <span className={styles.text}>
        <span className={styles.name}>
          {target.name}
          {target.pending ? <span className={styles.tagMuted}>대기 중</span> : null}
        </span>
        <span className={styles.meta}>
          {target.targetType === 'GROUP'
            ? `그룹 · ${target.memberCount}명`
            : `개인 공유${target.email ? ` · ${target.email}` : ''}`}
        </span>
      </span>

      {levelMatters ? (
        <select
          className={`${shared.field} ${styles.level}`}
          value={target.level}
          disabled={busy}
          aria-label={`${target.name} 공개 단계`}
          onChange={(event) => onLevel(event.target.value as ShareLevel)}
        >
          <option value="WORK_ONLY">{LEVEL_LABEL.WORK_ONLY}</option>
          <option value="FULL">{LEVEL_LABEL.FULL}</option>
        </select>
      ) : (
        <span className={styles.levelText}>{LEVEL_LABEL[target.level]}</span>
      )}

      <IconButton label={`${target.name} 공유 끊기`} disabled={busy} onClick={onRevoke}>
        ×
      </IconButton>
    </li>
  )
}
