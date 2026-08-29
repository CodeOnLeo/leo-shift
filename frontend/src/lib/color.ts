/**
 * 배경색 위에서 읽히는 글자색.
 *
 * 흰 글자에 그림자를 깔면 밝은 배경에서 흐려진다. 사용자가 일정 타입 색을
 * 직접 정할 수 있으므로(연한 민트 같은 색도 가능), 대비를 계산해서 고른다.
 * 한 곳에 두어 어느 화면에서든 같은 코드가 같게 읽히게 한다.
 */
export function contrastText(hex: string): string {
  const value = hex.replace('#', '')
  if (value.length !== 6) return '#ffffff'

  const channel = (c: number) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4)
  const [r, g, b] = [0, 2, 4].map((i) => channel(parseInt(value.slice(i, i + 2), 16) / 255))
  const luminance = 0.2126 * r! + 0.7152 * g! + 0.0722 * b!

  // 흰 글자와 검은 글자 중 대비가 큰 쪽 (WCAG 상대 휘도 기준)
  const withWhite = 1.05 / (luminance + 0.05)
  const withBlack = (luminance + 0.05) / 0.05
  return withWhite >= withBlack ? '#ffffff' : '#0f172a'
}
