import request from './request'

export function getTodayTrending(lang = 'zh') {
  return request.get('/trending/today', { params: { lang } })
}

export function getTrendingByDate(date, lang = 'zh') {
  return request.get('/trending', { params: { date, lang } })
}

export function refreshTrending(lang = 'zh') {
  return request.post('/trending/refresh', null, { params: { lang } })
}

export function subscribeEmail(email, language = 'zh') {
  return request.post('/email/subscribe', null, { params: { email, language } })
}
