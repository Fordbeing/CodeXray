export const statusMap = {
  PENDING: { text: '等待中', type: 'info' },
  CLONING: { text: '克隆中', type: 'warning' },
  ANALYZING: { text: '分析中', type: 'warning' },
  COMPLETED: { text: '已完成', type: 'success' },
  FAILED: { text: '已失败', type: 'danger' }
}

export function getStatusInfo(status) {
  return statusMap[status] || { text: status, type: 'info' }
}

export function formatTime(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function formatDate(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function shortenUrl(url) {
  if (!url) return ''
  return url.replace(/^https?:\/\/github\.com\//, '')
}
