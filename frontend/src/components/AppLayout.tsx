import { NavLink, Outlet } from 'react-router-dom'
import { today } from '@/lib/date'
import styles from './AppLayout.module.css'

/**
 * 그룹이 탭에 있는 이유: 프로젝트 인원의 부재 현황을 보는 것이 이 앱을 만든
 * 이유이고, 설정 안에 묻어두면 아무도 열지 않는다.
 */
const TABS = [
  { to: '/month', label: '월' },
  { to: '/week', label: '주' },
  { to: `/day/${today()}`, label: '오늘' },
  { to: '/groups', label: '그룹' },
  { to: '/settings', label: '설정' },
] as const

export function AppLayout() {
  return (
    <div className={styles.shell}>
      <main className={styles.content}>
        <Outlet />
      </main>

      <nav className={styles.nav} aria-label="주요 화면">
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) => (isActive ? `${styles.tab} ${styles.active}` : styles.tab)}
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
