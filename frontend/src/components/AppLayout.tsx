import { NavLink, Outlet } from 'react-router-dom'
import { today } from '@/lib/date'
import styles from './AppLayout.module.css'

const TABS = [
  { to: '/month', label: '월' },
  { to: '/week', label: '주' },
  { to: `/day/${today()}`, label: '오늘' },
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
