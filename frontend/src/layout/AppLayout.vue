<template>
  <div class="app-layout">
    <AppSidebar />
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import AppSidebar from './AppSidebar.vue'
</script>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
}

.app-main {
  flex: 1;
  margin-left: 220px;
  padding: 32px;
  max-width: 1080px;
}
</style>
