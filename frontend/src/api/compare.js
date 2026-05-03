import request from './request'

/** 对比两次分析报告 */
export function compareReports(taskA, taskB) {
  return request.get('/analysis/compare', { params: { taskA, taskB }, timeout: 120000 })
}

/** 获取按仓库分组的分析任务列表（用于对比选择） */
export function listTasksForCompare(limit = 50) {
  return request.get('/analysis/compare/tasks', { params: { limit } })
}

/** 获取对比历史记录 */
export function listComparisonRecords(limit = 30) {
  return request.get('/analysis/compare/list', { params: { limit } })
}

/** 获取单条对比结果 */
export function getComparisonRecord(comparisonId) {
  return request.get(`/analysis/compare/${comparisonId}`)
}

/** 删除对比记录 */
export function deleteComparisonRecord(comparisonId) {
  return request.delete(`/analysis/compare/${comparisonId}`)
}
