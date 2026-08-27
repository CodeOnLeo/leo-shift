import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { router } from './routes/router'
import { applyTheme, readTheme } from './lib/theme'
import './styles/global.css'

applyTheme(readTheme())

const container = document.getElementById('root')
if (!container) throw new Error('#root를 찾을 수 없습니다')

createRoot(container).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
