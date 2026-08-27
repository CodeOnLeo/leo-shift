/**
 * API 클라이언트.
 *
 * 이전 구현에서 깨져 있던 것들을 구조로 막는다.
 *  - 갱신 후 재시도가 헤더를 잃어 POST/PUT이 415로 거부됐다 → 요청을 통째로 다시 만든다
 *  - 인증 실패 시 undefined를 반환해 호출부가 그대로 터졌다 → 항상 예외를 던진다
 *  - 401을 받아야만 갱신했다 → 동시 요청은 갱신 하나를 공유한다
 *
 * 토큰은 서버가 HttpOnly 쿠키로 내려주는 것을 전제로 한다. localStorage에 두면
 * XSS 한 번에 계정이 통째로 넘어간다. 그래서 이 파일에 토큰을 다루는 코드가 없다.
 */

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }

  get isUnauthorized() {
    return this.status === 401
  }

  get isForbidden() {
    return this.status === 403
  }

  get isConflict() {
    // 낙관적 잠금 충돌. 다른 사람이 같은 날을 먼저 고쳤다는 뜻이다.
    return this.status === 409
  }
}

type Options = {
  method?: string
  body?: unknown
  signal?: AbortSignal
}

/** 동시에 여러 요청이 401을 받아도 갱신은 한 번만 한다. */
let refreshing: Promise<boolean> | null = null

async function refreshSession(): Promise<boolean> {
  refreshing ??= fetch('/api/auth/refresh', {
    method: 'POST',
    credentials: 'include',
  })
    .then((res) => res.ok)
    .catch(() => false)
    .finally(() => {
      // 다음 401은 새로 갱신을 시도할 수 있어야 한다
      queueMicrotask(() => {
        refreshing = null
      })
    })
  return refreshing
}

async function send(path: string, options: Options): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  return fetch(path, {
    method: options.method ?? 'GET',
    headers,
    credentials: 'include',
    ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
    ...(options.signal ? { signal: options.signal } : {}),
  })
}

async function toError(res: Response): Promise<ApiError> {
  let code: string | null = null
  let message = `요청에 실패했습니다 (${res.status})`
  try {
    const body = (await res.json()) as { code?: string; message?: string }
    code = body.code ?? null
    if (body.message) message = body.message
  } catch {
    // 본문이 JSON이 아니면 상태 코드만으로 판단한다
  }
  return new ApiError(res.status, code, message)
}

export async function request<T>(path: string, options: Options = {}): Promise<T> {
  let res = await send(path, options)

  if (res.status === 401) {
    const renewed = await refreshSession()
    if (!renewed) throw new ApiError(401, 'unauthenticated', '로그인이 필요합니다')
    // 헤더와 본문을 포함해 요청을 통째로 다시 만든다
    res = await send(path, options)
  }

  if (!res.ok) throw await toError(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) =>
    request<T>(path, signal ? { signal } : {}),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
