import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchCalendars } from '@/api/calendar'
import {
  createExternalSource,
  createFeedToken,
  deleteExternalSource,
  fetchExternalSources,
  fetchFeedTokens,
  revokeFeedToken,
  syncExternalSource,
  updateExternalSource,
  type SaveExternalSourceBody,
} from '@/api/external'
import type { ExternalDisplayMode, ExternalSource, FeedToken } from '@/api/types'
import { ColorPicker } from '@/components/ColorPicker'
import { IconButton } from '@/components/ui/IconButton'
import { PageHeader } from '@/components/ui/PageHeader'
import { useAsync } from '@/lib/useAsync'
import shared from '@/styles/shared.module.css'
import styles from './ExternalPage.module.css'

const DISPLAY_MODES: readonly { value: ExternalDisplayMode; label: string; hint: string }[] = [
  { value: 'BADGE', label: '출처만', hint: '달력에 캘린더 이름만 뜹니다' },
  { value: 'INLINE', label: '제목까지', hint: '일정 제목이 그대로 뜹니다' },
  { value: 'HIDDEN', label: '숨김', hint: '구독은 두되 달력에 그리지 않습니다' },
]

/**
 * 다른 캘린더 연동.
 *
 * 가져오기와 내보내기를 한 화면에 둔다. 사용자에게는 "구글 캘린더와 이어 붙이는
 * 일" 하나라, 설정 항목이 둘로 나뉘면 어느 쪽을 눌러야 하는지 매번 헷갈린다.
 */
export function ExternalPage() {
  const navigate = useNavigate()
  const [reloadKey, setReloadKey] = useState(0)

  const calendars = useAsync((signal) => fetchCalendars(signal), [])
  const mine = calendars.status === 'ready' ? calendars.data.filter((c) => c.mine) : []

  // 근무 캘린더가 내보내기의 주인공이다. 없으면 기본 캘린더, 그것도 없으면 첫 번째.
  const [calendarId, setCalendarId] = useState<number | null>(null)
  const activeId =
    calendarId ??
    mine.find((calendar) => calendar.kind === 'WORK')?.id ??
    mine.find((calendar) => calendar.isDefault)?.id ??
    mine[0]?.id ??
    null

  const sources = useAsync(
    async (signal) => (activeId === null ? [] : fetchExternalSources(activeId, signal)),
    [activeId, reloadKey],
  )
  const tokens = useAsync(
    async (signal) => (activeId === null ? [] : fetchFeedTokens(activeId, signal)),
    [activeId, reloadKey],
  )

  const [editing, setEditing] = useState<number | 'new' | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await action()
      setEditing(null)
      setReloadKey((key) => key + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  /**
   * 동기화 결과를 그 자리에서 알린다.
   *
   * 서버는 피드가 실패해도 200을 준다 — 실패를 예외로 올리면 구독이 저장됐는지
   * 아닌지가 화면에서 흐려진다. 대신 결과 안의 error를 여기서 읽는다.
   */
  async function runSync(action: () => Promise<{ imported: number; error: string | null }>) {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const result = await action()
      setEditing(null)
      setReloadKey((key) => key + 1)
      if (result.error) setError(result.error)
      else setNotice(`일정 ${result.imported}개를 가져왔습니다.`)
    } catch (e) {
      setError(e instanceof Error ? e.message : '가져오지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  if (calendars.status === 'error') {
    return (
      <p className={shared.error} role="alert">
        불러오지 못했습니다.
      </p>
    )
  }
  if (calendars.status !== 'ready') {
    return <p className={shared.notice}>불러오는 중…</p>
  }

  return (
    <div className={styles.page}>
      <PageHeader
        title="다른 캘린더 연동"
        left={<IconButton label="설정으로" onClick={() => navigate('/settings')}>←</IconButton>}
      />

      <div className={styles.body}>
        {mine.length > 1 ? (
          <label className={styles.label}>
            어느 캘린더
            <select
              className={shared.field}
              value={activeId ?? ''}
              onChange={(e) => {
                setCalendarId(Number(e.target.value))
                setEditing(null)
                setError(null)
                setNotice(null)
              }}
            >
              {mine.map((calendar) => (
                <option key={calendar.id} value={calendar.id}>
                  {calendar.name}
                  {calendar.kind === 'WORK' ? ' (근무)' : ''}
                </option>
              ))}
            </select>
          </label>
        ) : null}

        {notice ? <p className={shared.notice} role="status">{notice}</p> : null}
        {error && editing === null ? (
          <p className={shared.error} role="alert">
            {error}
          </p>
        ) : null}

        {activeId === null ? (
          <p className={shared.hint}>먼저 캘린더를 만들어 주세요.</p>
        ) : (
          <>
            <ExportSection
              tokens={tokens.status === 'ready' ? tokens.data : []}
              loading={tokens.status === 'loading'}
              busy={busy}
              onCreate={(visibility) => void run(() => createFeedToken(activeId, visibility))}
              onRevoke={(tokenId) => {
                if (
                  !window.confirm(
                    '이 주소를 폐기할까요? 이 주소로 구독 중인 앱에서 근무표가 사라집니다.',
                  )
                )
                  return
                void run(() => revokeFeedToken(tokenId))
              }}
            />

            <section className={styles.section}>
              <h2 className={styles.heading}>가져오기</h2>
              <p className={shared.hint}>
                구글 캘린더의 <strong>비공개 주소(ICS)</strong>를 넣으면 그 일정이 이 달력에
                함께 보입니다. 가져온 일정은 여기서 고칠 수 없습니다.
              </p>

              {sources.status === 'loading' ? (
                <p className={shared.notice}>불러오는 중…</p>
              ) : (
                <ul className={styles.list}>
                  {(sources.status === 'ready' ? sources.data : []).map((source) => (
                    <li key={source.id}>
                      {editing === source.id ? (
                        <SourceForm
                          source={source}
                          busy={busy}
                          error={error}
                          onSubmit={(body) =>
                            void runSync(() => updateExternalSource(source.id, body))
                          }
                          onCancel={() => {
                            setEditing(null)
                            setError(null)
                          }}
                        />
                      ) : (
                        <SourceRow
                          source={source}
                          busy={busy}
                          onEdit={() => {
                            setEditing(source.id)
                            setError(null)
                            setNotice(null)
                          }}
                          onSync={() => void runSync(() => syncExternalSource(source.id))}
                          onDelete={() => {
                            if (!window.confirm(`${source.name} 구독을 지울까요?`)) return
                            void run(() => deleteExternalSource(source.id))
                          }}
                        />
                      )}
                    </li>
                  ))}
                </ul>
              )}

              {editing === 'new' ? (
                <SourceForm
                  source={null}
                  busy={busy}
                  error={error}
                  onSubmit={(body) => void runSync(() => createExternalSource(activeId, body))}
                  onCancel={() => {
                    setEditing(null)
                    setError(null)
                  }}
                />
              ) : (
                <button
                  type="button"
                  className={`${shared.pressable} ${styles.add}`}
                  disabled={busy}
                  onClick={() => {
                    setEditing('new')
                    setError(null)
                    setNotice(null)
                  }}
                >
                  + 캘린더 구독
                </button>
              )}
            </section>
          </>
        )}
      </div>
    </div>
  )
}

/**
 * 내보내기용 구독 주소.
 *
 * 주소를 아는 사람은 <b>누구나</b> 이 캘린더를 볼 수 있다. 로그인도 필요 없다.
 * 그 사실을 만들기 전에 알려야 한다.
 */
function ExportSection({
  tokens,
  loading,
  busy,
  onCreate,
  onRevoke,
}: {
  tokens: readonly FeedToken[]
  loading: boolean
  busy: boolean
  onCreate: (visibility: 'FULL' | 'BUSY_ONLY') => void
  onRevoke: (tokenId: number) => void
}) {
  const [visibility, setVisibility] = useState<'FULL' | 'BUSY_ONLY'>('FULL')
  const [copied, setCopied] = useState<number | null>(null)

  async function copy(token: FeedToken) {
    try {
      await navigator.clipboard.writeText(token.url)
      setCopied(token.id)
      window.setTimeout(() => setCopied(null), 2000)
    } catch {
      // 클립보드를 못 쓰는 브라우저가 있다. 주소는 화면에 이미 보이므로 그대로 둔다.
    }
  }

  return (
    <section className={styles.section}>
      <h2 className={styles.heading}>내보내기</h2>
      <p className={shared.hint}>
        아래 주소를 구글 캘린더의 <strong>URL로 추가</strong>에 넣으면 근무표가 거기서도
        보입니다. 근무표는 이 앱에서 만들고 보기는 익숙한 앱에서 하면 됩니다.
      </p>

      {loading ? <p className={shared.notice}>불러오는 중…</p> : null}

      <ul className={styles.list}>
        {tokens.map((token) => (
          <li key={token.id} className={styles.token}>
            <div className={styles.tokenHead}>
              <span className={styles.tag}>
                {token.visibility === 'FULL' ? '전체' : '바쁨만'}
              </span>
              <span className={styles.meta}>
                {token.lastUsedAt ? '구독 중' : '아직 아무도 읽지 않음'}
              </span>
              <span className={styles.actions}>
                <IconButton
                  label="주소 복사"
                  disabled={busy}
                  onClick={() => void copy(token)}
                >
                  {copied === token.id ? '✓' : '⧉'}
                </IconButton>
                <IconButton label="주소 폐기" disabled={busy} onClick={() => onRevoke(token.id)}>
                  ×
                </IconButton>
              </span>
            </div>
            {/* 길어서 반드시 줄바꿈이 돼야 한다. 잘리면 손으로 옮겨 적을 수도 없다. */}
            <code className={styles.url}>{token.url}</code>
          </li>
        ))}
      </ul>

      <div className={styles.createRow}>
        <select
          className={shared.field}
          value={visibility}
          disabled={busy}
          onChange={(e) => setVisibility(e.target.value as 'FULL' | 'BUSY_ONLY')}
          aria-label="공개 단계"
        >
          <option value="FULL">전체 — 근무 이름과 일정 제목까지</option>
          <option value="BUSY_ONLY">바쁨만 — 시간이 차 있다는 것만</option>
        </select>
        <button
          type="button"
          className={`${shared.pressable} ${styles.add}`}
          disabled={busy}
          onClick={() => onCreate(visibility)}
        >
          + 주소 만들기
        </button>
      </div>

      <p className={shared.caption}>
        이 주소를 아는 사람은 로그인 없이 캘린더를 볼 수 있습니다. 남에게 넘어갔다면
        폐기하고 새로 만드세요.
      </p>
    </section>
  )
}

function SourceRow({
  source,
  busy,
  onEdit,
  onSync,
  onDelete,
}: {
  source: ExternalSource
  busy: boolean
  onEdit: () => void
  onSync: () => void
  onDelete: () => void
}) {
  return (
    <div className={styles.row}>
      <span
        className={styles.bar}
        style={source.color ? { background: source.color } : undefined}
        aria-hidden="true"
      />
      <span className={styles.text}>
        <span className={styles.name}>
          {source.name}
          {source.active ? null : <span className={styles.tag}>중지</span>}
          {source.displayMode === 'HIDDEN' ? <span className={styles.tag}>숨김</span> : null}
        </span>
        <span className={styles.meta}>
          {source.lastError
            ? source.lastError
            : source.lastSyncedAt
              ? `일정 ${source.eventCount}개 · ${source.syncIntervalMinutes}분마다`
              : '아직 가져오지 않음'}
        </span>
      </span>
      <span className={styles.actions}>
        <IconButton label={`${source.name} 지금 가져오기`} disabled={busy} onClick={onSync}>
          ↻
        </IconButton>
        <IconButton label={`${source.name} 편집`} disabled={busy} onClick={onEdit}>
          ✎
        </IconButton>
        <IconButton label={`${source.name} 구독 취소`} disabled={busy} onClick={onDelete}>
          ×
        </IconButton>
      </span>
    </div>
  )
}

function SourceForm({
  source,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  source: ExternalSource | null
  busy: boolean
  error: string | null
  onSubmit: (body: SaveExternalSourceBody) => void
  onCancel: () => void
}) {
  const [name, setName] = useState(source?.name ?? '')
  const [feedUrl, setFeedUrl] = useState(source?.feedUrl ?? '')
  const [color, setColor] = useState(source?.color ?? '#0EA5E9')
  const [displayMode, setDisplayMode] = useState<ExternalDisplayMode>(source?.displayMode ?? 'BADGE')
  const [active, setActive] = useState(source?.active ?? true)
  const [interval, setInterval] = useState(String(source?.syncIntervalMinutes ?? 360))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSubmit({
      name: name.trim(),
      feedUrl: feedUrl.trim(),
      color,
      displayMode,
      active,
      syncIntervalMinutes: Number(interval) || 360,
    })
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      <label className={styles.label}>
        이름
        <input
          className={shared.field}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="구글 캘린더"
          maxLength={100}
          required
          autoFocus
        />
      </label>

      <label className={styles.label}>
        구독 주소 (ICS)
        <input
          className={shared.field}
          value={feedUrl}
          onChange={(e) => setFeedUrl(e.target.value)}
          placeholder="https://calendar.google.com/calendar/ical/.../basic.ics"
          inputMode="url"
          maxLength={2000}
          required
        />
        <span className={shared.caption}>
          구글 캘린더 → 설정 → 내 캘린더 설정 → <strong>비공개 주소의 iCal 형식</strong>
        </span>
      </label>

      <div className={styles.label}>
        색
        <ColorPicker value={color} onChange={setColor} disabled={busy} />
      </div>

      <fieldset className={styles.field}>
        <legend className={styles.legend}>달력에 어떻게</legend>
        <div className={styles.options} role="radiogroup" aria-label="달력에 어떻게">
          {DISPLAY_MODES.map((option) => (
            <label key={option.value} className={styles.option} title={option.hint}>
              <input
                type="radio"
                name="displayMode"
                value={option.value}
                checked={displayMode === option.value}
                onChange={() => setDisplayMode(option.value)}
              />
              {option.label}
            </label>
          ))}
        </div>
      </fieldset>

      <label className={styles.label}>
        얼마나 자주 가져올까요
        <select
          className={shared.field}
          value={interval}
          onChange={(e) => setInterval(e.target.value)}
        >
          <option value="60">1시간마다</option>
          <option value="360">6시간마다</option>
          <option value="720">12시간마다</option>
          <option value="1440">하루에 한 번</option>
        </select>
      </label>

      <label className={styles.check}>
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
        구독 유지
      </label>

      {error ? (
        <p className={shared.error} role="alert">
          {error}
        </p>
      ) : null}

      <div className={styles.formActions}>
        <button
          type="button"
          className={`${shared.pressable} ${styles.cancel}`}
          onClick={onCancel}
          disabled={busy}
        >
          취소
        </button>
        <button
          type="submit"
          className={shared.primaryButton}
          disabled={busy || !name.trim() || !feedUrl.trim()}
        >
          {busy ? '가져오는 중…' : '저장하고 가져오기'}
        </button>
      </div>
    </form>
  )
}
