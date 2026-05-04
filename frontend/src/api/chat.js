import request from './request'

/** 发送问答消息（同步） */
export function chatWithRepo(repoUrl, question, sessionId, taskId) {
  return request.post('/chat', { repoUrl, question, sessionId, taskId })
}

/** 发送问答消息（异步）— 返回 pollId */
export function sendChatAsync(repoUrl, question, sessionId, taskId) {
  return request.post('/chat/send', { repoUrl, question, sessionId, taskId })
}

/** 轮询异步结果 */
export function getChatResult(pollId) {
  return request.get(`/chat/result/${pollId}`)
}

/**
 * SSE 流式问答 — 返回 { sessionId, stream }
 * stream 是一个 AsyncIterable，yield { type: 'session'|'token'|'done'|'error', data }
 */
export async function sendChatStream(repoUrl, question, sessionId, taskId) {
  const token = localStorage.getItem('codexray_token')
  const params = new URLSearchParams({ question })
  if (sessionId) params.set('sessionId', sessionId)
  if (repoUrl) params.set('repoUrl', repoUrl)
  if (taskId) params.set('taskId', taskId)

  const baseUrl = request.defaults?.baseURL || '/api'
  const resp = await fetch(`${baseUrl}/chat/stream?${params}`, {
    headers: token ? { 'Authorization': `Bearer ${token}` } : {},
  })
  if (!resp.ok) throw new Error(`SSE 请求失败: ${resp.status}`)

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  async function* stream() {
    let eventType = null
    let eventData = null
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const line of lines) {
        const trimmed = line.replace(/\r$/, '')
        if (trimmed === '') {
          if (eventType !== null && eventData !== null) {
            yield { type: eventType, data: eventData }
          }
          eventType = null
          eventData = null
        } else if (trimmed.startsWith('event:')) {
          if (eventType !== null && eventData !== null) {
            yield { type: eventType, data: eventData }
          }
          eventType = trimmed.slice(6).trimStart()
          eventData = null
        } else if (trimmed.startsWith('data:')) {
          const chunk = trimmed.slice(5).replace(/^ /, '')
          eventData = eventData !== null ? eventData + '\n' + chunk : chunk
        }
      }
    }
  }

  return { stream: stream(), reader }
}

/** 获取会话历史 */
export function getChatHistory(sessionId) {
  return request.get(`/chat/history/${sessionId}`)
}

/** 获取所有会话列表 */
export function listChatSessions(repoUrl) {
  return request.get('/chat/sessions', { params: { repoUrl } })
}

/** 创建新会话 */
export function createChatSession(repoUrl, taskId) {
  return request.post('/chat/session', { repoUrl, taskId })
}

/** 删除会话 */
export function deleteChatSession(sessionId) {
  return request.delete(`/chat/session/${sessionId}`)
}

/** 导出会话为 Markdown */
export function exportChatSession(sessionId) {
  return request.get(`/chat/session/${sessionId}/export`, { params: { format: 'md' } })
}

/**
 * 跨仓库 SSE 流式问答。
 * 返回 { stream: AsyncIterable, reader }
 */
export async function sendCrossRepoChatStream(question, taskIds) {
  const token = localStorage.getItem('codexray_token')
  const params = new URLSearchParams({ question })
  if (taskIds && taskIds.length > 0) params.set('taskIds', taskIds.join(','))

  const baseUrl = request.defaults?.baseURL || '/api'
  const resp = await fetch(`${baseUrl}/chat/cross-repo/stream?${params}`, {
    headers: token ? { 'Authorization': `Bearer ${token}` } : {},
  })
  if (!resp.ok) throw new Error(`SSE 请求失败: ${resp.status}`)

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  async function* stream() {
    let eventType = null
    let eventData = null
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const line of lines) {
        const trimmed = line.replace(/\r$/, '')
        if (trimmed === '') {
          if (eventType !== null && eventData !== null) {
            yield { type: eventType, data: eventData }
          }
          eventType = null
          eventData = null
        } else if (trimmed.startsWith('event:')) {
          if (eventType !== null && eventData !== null) {
            yield { type: eventType, data: eventData }
          }
          eventType = trimmed.slice(6).trimStart()
          eventData = null
        } else if (trimmed.startsWith('data:')) {
          const chunk = trimmed.slice(5).replace(/^ /, '')
          eventData = eventData !== null ? eventData + '\n' + chunk : chunk
        }
      }
    }
  }

  return { stream: stream(), reader }
}
