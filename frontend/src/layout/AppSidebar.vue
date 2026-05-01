<template>
  <aside class="sidebar">
    <div class="sidebar-logo">
      <el-icon :size="24" color="#2da44e"><DataAnalysis /></el-icon>
      <span class="logo-text">CodeXray</span>
    </div>
    <el-menu
      :default-active="activeMenu"
      :router="true"
      class="sidebar-menu"
    >
      <el-menu-item index="/">
        <el-icon><HomeFilled /></el-icon>
        <span>仪表盘</span>
      </el-menu-item>
      <el-menu-item index="/analyze">
        <el-icon><Search /></el-icon>
        <span>仓库分析</span>
      </el-menu-item>
      <el-menu-item index="/chat">
        <el-icon><ChatDotRound /></el-icon>
        <span>代码问答</span>
      </el-menu-item>
      <el-menu-item index="/trending">
        <el-icon><TrendCharts /></el-icon>
        <span>热点推送</span>
      </el-menu-item>
      <el-menu-item index="/history">
        <el-icon><List /></el-icon>
        <span>分析历史</span>
      </el-menu-item>
    </el-menu>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/analyze')) return '/analyze'
  return path
})
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
  gap: 10px;
  padding: 20px 20px 16px;
  border-bottom: 1px solid #e8ecf0;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #1f2328;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  padding-top: 8px;
}

.sidebar-menu .el-menu-item {
  height: 44px;
  line-height: 44px;
  margin: 2px 8px;
  border-radius: 6px;
  color: #656d76;
}

.sidebar-menu .el-menu-item:hover {
  background-color: #f6f8fa;
  color: #1f2328;
}

.sidebar-menu .el-menu-item.is-active {
  background-color: #f0fdf4;
  color: #2da44e !important;
}
</style>
