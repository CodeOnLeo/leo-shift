import { useState } from 'react'
import { Placeholder } from '@/components/Placeholder'
import { applyTheme, readTheme, type Theme } from '@/lib/theme'
import styles from './SettingsPage.module.css'

const THEMES: readonly { value: Theme; label: string }[] = [
  { value: 'system', label: '시스템' },
  { value: 'light', label: '밝게' },
  { value: 'dark', label: '어둡게' },
]

export function SettingsPage() {
  const [theme, setTheme] = useState<Theme>(readTheme)

  const change = (next: Theme) => {
    setTheme(next)
    applyTheme(next)
  }

  return (
    <Placeholder
      title="설정"
      description="개인 설정과 캘린더 관리"
      todo={[
        '근무 패턴 — 프리셋 고르기, 조 선택, 달력 미리보기',
        '근무 코드 관리',
        '내 캘린더 목록',
        '공유 관리 — 누구에게 어디까지 공개 중인지',
        '그룹 관리',
        '외부 캘린더 연동 · 내보내기 주소',
        '알림',
      ]}
    >
      <fieldset className={styles.field}>
        <legend className={styles.legend}>화면 테마</legend>
        <div className={styles.options} role="radiogroup" aria-label="화면 테마">
          {THEMES.map((option) => (
            <label key={option.value} className={styles.option}>
              <input
                type="radio"
                name="theme"
                value={option.value}
                checked={theme === option.value}
                onChange={() => change(option.value)}
              />
              {option.label}
            </label>
          ))}
        </div>
      </fieldset>
    </Placeholder>
  )
}
