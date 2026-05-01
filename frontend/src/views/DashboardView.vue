<template>
  <div class="dashboard-page">
    <div class="welcome">
      <h1 class="welcome-title">{{ greeting }}</h1>
      <p class="welcome-sub">欢迎使用 CodeXray 代码分析平台</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-number">{{ stats.total }}</div>
        <div class="stat-label">总任务数</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-number completed">{{ stats.completed }}</div>
        <div class="stat-label">已完成</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-number trending">{{ trendingCount }}</div>
        <div class="stat-label">今日热点</div>
      </el-card>
    </div>

    <!-- 快速分析 -->
    <el-card class="quick-card" shadow="never">
      <template #header>
        <span class="section-title">快速分析</span>
      </template>
      <div class="quick-row">
        <el-input
          v-model="quickUrl"
          placeholder="输入 GitHub 仓库地址，快速开始分析"
          size="large"
          clearable
          @keyup.enter="startQuickAnalyze"
        >
          <template #prefix>
            <el-icon><Link /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" :disabled="!quickUrl" @click="startQuickAnalyze">
          分析
        </el-button>
      </div>
    </el-card>

    <!-- 最近任务 + 热点 -->
    <div class="bottom-row">
      <el-card class="recent-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="section-title">最近任务</span>
            <el-button link type="primary" @click="$router.push('/history')">查看全部</el-button>
          </div>
        </template>
        <div v-if="recentTasks.length === 0" class="empty-hint">暂无分析记录</div>
        <div v-for="task in recentTasks" :key="task.taskId" class="recent-item" @click="viewTask(task)">
          <span class="recent-url">{{ shortenUrl(task.repoUrl) }}</span>
          <el-tag :type="getStatusInfo(task.status).type" size="small">
            {{ getStatusInfo(task.status).text }}
          </el-tag>
        </div>
      </el-card>

      <el-card class="hot-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="section-title">今日热点</span>
            <el-button link type="primary" @click="$router.push('/trending')">查看全部</el-button>
          </div>
        </template>
        <div v-if="hotRepos.length === 0" class="empty-hint">暂无热点数据</div>
        <div v-for="(repo, i) in hotRepos" :key="i" class="hot-item">
          <span class="hot-rank">{{ i + 1 }}</span>
          <div class="hot-info">
            <a :href="repo.repoUrl" target="_blank" class="hot-name">{{ repo.repoName }}</a>
            <div class="hot-meta">
              <el-tag v-if="repo.language" size="small" type="success">{{ repo.language }}</el-tag>
              <span v-if="repo.stars">{{ formatNumber(repo.stars) }} stars</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listTasks } from '../api/analysis'
import { getTodayTrending } from '../api/trending'
import { getStatusInfo, shortenUrl } from '../utils/status'

const router = useRouter()
const quickUrl = ref('')
const recentTasks = ref([])
const hotRepos = ref([])

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了 '
  if (h < 12) return '早上好 ☀'
  if (h < 18) return '下午好 ☁'
  return '晚上好 '
})

const stats = computed(() => {
  const total = recentTasks.value.length
  const completed = recentTasks.value.filter(t => t.status === 'COMPLETED').length
  return { total, completed }
})

const trendingCount = computed(() => hotRepos.value.length)

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

onMounted(async () => {
  try {
    const [tasks, repos] = await Promise.all([
      listTasks(5).catch(() => []),
      getTodayTrending().catch(() => [])
    ])
    recentTasks.value = tasks || []
    hotRepos.value = (repos || []).slice(0, 5)
  } catch {
    // ignore
  }
})
</script>

<style scoped>
.welcome {
  margin-bottom: 24px;
}

.welcome-title {
  font-size: 28px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 4px 0;
}

.welcome-sub {
  font-size: 14px;
  color: #656d76;
  margin: 0;
}

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  flex: 1;
  text-align: center;
}

.stat-card :deep(.el-card__body) {
  padding: 24px 16px;
}

.stat-number {
  font-size: 32px;
  font-weight: 700;
  color: #1f2328;
}

.stat-number.completed {
  color: #2da44e;
}

.stat-number.trending {
  color: #d29922;
}

.stat-label {
  font-size: 13px;
  color: #656d76;
  margin-top: 4px;
}

.quick-card {
  margin-bottom: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2328;
}

.quick-row {
  display: flex;
  gap: 12px;
}

.quick-row .el-input {
  flex: 1;
}

.bottom-row {
  display: flex;
  gap: 20px;
}

.recent-card,
.hot-card {
  flex: 1;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.empty-hint {
  color: #8b949e;
  font-size: 13px;
  text-align: center;
  padding: 20px 0;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-item:hover .recent-url {
  color: #2da44e;
}

.recent-url {
  font-size: 13px;
  color: #1f2328;
  transition: color 0.2s;
}

.hot-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
}

.hot-item:last-child {
  border-bottom: none;
}

.hot-rank {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f0fdf4;
  color: #2da44e;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  flex-shrink: 0;
}

.hot-info {
  flex: 1;
  min-width: 0;
}

.hot-name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2328;
  text-decoration: none;
}

.hot-name:hover {
  color: #2da44e;
}

.hot-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
  font-size: 12px;
  color: #656d76;
}
</style>
