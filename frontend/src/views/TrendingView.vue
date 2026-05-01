<template>
  <div class="trending-page">
    <div class="page-header">
      <h1 class="page-title">热点推送</h1>
      <div class="header-actions">
        <el-radio-group v-model="lang" size="small" @change="reload">
          <el-radio-button value="zh">中文</el-radio-button>
          <el-radio-button value="en">English</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="selectedDate"
          type="date"
          placeholder="选择日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          @change="loadByDate"
        />
        <el-button type="primary" :loading="refreshing" @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button @click="showSubscribe = true">
          <el-icon><Message /></el-icon>
          订阅
        </el-button>
      </div>
    </div>

    <div v-loading="loading">
      <div v-if="repos.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无热点数据" />
      </div>

      <div class="repo-list">
        <el-card v-for="(repo, i) in repos" :key="i" class="repo-card" shadow="never">
          <div class="repo-rank">{{ i + 1 }}</div>
          <div class="repo-info">
            <div class="repo-name">
              <a :href="repo.repoUrl" target="_blank" rel="noopener">{{ repo.repoName }}</a>
            </div>
            <div class="repo-desc" v-if="repo.description">{{ repo.description }}</div>
            <div class="repo-meta">
              <el-tag v-if="repo.language" size="small" type="success">{{ repo.language }}</el-tag>
              <span class="meta-item" v-if="repo.stars">
                <el-icon><Star /></el-icon> {{ formatNumber(repo.stars) }}
              </span>
              <span class="meta-item" v-if="repo.forks">
                <el-icon><Share /></el-icon> {{ formatNumber(repo.forks) }}
              </span>
              <span class="meta-item today-stars" v-if="repo.todayStars">
                <el-icon><TrendCharts /></el-icon> +{{ formatNumber(repo.todayStars) }}
              </span>
            </div>
            <div class="repo-analysis" v-if="repo.analysis">
              <div class="analysis-content">{{ repo.analysis }}</div>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="showSubscribe" title="订阅热点日报" width="400px">
      <el-form :model="subForm" label-position="top">
        <el-form-item label="邮箱地址">
          <el-input v-model="subForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="邮件语言">
          <el-radio-group v-model="subForm.language">
            <el-radio-button value="zh">中文</el-radio-button>
            <el-radio-button value="en">English</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubscribe = false">取消</el-button>
        <el-button type="primary" :loading="subscribing" @click="handleSubscribe">订阅</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTodayTrending, getTrendingByDate, refreshTrending, subscribeEmail } from '../api/trending'

const repos = ref([])
const loading = ref(false)
const refreshing = ref(false)
const selectedDate = ref(null)
const lang = ref('zh')

const showSubscribe = ref(false)
const subscribing = ref(false)
const subForm = ref({ email: '', language: 'zh' })

function formatNumber(n) {
  const num = parseInt(n) || 0
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return String(num)
}

async function loadToday() {
  loading.value = true
  try {
    repos.value = await getTodayTrending(lang.value)
  } finally {
    loading.value = false
  }
}

async function loadByDate() {
  if (!selectedDate.value) return loadToday()
  loading.value = true
  try {
    repos.value = await getTrendingByDate(selectedDate.value, lang.value)
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  refreshing.value = true
  try {
    // refreshTrending 立即返回当前数据，后台异步更新
    repos.value = await refreshTrending(lang.value)
    selectedDate.value = null
    ElMessage.success('热点数据已在后台更新，稍后刷新页面查看最新结果')
  } finally {
    refreshing.value = false
  }
}

async function reload() {
  if (selectedDate.value) {
    await loadByDate()
  } else {
    await loadToday()
  }
}

async function handleSubscribe() {
  if (!subForm.value.email) {
    ElMessage.warning('请输入邮箱地址')
    return
  }
  subscribing.value = true
  try {
    await subscribeEmail(subForm.value.email, subForm.value.language)
    ElMessage.success('订阅成功！')
    showSubscribe.value = false
  } catch (e) {
    ElMessage.error('订阅失败，请重试')
  } finally {
    subscribing.value = false
  }
}

onMounted(() => {
  loadToday()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2328;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.empty-state {
  padding: 60px 0;
}

.repo-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.repo-card {
  position: relative;
  padding-left: 12px;
}

.repo-card :deep(.el-card__body) {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.repo-rank {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f0fdf4;
  color: #2da44e;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.repo-info {
  flex: 1;
  min-width: 0;
}

.repo-name a {
  font-size: 16px;
  font-weight: 600;
  color: #2da44e;
  text-decoration: none;
}

.repo-name a:hover {
  text-decoration: underline;
}

.repo-desc {
  font-size: 13px;
  color: #656d76;
  margin-top: 4px;
  line-height: 1.5;
}

.repo-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
  font-size: 13px;
  color: #656d76;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.today-stars {
  color: #2da44e;
  font-weight: 600;
}

.repo-analysis {
  margin-top: 10px;
  padding: 10px 14px;
  background: #f6f8fa;
  border-radius: 6px;
  border-left: 3px solid #2da44e;
}

.analysis-content {
  font-size: 13px;
  color: #1f2328;
  line-height: 1.7;
  white-space: pre-line;
}
</style>
