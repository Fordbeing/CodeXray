<template>
  <div class="dashboard-page">
    <div class="welcome">
      <h1 class="welcome-title">{{ greeting }}</h1>
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
          <div class="stat-number">{{ stats.total }}</div>
          <div class="stat-label">总任务数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-wrap" style="background: #ecfdf5">
          <el-icon :size="20" color="#2da44e"><CircleCheck /></el-icon>
        </div>
        <div>
          <div class="stat-number" style="color: #2da44e">{{ stats.completed }}</div>
          <div class="stat-label">已完成</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-wrap" style="background: #fefce8">
          <el-icon :size="20" color="#d29922"><TrendCharts /></el-icon>
        </div>
        <div>
          <div class="stat-number" style="color: #d29922">{{ trendingCount }}</div>
          <div class="stat-label">今日热点</div>
        </div>
      </div>
    </div>

    <!-- 最近任务 + 热点 -->
    <div class="bottom-row">
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
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
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
.dashboard-page {
  max-width: 960px;
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

.quick-row .el-input {
  flex: 1;
}

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
  background: #ffffff;
  border: 1px solid #d8dee4;
  border-radius: 10px;
  padding: 18px 20px;
}

.stat-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-number {
  font-size: 24px;
  font-weight: 800;
  color: #1f2328;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #656d76;
}

.bottom-row {
  display: flex;
  gap: 20px;
}

.section-card {
  flex: 1;
  background: #ffffff;
  border: 1px solid #d8dee4;
  border-radius: 10px;
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

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
  transition: background 0.15s;
  border-radius: 4px;
  padding-left: 4px;
  padding-right: 4px;
}

.recent-item:hover {
  background: #f6f8fa;
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-url {
  font-size: 13px;
  color: #1f2328;
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
  border-radius: 6px;
  background: #f6f8fa;
  color: #656d76;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 12px;
  flex-shrink: 0;
}

.hot-rank.rank-1 {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  color: #92400e;
}

.hot-rank.rank-2 {
  background: #f3f4f6;
  color: #4b5563;
}

.hot-rank.rank-3 {
  background: linear-gradient(135deg, #fef2f2, #fed7aa);
  color: #9a3412;
}

.hot-info {
  flex: 1;
  min-width: 0;
}

.hot-name {
  font-size: 14px;
  font-weight: 600;
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
