import request from './request'

export function chatWithRepo(repoUrl, question, sessionId) {
  return request.post('/chat', { repoUrl, question, sessionId })
}

export function getChatHistory(sessionId) {
  return request.get(`/chat/history/${sessionId}`)
}

export function listChatSessions(limit = 20) {
  return request.get('/chat/sessions', { params: { limit } })
}
