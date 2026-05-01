import request from './request'

/** 发送问答消息 */
export function chatWithRepo(repoUrl, question, sessionId, taskId) {
  return request.post('/chat', { repoUrl, question, sessionId, taskId })
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
