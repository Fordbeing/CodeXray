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
              <svg viewBox="0 0 16 16" width="12" height="12"><path fill="#656d76" d="M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z"/></svg>
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
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  HomeFilled, Search, ChatDotRound, TrendCharts, List, Platform
} from '@element-plus/icons-vue'
import { getMe } from '../api/auth'
import AuthDialog from '../components/AuthDialog.vue'
import ProfileDialog from '../components/ProfileDialog.vue'

defineProps({ visible: Boolean })
defineEmits(['close'])

const route = useRoute()
const isMobile = ref(false)
const user = ref(null)
const showAuth = ref(false)
const showProfile = ref(false)

const menuItems = [
  { path: '/', label: '仪表盘', icon: HomeFilled },
  { path: '/analyze', label: '仓库分析', icon: Search },
  { path: '/chat', label: '代码问答', icon: ChatDotRound },
  { path: '/trending', label: '热点推送', icon: TrendCharts },
  { path: '/github', label: '我的 GitHub', icon: Platform },
  { path: '/history', label: '分析历史', icon: List },
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
  localStorage.removeItem('codexray_token')
  localStorage.removeItem('codexray_user')
  user.value = null
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

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  loadUser()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
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
}

.sidebar-overlay.visible {
  display: block;
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
}

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
}
</style>
