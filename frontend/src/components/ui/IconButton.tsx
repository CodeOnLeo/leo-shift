import type { ButtonHTMLAttributes } from 'react'
import shared from '@/styles/shared.module.css'

/** 테두리 없는 아이콘 버튼. 라벨은 필수다 — 화살표 글리프만으로는 읽히지 않는다. */
export function IconButton({
  label,
  children,
  className,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      className={className ? `${shared.iconButton} ${className}` : shared.iconButton}
      {...rest}
    >
      {children}
    </button>
  )
}
