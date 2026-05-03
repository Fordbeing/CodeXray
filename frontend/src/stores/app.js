import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useAppStore = defineStore('app', () => {
  const isDark = ref(localStorage.getItem('codexray_dark') === 'true')
  const sidebarOpen = ref(true)

  function toggleDark() {
    isDark.value = !isDark.value
    localStorage.setItem('codexray_dark', String(isDark.value))
    applyDarkClass()
  }

  function applyDarkClass() {
    document.documentElement.classList.toggle('dark', isDark.value)
  }

  function toggleSidebar() {
    sidebarOpen.value = !sidebarOpen.value
  }

  // Initialize dark mode on load
  applyDarkClass()

  return { isDark, sidebarOpen, toggleDark, toggleSidebar }
})
