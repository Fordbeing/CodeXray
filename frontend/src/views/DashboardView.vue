<template>
  <div class="dashboard-page">
    <div class="welcome">
      <h1 class="welcome-title">{{ greeting }}<span v-if="user">，{{ user.nickname || user.username }}</span></h1>
      <p class="welcome-sub">欢迎使用 CodeXray 代码分析平台</p>
    </div>

    <!-- 快速分析 -->
    <div class="quick-section">
      <div class="quick-row">
        <el-input
          v-model="quickUrl"
          placeholder="输入 GitHub 仓库地址，快速开始分析..."
          size="large"
          clearable
          @keyup.enter="startQuickAnalyze"
        >
          <template #prefix>
            <el-icon><Link /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" :disabled="!quickUrl" @click="startQuickAnalyze">
          开始分析
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon-wrap" style="background: #f0fdf4">
          <el-icon :size="20" color="#2da44e"><List /></el-icon>
        </div>
        <div>
          <div class="stat-number">{{ allTasks.length }}</div>
          <div class="stat-label">总任务数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-wrap" style="background: #ecfdf5">
          <el-icon :size="20" color="#2da44e"><CircleCheck /></el-icon>
        </div>
        <div>
          <div class="stat-number" style="color: #2da44e">{{ completedCount }}</div>
          <div class="stat-label">已完成</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-wrap" style="background: #fefce8">
          <el-icon :size="20" color="#d29922"><TrendCharts /></el-icon>
        </div>
        <div>
          <div class="stat-number" style="color: #d29922">{{ hotRepos.length }}</div>
          <div class="stat-label">今日热点</div>
        </div>
      </div>
    </div>

    <!-- 左右布局 -->
    <div class="main-row">
      <!-- 左侧：最近任务 -->
      <div class="left-col">
        <div class="section-card">
          <div class="section-header">
            <span class="section-title">最近任务</span>
            <el-button link type="primary" @click="$router.push('/history')">查看全部</el-button>
          </div>
          <div v-if="recentTasks.length === 0" class="empty-hint">暂无分析记录</div>
          <div v-for="task in recentTasks" :key="task.taskId" class="recent-item" @click="viewTask(task)">
            <span class="recent-url">{{ shortenUrl(task.repoUrl) }}</span>
            <el-tag :type="getStatusInfo(task.status).type" size="small" effect="light">
              {{ getStatusInfo(task.status).text }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- 右侧：今日热点 + 快速操作 -->
      <div class="right-col">
        <div class="section-card">
          <div class="section-header">
            <span class="section-title">今日热点</span>
            <el-button link type="primary" @click="$router.push('/trending')">查看全部</el-button>
          </div>
          <div v-if="hotRepos.length === 0" class="empty-hint">暂无热点数据</div>
          <div v-for="(repo, i) in hotRepos" :key="i" class="hot-item">
            <span class="hot-rank" :class="{ 'rank-1': i === 0, 'rank-2': i === 1, 'rank-3': i === 2 }">{{ i + 1 }}</span>
            <div class="hot-info">
              <a :href="repo.repoUrl" target="_blank" class="hot-name">{{ repo.repoName }}</a>
              <div class="hot-meta">
                <el-tag v-if="repo.language" size="small" type="success" effect="light">{{ repo.language }}</el-tag>
                <span v-if="repo.stars"><span style="color: #e3b341">&#9733;</span> {{ formatNumber(repo.stars) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 快速操作 -->
        <div class="section-card quick-actions">
          <div class="section-header">
            <span class="section-title">快速操作</span>
          </div>
          <div class="action-grid">
            <div v-for="action in quickActions" :key="action.path" class="action-card" @click="$router.push(action.path)">
              <div class="action-icon" :style="{ background: action.bg }">
                <el-icon :size="22" :color="action.color"><component :is="action.icon" /></el-icon>
              </div>
              <div class="action-info">
                <div class="action-name">{{ action.label }}</div>
                <div class="action-desc">{{ action.desc }}</div>
              </div>
              <el-icon class="action-arrow" :size="14" color="#d0d7de"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listTasks } from '../api/analysis'
import { getTodayTrending } from '../api/trending'
import { getStatusInfo, shortenUrl } from '../utils/status'
import {
  Search, ChatDotRound, TrendCharts, Platform, ArrowRight
} from '@element-plus/icons-vue'

const router = useRouter()
const quickUrl = ref('')
const recentTasks = ref([])
const allTasks = ref([])
const hotRepos = ref([])
const user = ref(null)

const quickActions = [
  { path: '/analyze', label: '仓库分析', desc: '输入仓库地址，深度分析代码', icon: Search, bg: '#f0fdf4', color: '#2da44e' },
  { path: '/chat', label: '代码问答', desc: '基于 RAG 的智能代码问答', icon: ChatDotRound, bg: '#eff6ff', color: '#3b82f6' },
  { path: '/trending', label: '热点推送', desc: '每日 GitHub 热门项目推荐', icon: TrendCharts, bg: '#fefce8', color: '#d29922' },
  { path: '/github', label: '我的 GitHub', desc: '查看仓库、收藏和语言统计', icon: Platform, bg: '#f5f3ff', color: '#8b5cf6' },
]

const completedCount = computed(() => allTasks.value.filter(t => t.status === 'COMPLETED').length)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

function formatNumber(n) {
  const num = parseInt(n) || 0
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return String(num)
}

function startQuickAnalyze() {
  if (!quickUrl.value) return
  router.push({ path: '/analyze', query: { url: quickUrl.value } })
}

function viewTask(task) {
  if (task.status === 'COMPLETED') {
    router.push(`/analyze/${task.taskId}`)
  }
}

function loadUser() {
  const saved = localStorage.getItem('codexray_user')
  if (saved) {
    try { user.value = JSON.parse(saved) } catch { /* ignore */ }
  }
}

onMounted(async () => {
  loadUser()

  const [tasks, repos] = await Promise.all([
    listTasks(200).catch(() => []),
    getTodayTrending().catch(() => [])
  ])
  allTasks.value = tasks || []
  recentTasks.value = (tasks || []).slice(0, 5)
  hotRepos.value = (repos || []).slice(0, 5)
})
</script>

<style scoped>
.dashboard-page {
  max-width: 1400px;
  margin: 0 auto;
}

.welcome {
  margin-bottom: 20px;
}

.welcome-title {
  font-size: 26px;
  font-weight: 700;
  color: #1f2328;
  margin: 0 0 4px 0;
}

.welcome-sub {
  font-size: 14px;
  color: #656d76;
  margin: 0;
}

.quick-section {
  margin-bottom: 24px;
}

.quick-row {
  display: flex;
  gap: 12px;
}

.quick-row :deep(.el-input) { flex: 1; }

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  padding: 18px 20px;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.stat-card:hover {
  border-color: #2da44e40;
  box-shadow: 0 2px 8px rgba(45,164,78,0.06);
}

.stat-icon-wrap {
  width: 42px; height: 42px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.stat-number { font-size: 24px; font-weight: 800; color: #1f2328; line-height: 1.2; }
.stat-label { font-size: 13px; color: #656d76; margin-top: 2px; }

/* ===== 左右布局 ===== */
.main-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.left-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.right-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.section-card {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  padding: 18px 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2328;
}

.empty-hint {
  color: #8b949e;
  font-size: 13px;
  text-align: center;
  padding: 24px 0;
}

/* 最近任务 */
.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 4px;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
  transition: background 0.15s;
  border-radius: 6px;
}

.recent-item:hover { background: #f6f8fa; }
.recent-item:last-child { border-bottom: none; }
.recent-url { font-size: 13px; color: #1f2328; }

/* 热点 */
.hot-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
}

.hot-item:last-child { border-bottom: none; }

.hot-rank {
  width: 24px; height: 24px;
  border-radius: 6px;
  background: #f6f8fa;
  color: #656d76;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 12px;
  flex-shrink: 0;
}

.hot-rank.rank-1 { background: linear-gradient(135deg, #fef3c7, #fde68a); color: #92400e; }
.hot-rank.rank-2 { background: #f3f4f6; color: #4b5563; }
.hot-rank.rank-3 { background: linear-gradient(135deg, #fef2f2, #fed7aa); color: #9a3412; }

.hot-info { flex: 1; min-width: 0; }

.hot-name {
  font-size: 14px; font-weight: 600;
  color: #1f2328; text-decoration: none;
}

.hot-name:hover { color: #2da44e; }

.hot-meta {
  display: flex; gap: 8px; align-items: center;
  margin-top: 4px; font-size: 12px; color: #656d76;
}

/* 快速操作 */
.action-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid #e8ecf0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-card:hover {
  border-color: #2da44e40;
  background: #f8faf9;
  transform: translateX(2px);
}

.action-icon {
  width: 44px; height: 44px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.action-info { flex: 1; min-width: 0; }

.action-name {
  font-size: 14px;
  font-weight: 700;
  color: #1f2328;
}

.action-desc {
  font-size: 12px;
  color: #8b949e;
  margin-top: 2px;
}

.action-arrow {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}

.action-card:hover .action-arrow {
  opacity: 1;
}

/* ===== 响应式 ===== */
@media (max-width: 767px) {
  .welcome-title { font-size: 22px; }
  .quick-row { flex-direction: column; }
  .quick-row .el-button { width: 100%; }
  .stats-row { flex-direction: column; gap: 12px; }
  .stat-card { padding: 14px 16px; }
  .stat-number { font-size: 20px; }
  .main-row { flex-direction: column; gap: 16px; }
}
</style>
