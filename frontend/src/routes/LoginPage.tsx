import { Placeholder } from '@/components/Placeholder'

export function LoginPage() {
  return (
    <div style={{ padding: 'var(--space-4)' }}>
      <Placeholder
        title="로그인"
        description="초대받은 사람만 가입할 수 있다."
        todo={[
          '구글 로그인 — 리프레시 토큰까지 정상 발급 (이전 구현은 24시간마다 강제 로그아웃됐다)',
          '이메일 로그인',
          '초대 코드로 가입',
          '토큰은 HttpOnly 쿠키로 — localStorage에 두지 않는다',
        ]}
      />
    </div>
  )
}
