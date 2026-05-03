<template>
  <div class="session-sidebar" :class="{ 'sidebar-open': sidebarOpen }">
    <div class="sidebar-header">
      <span class="sidebar-title">对话历史</span>
      <div class="sidebar-actions">
        <el-button type="primary" size="small" @click="$emit('new-session')" :disabled="!canCreate">
          <el-icon><Plus /></el-icon>
          <span class="btn-text">新建</span>
        </el-button>
        <el-button class="sidebar-close-btn" size="small" text @click="$emit('close')">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </div>
    <div class="session-list">
      <div
        v-for="session in sessions"
        :key="session.sessionId"
        :class="['session-item', { active: session.sessionId === currentSessionId }]"
        @click="$emit('switch', session)"
      >
        <div class="session-info">
          <div class="session-repo">
            {{ formatRepoName(session.repoUrl) }}
            <el-tag v-if="session.taskId" size="small" type="success" effect="light" style="margin-left: 4px">RAG</el-tag>
          </div>
          <div class="session-preview">{{ session.firstQuestion || '新对话' }}</div>
          <div class="session-time">{{ formatTime(session.createdAt) }}</div>
        </div>
        <el-button
          class="session-delete"
          type="danger"
          size="small"
          text
          @click.stop="$emit('delete', session.sessionId)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
      <div v-if="sessions.length === 0" class="no-sessions">
        暂无对话记录
      </div>
    </div>
  </div>
  <div class="sidebar-overlay" v-if="sidebarOpen" @click="$emit('close')"></div>
</template>

<script setup>
import { Plus, Delete, Close } from '@element-plus/icons-vue'

defineProps({
  sessions: { type: Array, default: () => [] },
  currentSessionId: { type: String, default: null },
  sidebarOpen: { type: Boolean, default: false },
  canCreate: { type: Boolean, default: false },
})

defineEmits(['switch', 'delete', 'new-session', 'close'])

function formatRepoName(url) {
  if (!url) return '未知仓库'
  const match = url.match(/github\.com\/([^/]+\/[^/]+)/)
  return match ? match[1] : url.length > 30 ? url.slice(0, 30) + '...' : url
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const diffMs = Date.now() - d
  if (diffMs < 60000) return '刚刚'
  if (diffMs < 3600000) return Math.floor(diffMs / 60000) + '分钟前'
  if (diffMs < 86400000) return Math.floor(diffMs / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
</script>

<style scoped>
.session-sidebar {
  width: 260px;
  min-width: 260px;
  border-right: 1px solid #e8ecf0;
  background: #f8faf9;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 14px;
  border-bottom: 1px solid #e8ecf0;
}

.sidebar-title { font-size: 14px; font-weight: 600; color: #1f2328; }
.sidebar-actions { display: flex; gap: 4px; align-items: center; }
.sidebar-close-btn { display: none; }

.session-list { flex: 1; overflow-y: auto; padding: 8px; }

.session-item {
  display: flex; align-items: center;
  padding: 10px 12px; border-radius: 8px;
  cursor: pointer; margin-bottom: 2px;
  transition: background 0.15s;
}

.session-item:hover { background: #e8ecf0; }
.session-item.active { background: #f0fdf4; border: 1px solid #2da44e33; }

.session-info { flex: 1; min-width: 0; }

.session-repo {
  font-size: 13px; font-weight: 600; color: #1f2328;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  display: flex; align-items: center;
}

.session-preview {
  font-size: 12px; color: #656d76;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px;
}

.session-time { font-size: 11px; color: #8b949e; margin-top: 2px; }
.session-delete { opacity: 0; transition: opacity 0.15s; }
.session-item:hover .session-delete { opacity: 1; }
.no-sessions { text-align: center; color: #8b949e; font-size: 13px; padding: 40px 16px; }
.sidebar-overlay { display: none; }

@media (max-width: 767px) {
  .session-sidebar {
    position: fixed; left: 0; top: 0; bottom: 0; z-index: 200;
    width: 280px; min-width: 280px;
    transform: translateX(-100%); transition: transform 0.25s ease;
  }

  .session-sidebar.sidebar-open { transform: translateX(0); box-shadow: 4px 0 24px rgba(0,0,0,0.12); }
  .sidebar-close-btn { display: inline-flex; }

  .sidebar-overlay {
    display: block; position: fixed; inset: 0;
    background: rgba(0,0,0,0.3); z-index: 199; backdrop-filter: blur(2px);
  }
}

@media (min-width: 768px) and (max-width: 1024px) {
  .session-sidebar { width: 220px; min-width: 220px; }
}
</style>
