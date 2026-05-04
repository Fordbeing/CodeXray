import request from './request'

export function analyzeRepo(repoUrl) {
  return request.post('/analysis/analyze', { repoUrl })
}

export function uploadAndAnalyze(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/analysis/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export function getAnalysisResult(taskId) {
  return request.get(`/analysis/${taskId}`)
}

export function listTasks(limit = 20) {
  return request.get('/analysis/list', { params: { limit } })
}

export function deleteTask(taskId) {
  return request.delete(`/analysis/${taskId}`)
}

export function previewRepo(repoUrl) {
  return request.get('/analysis/preview', { params: { repoUrl } })
}

export function getFileTree(taskId) {
  return request.get(`/analysis/${taskId}/tree`)
}

export function getFileContent(taskId, path) {
  return request.get(`/analysis/${taskId}/file`, { params: { path } })
}

export function codeSearch(taskId, q, limit = 10) {
  return request.get(`/analysis/${taskId}/search`, { params: { q, limit } })
}

export function getQuestions(taskId) {
  return request.get(`/analysis/${taskId}/questions`)
}

export function getNotifications(limit = 10) {
  return request.get('/analysis/notifications', { params: { limit } })
}

/**
 * 订阅分析进度 SSE 流。
 * 返回 { event, data } 异步迭代器。
 */
export async function* subscribeToAnalysis(taskId) {
  const token = localStorage.getItem('codexray_token')
  const baseUrl = request.defaults.baseURL || '/api'
  const url = `${baseUrl}/analysis/${taskId}/stream`
  const headers = {}
  if (token) headers['Authorization'] = `Bearer ${token}`

  const response = await fetch(url, { headers })
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    let currentEvent = 'message'
    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        yield { event: currentEvent, data: tryParse(data) }
        currentEvent = 'message'
      }
    }
  }
}

function tryParse(str) {
  try { return JSON.parse(str) } catch { return str }
}

/** 获取架构图数据 */
export function getArchitectureGraph(taskId) {
  return request.get(`/analysis/${taskId}/graph`)
}

/** 获取 AI 代码导览 */
export function getCodeTour(taskId) {
  return request.get(`/analysis/${taskId}/tour`)
}

/** 获取用户分析统计 */
export function getUserStats() {
  return request.get('/analysis/stats')
}

/** 创建分享链接 */
export function createShareLink(taskId, { password, expiresInDays } = {}) {
  return request.post(`/analysis/${taskId}/share`, { password, expiresInDays })
}

/** 查看分享报告 */
export function getSharedReport(shareToken, password) {
  return request.get(`/analysis/shared/${shareToken}`, { params: { password } })
}

/** 列出我的分享 */
export function listShares() {
  return request.get('/analysis/shares')
}

/** 撤销分享 */
export function revokeShare(shareToken) {
  return request.delete(`/analysis/shares/${shareToken}`)
}
