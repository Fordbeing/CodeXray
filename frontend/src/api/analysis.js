import request from './request'

export function analyzeRepo(repoUrl) {
  return request.post('/analysis/analyze', { repoUrl })
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
