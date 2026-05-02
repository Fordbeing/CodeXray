import request from './request'

export function getSettings() {
  return request.get('/settings')
}

export function updateSettings(settings) {
  return request.put('/settings', settings)
}

export function testAiConnection() {
  return request.post('/settings/test-ai')
}

export function testEmbeddingConnection() {
  return request.post('/settings/test-embedding')
}

// AI Presets
export function getAiPresets() {
  return request.get('/settings/presets/ai')
}

export function saveAiPreset(preset) {
  return request.post('/settings/presets/ai', preset)
}

export function deleteAiPreset(name) {
  return request.delete(`/settings/presets/ai/${encodeURIComponent(name)}`)
}

// Mail Presets
export function getMailPresets() {
  return request.get('/settings/presets/mail')
}

export function saveMailPreset(preset) {
  return request.post('/settings/presets/mail', preset)
}

export function deleteMailPreset(name) {
  return request.delete(`/settings/presets/mail/${encodeURIComponent(name)}`)
}
