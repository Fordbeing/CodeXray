import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMe } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('codexray_token') || '')
  const user = ref(JSON.parse(localStorage.getItem('codexray_user') || 'null'))
  const isLoggedIn = ref(!!token.value)

  function setAuth(newToken, userData) {
    token.value = newToken
    user.value = userData
    isLoggedIn.value = true
    localStorage.setItem('codexray_token', newToken)
    localStorage.setItem('codexray_user', JSON.stringify(userData))
  }

  function clearAuth() {
    token.value = ''
    user.value = null
    isLoggedIn.value = false
    localStorage.removeItem('codexray_token')
    localStorage.removeItem('codexray_user')
  }

  async function refreshUser() {
    try {
      const data = await getMe()
      user.value = data
      localStorage.setItem('codexray_user', JSON.stringify(data))
      return data
    } catch {
      clearAuth()
      return null
    }
  }

  return { token, user, isLoggedIn, setAuth, clearAuth, refreshUser }
})
