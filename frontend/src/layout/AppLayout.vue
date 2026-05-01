<template>
  <div class="app-layout">
    <AppSidebar :visible="sidebarVisible" @close="sidebarVisible = false" />
    <main class="app-main" :class="{ expanded: !sidebarVisible }">
      <button class="menu-toggle" @click="sidebarVisible = !sidebarVisible">
        <el-icon :size="20"><Operation /></el-icon>
      </button>
      <div class="app-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AppSidebar from './AppSidebar.vue'

const sidebarVisible = ref(true)
</script>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  background: #f6f8fa;
}

.app-main {
  flex: 1;
  margin-left: 220px;
  min-height: 100vh;
  position: relative;
}

.app-content {
  padding: 28px 36px 48px;
}

.menu-toggle {
  display: none;
  position: fixed;
  top: 14px;
  left: 14px;
  z-index: 90;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid #d0d7de;
  background: #ffffff;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  color: #656d76;
  transition: all 0.15s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.menu-toggle:hover {
  color: #1f2328;
  border-color: #2da44e;
  background: #f0fdf4;
}

@media (max-width: 767px) {
  .app-main {
    margin-left: 0;
  }

  .app-content {
    padding: 20px 16px 40px;
  }

  .menu-toggle {
    display: flex;
  }
}

@media (min-width: 768px) and (max-width: 1024px) {
  .app-content {
    padding: 24px 24px 40px;
  }
}
</style>
