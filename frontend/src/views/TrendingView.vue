<template>
  <div class="trending-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">GitHub 热点</h1>
        <p class="page-subtitle">每日热门开源项目，AI 助你快速判断是否值得关注</p>
      </div>
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
          style="width: 140px"
        />
        <el-button type="primary" :loading="refreshing" @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新数据
        </el-button>
        <el-button plain @click="showSubscribe = true">
          <el-icon><Message /></el-icon>
          订阅日报
        </el-button>
      </div>
    </div>

    <div v-loading="loading">
      <div v-if="repos.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无热点数据，点击刷新获取今日热点" />
      </div>

      <div class="repo-list">
        <div v-for="(repo, i) in repos" :key="i" class="repo-card">
          <div class="card-left">
            <div class="rank-badge" :class="rankClass(i)">
              {{ i + 1 }}
            </div>
          </div>
          <div class="card-main">
            <div class="card-header">
              <div class="repo-title">
                <a :href="repo.repoUrl" target="_blank" rel="noopener" class="repo-name">
                  {{ repo.repoName }}
                </a>
                <el-tag v-if="repo.language" size="small" class="lang-tag" :style="{ background: langColor(repo.language) + '18', color: langColor(repo.language), borderColor: langColor(repo.language) + '40' }">
                  {{ repo.language }}
                </el-tag>
              </div>
              <div class="repo-stats">
                <div class="stat-item" v-if="repo.stars">
                  <svg class="stat-icon" viewBox="0 0 16 16" width="14" height="14"><path fill="#e3b341" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>
                  <span class="stat-value">{{ formatNumber(repo.stars) }}</span>
                </div>
                <div class="stat-item" v-if="repo.forks">
                  <svg class="stat-icon" viewBox="0 0 16 16" width="14" height="14"><path fill="#8b949e" d="M5 5.372v.878c0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75v-.878a2.25 2.25 0 1 1 1.5 0v.878a2.25 2.25 0 0 1-2.25 2.25h-1.5v2.128a2.251 2.251 0 1 1-1.5 0V8.5h-1.5A2.25 2.25 0 0 1 3.5 6.25v-.878a2.25 2.25 0 1 1 1.5 0ZM5 3.25a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Zm6.75.75a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5Zm-3 8.75a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Z"/></svg>
                  <span class="stat-value">{{ formatNumber(repo.forks) }}</span>
                </div>
                <div class="stat-item trend-up" v-if="repo.todayStars">
                  <svg class="stat-icon" viewBox="0 0 16 16" width="14" height="14"><path fill="#2da44e" d="m4.427 7.427 3.396 3.396a.25.25 0 0 0 .354 0l3.396-3.396A.25.25 0 0 0 11.396 7H4.604a.25.25 0 0 0-.177.427Z"/></svg>
                  <span class="stat-value">+{{ formatNumber(repo.todayStars) }} today</span>
                </div>
              </div>
            </div>
            <p class="repo-desc" v-if="repo.description">{{ repo.description }}</p>
            <div class="repo-analysis" v-if="repo.analysis">
              <div class="analysis-header">
                <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#2da44e" d="M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"/></svg>
                <span>AI 分析</span>
              </div>
              <div class="analysis-body" v-html="formatAnalysis(repo.analysis)"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showSubscribe" title="订阅每日热点日报" width="420px" class="subscribe-dialog">
      <p class="subscribe-hint">每日早 8 点自动推送 GitHub 热点项目到您的邮箱</p>
      <el-form :model="subForm" label-position="top">
        <el-form-item label="邮箱地址">
          <el-input v-model="subForm.email" placeholder="your@email.com" size="large" />
        </el-form-item>
        <el-form-item label="邮件语言">
          <el-radio-group v-model="subForm.language" size="large">
            <el-radio-button value="zh">中文</el-radio-button>
            <el-radio-button value="en">English</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubscribe = false">取消</el-button>
        <el-button type="primary" :loading="subscribing" size="large" @click="handleSubscribe">立即订阅</el-button>
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

const LANG_COLORS = {
  JavaScript: '#f1e05a', TypeScript: '#3178c6', Python: '#3572A5', Java: '#b07219',
  Go: '#00ADD8', Rust: '#dea584', 'C++': '#f34b7d', C: '#555555', 'C#': '#178600',
  Ruby: '#701516', PHP: '#4F5D95', Swift: '#F05138', Kotlin: '#A97BFF', Dart: '#00B4AB',
  Shell: '#89e051', HTML: '#e34c26', CSS: '#563d7c', Vue: '#41b883', Zig: '#ec915c',
}

function langColor(lang) {
  return LANG_COLORS[lang] || '#656d76'
}

function rankClass(i) {
  if (i === 0) return 'rank-gold'
  if (i === 1) return 'rank-silver'
  if (i === 2) return 'rank-bronze'
  return ''
}

function formatNumber(n) {
  const num = parseInt(n) || 0
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'm'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return String(num)
}

function formatAnalysis(text) {
  if (!text) return ''
  return text
    .replace(/\n/g, '<br>')
    .replace(/【([^】]+)】/g, '<div class="analysis-section-title">$1</div>')
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
    ElMessage.success('订阅成功！每日早 8 点将推送热点日报')
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
.trending-page {
  max-width: 960px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.header-left {
  flex-shrink: 0;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
  color: #1f2328;
  margin: 0 0 4px;
}

.page-subtitle {
  font-size: 14px;
  color: #656d76;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.empty-state {
  padding: 80px 0;
}

/* ===== Repo Card ===== */
.repo-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.repo-card {
  display: flex;
  gap: 16px;
  background: #ffffff;
  border: 1px solid #d8dee4;
  border-radius: 10px;
  padding: 20px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.repo-card:hover {
  border-color: #2da44e50;
  box-shadow: 0 2px 12px rgba(45, 164, 78, 0.08);
}

.card-left {
  flex-shrink: 0;
}

.rank-badge {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f6f8fa;
  color: #656d76;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 15px;
  border: 1px solid #d8dee4;
}

.rank-badge.rank-gold {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  color: #92400e;
  border-color: #fcd34d;
}

.rank-badge.rank-silver {
  background: linear-gradient(135deg, #f3f4f6, #e5e7eb);
  color: #4b5563;
  border-color: #d1d5db;
}

.rank-badge.rank-bronze {
  background: linear-gradient(135deg, #fef2f2, #fed7aa);
  color: #9a3412;
  border-color: #fdba74;
}

.card-main {
  flex: 1;
  min-width: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.repo-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.repo-name {
  font-size: 17px;
  font-weight: 700;
  color: #1f2328;
  text-decoration: none;
}

.repo-name:hover {
  color: #2da44e;
}

.lang-tag {
  font-size: 12px;
  font-weight: 600;
  border-radius: 12px;
  padding: 0 10px;
  height: 22px;
  line-height: 22px;
}

.repo-stats {
  display: flex;
  gap: 14px;
  align-items: center;
  flex-shrink: 0;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #656d76;
  font-weight: 500;
}

.stat-item.trend-up .stat-value {
  color: #2da44e;
  font-weight: 700;
}

.stat-icon {
  flex-shrink: 0;
}

.repo-desc {
  margin: 8px 0 0;
  font-size: 13.5px;
  color: #656d76;
  line-height: 1.6;
}

/* ===== Analysis Block ===== */
.repo-analysis {
  margin-top: 14px;
  background: #f8faf9;
  border: 1px solid #e8ecf0;
  border-radius: 8px;
  padding: 14px 16px;
}

.analysis-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: #2da44e;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e8ecf0;
}

.analysis-body {
  font-size: 13.5px;
  color: #1f2328;
  line-height: 1.8;
}

.analysis-body :deep(.analysis-section-title) {
  font-weight: 700;
  color: #2da44e;
  font-size: 13px;
  margin-top: 10px;
  margin-bottom: 2px;
}

.analysis-body :deep(.analysis-section-title):first-child {
  margin-top: 0;
}

/* ===== Subscribe Dialog ===== */
.subscribe-hint {
  font-size: 13px;
  color: #656d76;
  margin: 0 0 16px;
}
</style>
