import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppLayout } from '@/components/AppLayout'
import { ErrorPage } from '@/routes/ErrorPage'
import { MonthPage } from '@/routes/MonthPage'
import { WeekPage } from '@/routes/WeekPage'
import { DayPage } from '@/routes/DayPage'
import { GroupTimelinePage } from '@/routes/GroupTimelinePage'
import { SettingsPage } from '@/routes/SettingsPage'
import { LoginPage } from '@/routes/LoginPage'
import { today } from '@/lib/date'

/**
 * URL 라우팅.
 *
 * 이전 구현에는 라우팅이 아예 없었다. 화면 전환이 hidden 토글이라
 *  - 특정 날짜로 링크를 보낼 수 없었고 (공유 캘린더인데)
 *  - 안드로이드에서 뒤로가기를 누르면 모달이 닫히는 대신 앱이 종료됐다
 */
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage />, errorElement: <ErrorPage /> },
  {
    path: '/',
    element: <AppLayout />,
    errorElement: <ErrorPage />,
    children: [
      { index: true, element: <Navigate to="/month" replace /> },
      { path: 'month', element: <MonthPage /> },
      { path: 'month/:year/:month', element: <MonthPage /> },
      { path: 'week', element: <WeekPage /> },
      { path: 'week/:date', element: <WeekPage /> },
      { path: 'day/today', element: <Navigate to={`/day/${today()}`} replace /> },
      { path: 'day/:date', element: <DayPage /> },
      { path: 'groups/:groupId', element: <GroupTimelinePage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'settings/:section', element: <SettingsPage /> },
    ],
  },
])
