export type Theme = 'system' | 'light' | 'dark'

const STORAGE_KEY = 'leo-shift.theme'

export function readTheme(): Theme {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'light' || stored === 'dark' ? stored : 'system'
}

export function applyTheme(theme: Theme): void {
  const root = document.documentElement
  if (theme === 'system') {
    root.removeAttribute('data-theme')
    localStorage.removeItem(STORAGE_KEY)
  } else {
    root.setAttribute('data-theme', theme)
    localStorage.setItem(STORAGE_KEY, theme)
  }
}
