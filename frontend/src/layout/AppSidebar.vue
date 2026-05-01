<template>
  <aside class="sidebar">
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
      >
        <el-icon :size="18"><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="sidebar-footer">
      <div class="footer-badge">
        <el-icon :size="14"><Promotion /></el-icon>
        <span>Powered by LLM</span>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { useRoute } from 'vue-router'
import {
  HomeFilled, Search, ChatDotRound, TrendCharts, List
} from '@element-plus/icons-vue'

const route = useRoute()

const menuItems = [
  { path: '/', label: '仪表盘', icon: HomeFilled },
  { path: '/analyze', label: '仓库分析', icon: Search },
  { path: '/chat', label: '代码问答', icon: ChatDotRound },
  { path: '/trending', label: '热点推送', icon: TrendCharts },
  { path: '/history', label: '分析历史', icon: List },
]

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>

<style scoped>
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
  padding: 16px 20px;
  border-top: 1px solid #f0f2f5;
}

.footer-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8b949e;
}
</style>
