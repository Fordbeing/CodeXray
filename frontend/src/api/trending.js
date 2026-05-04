import request from './request'

export function getTodayTrending(lang = 'zh') {
  return request.get('/trending/today', { params: { lang } })
}

export function getTrendingByDate(date, lang = 'zh') {
  return request.get('/trending', { params: { date, lang } })
}

export function getWeeklyTrending(lang = 'zh') {
  return request.get('/trending/weekly', { params: { lang } })
}

export function refreshTrending(lang = 'zh') {
  return request.post('/trending/refresh', null, { params: { lang } })
}

export function subscribeEmail(email, language = 'zh') {
  return request.post('/email/subscribe', null, { params: { email, language } })
}

export function unsubscribeEmail(email) {
  return request.post('/email/unsubscribe', null, { params: { email } })
}
