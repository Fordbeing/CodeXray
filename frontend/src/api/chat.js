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
