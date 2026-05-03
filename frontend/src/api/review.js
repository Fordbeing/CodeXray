import request from './request'

/** 提交 diff 进行 AI 代码审查 */
export function reviewCode(diff, prUrl) {
  return request.post('/review', { diff, prUrl }, { timeout: 180000 })
}

/** 从已有分析任务中审查文件 */
export function reviewFile(taskId, filePath) {
  return request.post('/review', { taskId, filePath }, { timeout: 180000 })
}

/** 获取分析任务的文件树 */
export function getFileTree(taskId) {
  return request.get(`/analysis/${taskId}/tree`)
}

/** 获取分析任务列表（用于文件审查选择） */
export function listTasksForReview(limit = 30) {
  return request.get('/analysis/list', { params: { limit } })
}

/** 获取审查历史记录 */
export function listReviewRecords(limit = 30) {
  return request.get('/review/list', { params: { limit } })
}

/** 获取单条审查结果 */
export function getReviewRecord(reviewId) {
  return request.get(`/review/${reviewId}`)
}

/** 删除审查记录 */
export function deleteReviewRecord(reviewId) {
  return request.delete(`/review/${reviewId}`)
}
