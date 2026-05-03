/**
 * 语言颜色映射
 */
export const LANG_COLORS = {
  JavaScript: '#f1e05a', TypeScript: '#3178c6', Python: '#3572A5',
  Java: '#b07219', Go: '#00ADD8', Rust: '#dea584',
  'C++': '#f34b7d', C: '#555555', 'C#': '#178600',
  Ruby: '#701516', PHP: '#4F5D95', Swift: '#F05138',
  Kotlin: '#A97BFF', Dart: '#00B4AB', Shell: '#89e051',
  HTML: '#e34c26', CSS: '#563d7c', Vue: '#41b883',
  Scala: '#c22d40', Lua: '#000080', R: '#198CE7',
  Elixir: '#6e4a7e', Zig: '#ec915c', Haskell: '#5e5086',
  Nix: '#7e7eff', SCSS: '#c6538c', MDX: '#fcb32c',
  Svelte: '#ff3e00',
}

export function formatNumber(n) {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

export function formatRelative(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now - d
  const mins = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return mins + ' 分钟前'
  if (hours < 24) return hours + ' 小时前'
  if (days < 30) return days + ' 天前'
  const months = Math.floor(days / 30)
  return months + ' 个月前'
}
