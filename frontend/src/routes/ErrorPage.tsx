import { isRouteErrorResponse, Link, useRouteError } from 'react-router-dom'

export function ErrorPage() {
  const error = useRouteError()

  const message = isRouteErrorResponse(error)
    ? error.status === 404
      ? '없는 주소입니다.'
      : `요청을 처리하지 못했습니다 (${error.status})`
    : '예상하지 못한 오류가 발생했습니다.'

  return (
    <div style={{ padding: 'var(--space-6)' }}>
      <h1 style={{ fontSize: 20 }}>문제가 생겼습니다</h1>
      <p style={{ color: 'var(--text-muted)' }}>{message}</p>
      <Link to="/month">달력으로 돌아가기</Link>
    </div>
  )
}
