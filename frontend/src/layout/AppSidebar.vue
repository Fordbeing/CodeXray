<template>
  <div class="sidebar-overlay" :class="{ visible: visible && isMobile }" @click="$emit('close')"></div>
  <aside class="sidebar" :class="{ collapsed: !visible && isMobile }">
    <div class="sidebar-logo">
      <div class="logo-icon">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none">
          <path d="M12 2L2 7l10 5 10-5-10-5z" fill="#2da44e"/>
          <path d="M2 17l10 5 10-5" stroke="#2da44e" stroke-width="2" fill="none"/>
          <path d="M2 12l10 5 10-5" stroke="#2da44e" stroke-width="2" fill="none"/>
        </svg>
      </div>
      <div class="logo-text">
        <span class="logo-name">CodeXray</span>
        <span class="logo-tag">AI Code Analysis</span>
      </div>
      <div class="logo-actions">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="9" class="notif-badge">
          <button class="notif-btn" @click="dismissNotifications" title="通知">
            <el-icon :size="16"><Bell /></el-icon>
          </button>
        </el-badge>
      </div>
    </div>

    <!-- 通知面板 -->
    <div v-if="showNotif" class="notif-panel">
      <div class="notif-header">
        <span>通知</span>
        <el-button text size="small" @click="showNotif = false">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
      <div v-if="notifications.length === 0" class="notif-empty">暂无通知</div>
      <div
        v-for="n in notifications"
        :key="n.taskId"
        class="notif-item"
        @click="goToTask(n.taskId)"
      >
        <el-icon :size="14" :color="n.status === 'COMPLETED' ? '#2da44e' : '#cf222e'">
          <CircleCheck v-if="n.status === 'COMPLETED'" />
          <CircleClose v-else />
        </el-icon>
        <div class="notif-info">
          <div class="notif-title">{{ shortenUrl(n.repoUrl) }}</div>
          <div class="notif-meta">{{ n.status === 'COMPLETED' ? '分析完成' : '分析失败' }}</div>
        </div>
      </div>
    </div>

    <nav class="sidebar-nav">
      <router-link
        v-for="item in menuItems"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        :class="{ active: isActive(item.path) }"
        @click="$emit('close')"
      >
        <el-icon :size="18"><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="sidebar-footer">
      <!-- 已登录 -->
      <div v-if="user" class="user-card">
        <div class="user-info" @click="showProfile = true">
          <div class="user-avatar">
            <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" />
            <span v-else>{{ (user.nickname || user.username || '?').charAt(0).toUpperCase() }}</span>
          </div>
          <div class="user-detail">
            <div class="user-name">{{ user.nickname || user.username }}</div>
            <div class="user-meta" v-if="user.githubUsername">
              <GitHubIcon :size="12" />
              {{ user.githubUsername }}
            </div>
          </div>
        </div>
        <el-button class="logout-btn" text size="small" @click="handleLogout">
          <el-icon :size="16"><SwitchButton /></el-icon>
        </el-button>
      </div>

      <!-- 未登录 -->
      <div v-else class="login-prompt" @click="showAuth = true">
        <el-icon :size="18" color="#2da44e"><UserFilled /></el-icon>
        <span>登录 / 注册</span>
      </div>
    </div>
  </aside>

  <AuthDialog v-model="showAuth" @success="onAuthSuccess" />
  <ProfileDialog v-model="showProfile" :user="user" @success="onProfileUpdate" />
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  HomeFilled, Search, ChatDotRound, TrendCharts, List, Platform, Setting,
  SwitchButton, UserFilled, Bell, Close, CircleCheck, CircleClose
} from '@element-plus/icons-vue'
import { getMe } from '../api/auth'
import { getNotifications } from '../api/analysis'
import { shortenUrl } from '../utils/status'
import { useAuthStore } from '../stores/auth'
import AuthDialog from '../components/AuthDialog.vue'
import ProfileDialog from '../components/ProfileDialog.vue'
import GitHubIcon from '../components/icons/GitHubIcon.vue'

defineProps({ visible: Boolean })
defineEmits(['close'])

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isMobile = ref(false)
const user = ref(null)
const showAuth = ref(false)
const showProfile = ref(false)
const showNotif = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
let notifTimer = null

const menuItems = [
  { path: '/', label: '仪表盘', icon: HomeFilled },
  { path: '/analyze', label: '仓库分析', icon: Search },
  { path: '/chat', label: '代码问答', icon: ChatDotRound },
  { path: '/trending', label: '热点推送', icon: TrendCharts },
  { path: '/github', label: '我的 GitHub', icon: Platform },
  { path: '/history', label: '分析历史', icon: List },
  { path: '/settings', label: '系统设置', icon: Setting },
]

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

function loadUser() {
  const saved = localStorage.getItem('codexray_user')
  const token = localStorage.getItem('codexray_token')
  if (saved && token) {
    user.value = JSON.parse(saved)
    // 验证 token 有效性并刷新用户信息
    getMe().then(data => {
      user.value = { ...data, token }
      localStorage.setItem('codexray_user', JSON.stringify(user.value))
    }).catch(() => {
      // token 过期，清除
      handleLogout()
    })
  }
}

function handleLogout() {
  // 清除所有应用相关的 localStorage 数据
  const keysToRemove = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key && key.startsWith('codexray_')) {
      keysToRemove.push(key)
    }
  }
  keysToRemove.forEach(k => localStorage.removeItem(k))
  user.value = null
  window.dispatchEvent(new CustomEvent('auth-change', { detail: null }))
  ElMessage.success('已退出登录')
}

function onAuthSuccess(data) {
  user.value = data
  // 触发全局事件让其他组件知道登录状态变化
  window.dispatchEvent(new CustomEvent('auth-change', { detail: data }))
}

function onProfileUpdate(data) {
  const token = user.value?.token
  user.value = { ...data, token }
  localStorage.setItem('codexray_user', JSON.stringify(user.value))
  window.dispatchEvent(new CustomEvent('auth-change', { detail: user.value }))
}

function onAuthRequired() {
  showAuth.value = true
}

async function loadNotifications() {
  try {
    const data = await getNotifications(5)
    const lastSeen = parseInt(localStorage.getItem('codexray_notif_seen') || '0')
    notifications.value = data
    // 只统计上次查看之后的新通知
    unreadCount.value = data.filter(n => {
      const t = new Date(n.updatedAt || n.createdAt).getTime()
      return t > lastSeen && (n.status === 'COMPLETED' || n.status === 'FAILED')
    }).length
  } catch { /* ignore */ }
}

function dismissNotifications() {
  showNotif.value = !showNotif.value
  if (showNotif.value) {
    unreadCount.value = 0
    localStorage.setItem('codexray_notif_seen', String(Date.now()))
  }
}

function goToTask(taskId) {
  showNotif.value = false
  router.push('/analyze/' + taskId)
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  window.addEventListener('auth-required', onAuthRequired)
  loadUser()
  loadNotifications()
  notifTimer = setInterval(loadNotifications, 30000)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  window.removeEventListener('auth-required', onAuthRequired)
  if (notifTimer) clearInterval(notifTimer)
})
</script>

<style scoped>
.sidebar-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 99;
  backdrop-filter: blur(2px);
  opacity: 0;
  transition: opacity 0.25s ease;
}

.sidebar-overlay.visible {
  display: block;
  opacity: 1;
}

.sidebar {
  width: 220px;
  height: 100vh;
  background: #ffffff;
  border-right: 1px solid #e8ecf0;
  display: flex;
  flex-direction: column;
  position: fixed;
  left: 0;
  top: 0;
  z-index: 100;
  transition: transform 0.25s ease;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 20px 18px;
  border-bottom: 1px solid #f0f2f5;
  position: relative;
}

.logo-actions {
  margin-left: auto;
}

.notif-badge :deep(.el-badge__content) {
  border: none;
}

.notif-btn {
  width: 32px; height: 32px; border-radius: 8px;
  border: 1px solid #e8ecf0; background: #fff; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: #656d76; transition: all 0.15s;
}

.notif-btn:hover { border-color: #2da44e; color: #2da44e; background: #f0fdf4; }

/* 通知面板 */
.notif-panel {
  position: absolute; top: 60px; right: 12px; left: 12px;
  background: #fff; border: 1px solid #d8dee4; border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.12); z-index: 200;
  max-height: 300px; overflow-y: auto;
}

.notif-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; border-bottom: 1px solid #f0f2f5;
  font-size: 13px; font-weight: 600; color: #1f2328;
}

.notif-empty {
  text-align: center; padding: 24px; color: #8b949e; font-size: 13px;
}

.notif-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; cursor: pointer; transition: background 0.1s;
  border-bottom: 1px solid #f6f8fa;
}

.notif-item:last-child { border-bottom: none; }
.notif-item:hover { background: #f6f8fa; }

.notif-info { flex: 1; min-width: 0; }
.notif-title {
  font-size: 13px; color: #1f2328;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.notif-meta { font-size: 11px; color: #8b949e; margin-top: 2px; }

.logo-icon {
  width: 36px;
  height: 36px;
  background: #f0fdf4;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.logo-name {
  font-size: 16px;
  font-weight: 700;
  color: #1f2328;
  line-height: 1.2;
}

.logo-tag {
  font-size: 11px;
  color: #8b949e;
  font-weight: 500;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  color: #656d76;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.15s;
}

.nav-item:hover {
  background: #f6f8fa;
  color: #1f2328;
}

.nav-item.active {
  background: #f0fdf4;
  color: #2da44e;
  font-weight: 600;
}

.sidebar-footer {
  padding: 12px 14px;
  border-top: 1px solid #f0f2f5;
}

.sidebar-toggle {
  padding: 4px 14px;
}

.dark-toggle {
  width: 100%;
  justify-content: flex-start;
  gap: 6px;
  color: #656d76;
  font-size: 13px;
}

/* 登录提示 */
.login-prompt {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #1f2328;
  transition: all 0.15s;
  border: 1px dashed #d0d7de;
}

.login-prompt:hover {
  background: #f0fdf4;
  border-color: #2da44e;
}

/* 用户卡片 */
.user-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px;
  border-radius: 8px;
  background: #f8faf9;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  cursor: pointer;
  flex: 1;
}

.user-info:hover .user-name {
  color: #2da44e;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #2da44e;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-detail {
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2328;
  transition: color 0.15s;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: #8b949e;
  margin-top: 1px;
}

.logout-btn {
  flex-shrink: 0;
  color: #8b949e;
}

.logout-btn:hover {
  color: #cf222e;
}

@media (max-width: 767px) {
  .sidebar.collapsed {
    transform: translateX(-100%);
  }

  .sidebar-overlay.visible {
    transition: opacity 0.25s ease;
  }
}

/* 暗色模式适配 */
:global(html.dark) .sidebar {
  background: #22272e;
  border-right-color: #373e47;
}

:global(html.dark) .sidebar-logo {
  border-bottom-color: #373e47;
}

:global(html.dark) .logo-name {
  color: #e6edf3;
}

:global(html.dark) .nav-item {
  color: #768390;
}

:global(html.dark) .nav-item:hover {
  background: #2d333b;
  color: #e6edf3;
}

:global(html.dark) .nav-item.active {
  background: rgba(45, 164, 78, 0.15);
  color: #2da44e;
}

:global(html.dark) .sidebar-footer {
  border-top-color: #373e47;
}

:global(html.dark) .user-card {
  background: #2d333b;
}

:global(html.dark) .user-name {
  color: #e6edf3;
}

:global(html.dark) .login-prompt {
  color: #e6edf3;
  border-color: #444c56;
}

:global(html.dark) .login-prompt:hover {
  background: rgba(45, 164, 78, 0.15);
  border-color: #2da44e;
}
</style>
