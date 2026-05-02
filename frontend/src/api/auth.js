import request from './request'

export function register(username, password, nickname, githubUsername) {
  return request.post('/auth/register', { username, password, nickname, githubUsername })
}

export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

export function getMe() {
  return request.get('/auth/me')
}

export function updateProfile(data) {
  return request.put('/auth/profile', data)
}

export function changePassword(oldPassword, newPassword) {
  return request.post('/auth/change-password', { oldPassword, newPassword })
}

export function sendVerificationCode(email) {
  return request.post('/auth/send-verification-code', { email })
}

export function verifyEmail(email, code) {
  return request.post('/auth/verify-email', { email, code })
}
