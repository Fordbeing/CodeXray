<template>
  <div class="trending-page">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">GitHub 热点</h1>
        <p class="page-subtitle">每日热门开源项目，AI 助你快速判断是否值得关注</p>
      </div>
      <div class="header-right">
        <el-radio-group v-model="lang" size="default" @change="onLangChange">
          <el-radio-button value="zh">中文</el-radio-button>
          <el-radio-button value="en">English</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- Tab + 工具栏 -->
    <div class="toolbar">
      <div class="tabs">
        <button class="tab" :class="{ active: activeTab === 'daily' }" @click="switchTab('daily')">
          <svg viewBox="0 0 16 16" width="16" height="16"><path fill="currentColor" d="M4.75 0a.75.75 0 0 1 .75.75V2h5V.75a.75.75 0 0 1 1.5 0V2h1.25c.966 0 1.75.784 1.75 1.75v10.5A1.75 1.75 0 0 1 13.25 16H2.75A1.75 1.75 0 0 1 1 14.25V3.75C1 2.784 1.784 2 2.75 2H4V.75A.75.75 0 0 1 4.75 0ZM2.5 7.5v6.75c0 .138.112.25.25.25h10.5a.25.25 0 0 0 .25-.25V7.5Zm1.5 1.5h2v2h-2Z"/></svg>
          日榜
        </button>
        <button class="tab" :class="{ active: activeTab === 'weekly' }" @click="switchTab('weekly')">
          <svg viewBox="0 0 16 16" width="16" height="16"><path fill="currentColor" d="M0 1.75C0 .784.784 0 1.75 0h12.5C15.216 0 16 .784 16 1.75v12.5A1.75 1.75 0 0 1 14.25 16H1.75A1.75 1.75 0 0 1 0 14.25ZM6.5 5h3a.5.5 0 0 1 .5.5v3a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-3a.5.5 0 0 1 .5-.5ZM5 6.5v3A1.5 1.5 0 0 0 6.5 11h3A1.5 1.5 0 0 0 11 9.5v-3A1.5 1.5 0 0 0 9.5 5h-3A1.5 1.5 0 0 0 5 6.5ZM1.75 2.5a.25.25 0 0 0-.25.25v10.5c0 .138.112.25.25.25h12.5a.25.25 0 0 0 .25-.25V2.75a.25.25 0 0 0-.25-.25Z"/></svg>
          周榜
        </button>
      </div>
      <div class="toolbar-actions">
        <el-date-picker
          v-if="activeTab === 'daily'"
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
        <el-button plain @click="handleSubscribeClick">
          <el-icon><Message /></el-icon>
          订阅日报
        </el-button>
        <el-button plain size="default" @click="showUnsubscribe = true">
          <el-icon><CloseBold /></el-icon>
          退订
        </el-button>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="trending-body" v-loading="loading">
      <div v-if="repos.length === 0 && !loading" class="empty-state">
        <el-empty :description="activeTab === 'weekly' ? '暂无周榜数据' : '暂无热点数据，点击刷新获取今日热点'" />
      </div>

      <!-- 左侧：列表 -->
      <div class="repo-list">
        <!-- ===== 日榜卡片 ===== -->
        <template v-if="activeTab === 'daily'">
          <div v-for="(repo, i) in repos" :key="'d-' + i" class="repo-card">
            <div class="card-left">
              <div class="rank-badge" :class="rankClass(i)">{{ i + 1 }}</div>
            </div>
            <div class="card-main">
              <div class="card-header">
                <div class="repo-title">
                  <a :href="repo.repoUrl" target="_blank" rel="noopener" class="repo-name">{{ repo.repoName }}</a>
                  <el-tag v-if="repo.language" size="small" class="lang-tag"
                    :style="{ background: langColor(repo.language) + '18', color: langColor(repo.language), borderColor: langColor(repo.language) + '40' }">
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
              <div class="card-actions">
                <el-button size="small" type="primary" plain @click="analyzeRepo(repo)">
                  <el-icon style="margin-right: 2px"><Search /></el-icon>
                  代码分析
                </el-button>
                <el-button size="small" @click="copyClone(repo)">
                  <el-icon style="margin-right: 2px"><CopyDocument /></el-icon>
                  Clone
                </el-button>
              </div>
              <div class="repo-analysis" v-if="repo.analysis">
                <div class="analysis-header">
                  <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#2da44e" d="M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"/></svg>
                  <span>AI 分析</span>
                </div>
                <div class="analysis-body" v-html="formatAnalysis(repo.analysis)"></div>
              </div>
            </div>
          </div>
        </template>

        <!-- ===== 周榜卡片 ===== -->
        <template v-if="activeTab === 'weekly'">
          <div v-for="(repo, i) in repos" :key="'w-' + i" class="repo-card weekly-card">
            <div class="card-left">
              <div class="rank-badge" :class="rankClass(i)">{{ i + 1 }}</div>
            </div>
            <div class="card-main">
              <div class="card-header">
                <div class="repo-title">
                  <a :href="repo.repoUrl" target="_blank" rel="noopener" class="repo-name">{{ repo.repoName }}</a>
                  <el-tag v-if="repo.language" size="small" class="lang-tag"
                    :style="{ background: langColor(repo.language) + '18', color: langColor(repo.language), borderColor: langColor(repo.language) + '40' }">
                    {{ repo.language }}
                  </el-tag>
                </div>
                <div class="weekly-badge" :class="weekBadgeClass(repo.daysCount)">
                  <svg viewBox="0 0 16 16" width="12" height="12"><path fill="currentColor" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>
                  上榜 {{ repo.daysCount }} 天
                </div>
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
                <div class="stat-item trend-up" v-if="repo.totalTodayStars">
                  <svg class="stat-icon" viewBox="0 0 16 16" width="14" height="14"><path fill="#2da44e" d="m4.427 7.427 3.396 3.396a.25.25 0 0 0 .354 0l3.396-3.396A.25.25 0 0 0 11.396 7H4.604a.25.25 0 0 0-.177.427Z"/></svg>
                  <span class="stat-value">+{{ formatNumber(repo.totalTodayStars) }} 周累计</span>
                </div>
              </div>
              <p class="repo-desc" v-if="repo.description">{{ repo.description }}</p>
              <div class="card-actions">
                <el-button size="small" type="primary" plain @click="analyzeRepo(repo)">
                  <el-icon style="margin-right: 2px"><Search /></el-icon>
                  代码分析
                </el-button>
                <el-button size="small" @click="copyClone(repo)">
                  <el-icon style="margin-right: 2px"><CopyDocument /></el-icon>
                  Clone
                </el-button>
              </div>
              <div class="repo-analysis" v-if="repo.analysis">
                <div class="analysis-header">
                  <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#2da44e" d="M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"/></svg>
                  <span>AI 分析</span>
                </div>
                <div class="analysis-body" v-html="formatAnalysis(repo.analysis)"></div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 右侧：信息面板 -->
      <div class="side-panel" v-if="repos.length > 0">
        <!-- Top 3 高亮 -->
        <div class="panel-card">
          <div class="panel-title">
            <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#e3b341" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>
            {{ activeTab === 'weekly' ? '周榜 Top 3' : 'Top 3 热门' }}
          </div>
          <div v-for="(repo, i) in repos.slice(0, 3)" :key="i" class="top-item">
            <div class="top-rank" :class="rankClass(i)">{{ i + 1 }}</div>
            <div class="top-info">
              <a :href="repo.repoUrl" target="_blank" class="top-name">{{ repo.repoName }}</a>
              <div class="top-meta">
                <span v-if="repo.stars" class="top-star">
                  <svg viewBox="0 0 16 16" width="11" height="11"><path fill="#e3b341" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>
                  {{ formatNumber(repo.stars) }}
                </span>
                <el-tag v-if="activeTab === 'weekly' && repo.daysCount" size="small" type="warning" effect="light">
                  {{ repo.daysCount }}天
                </el-tag>
                <el-tag v-if="repo.language" size="small" type="success" effect="light">{{ repo.language }}</el-tag>
              </div>
            </div>
          </div>
        </div>

        <!-- 语言分布 -->
        <div class="panel-card">
          <div class="panel-title">
            <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#656d76" d="M8 0C3.58 0 0 3.58 0 8s3.58 8 8 8 8-3.58 8-8-3.58-8-8-8Zm3.54 4.46a.75.75 0 0 1 0 1.06L9.3 7.75l2.24 2.23a.749.749 0 0 1-.53 1.28.749.749 0 0 1-.53-.22L8 8.5l-2.48 2.54a.75.75 0 0 1-1.06-1.06L6.7 7.75 4.46 5.52a.75.75 0 0 1 1.06-1.06L8 6.94l2.48-2.54a.75.75 0 0 1 1.06 0Z"/></svg>
            语言分布
          </div>
          <div class="lang-stats">
            <div v-for="item in langStats" :key="item.name" class="lang-row">
              <div class="lang-row-header">
                <span class="lang-row-name">
                  <span class="lang-dot" :style="{ background: langColor(item.name) }"></span>
                  {{ item.name }}
                </span>
                <span class="lang-row-count">{{ item.count }}</span>
              </div>
              <div class="lang-bar-bg">
                <div class="lang-bar-fill" :style="{ width: item.pct + '%', background: langColor(item.name) }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 数据概览 -->
        <div class="panel-card">
          <div class="panel-title">
            <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#656d76" d="M1.5 1.75V13.5h13.75a.75.75 0 0 1 0 1.5H.75a.75.75 0 0 1-.75-.75V1.75a.75.75 0 0 1 1.5 0Zm14.28 2.53-5.25 5.25a.75.75 0 0 1-1.06 0L7 7.06 4.28 9.78a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042l3.25-3.25a.75.75 0 0 1 1.06 0L10 7.94l4.72-4.72a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042Z"/></svg>
            数据概览
          </div>
          <div class="overview-grid">
            <div class="overview-item">
              <span class="overview-num">{{ repos.length }}</span>
              <span class="overview-label">{{ activeTab === 'weekly' ? '周榜项目' : '热门项目' }}</span>
            </div>
            <div class="overview-item">
              <span class="overview-num">{{ langStats.length }}</span>
              <span class="overview-label">编程语言</span>
            </div>
            <div class="overview-item">
              <span class="overview-num">{{ totalStars }}</span>
              <span class="overview-label">总 Stars</span>
            </div>
            <div class="overview-item">
              <span class="overview-num">{{ activeTab === 'weekly' ? weeklyMaxDays : totalTodayStars }}</span>
              <span class="overview-label">{{ activeTab === 'weekly' ? '最长上榜' : '今日新增' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 弹窗 ===== -->
    <el-dialog v-model="showSubscribe" title="订阅每日热点日报" width="420px" class="subscribe-dialog">
      <p class="subscribe-hint">每日早 8 点自动推送 GitHub 热点项目到您的邮箱</p>
      <div v-if="currentUser?.emailVerified" class="sub-email-info">
        <el-icon :size="18" color="#2da44e"><CircleCheck /></el-icon>
        <span>将发送至已绑定邮箱 <strong>{{ currentUser.email }}</strong></span>
      </div>
      <el-form v-else :model="subForm" label-position="top">
        <el-form-item label="邮箱地址">
          <el-input v-model="subForm.email" placeholder="your@email.com" size="large" />
        </el-form-item>
      </el-form>
      <el-form :model="subForm" label-position="top">
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

    <el-dialog v-model="showUnsubscribe" title="取消订阅" width="400px">
      <p class="subscribe-hint">输入您订阅时使用的邮箱地址，即可取消每日热点推送</p>
      <el-input v-model="unsubEmail" placeholder="your@email.com" size="large" />
      <template #footer>
        <el-button @click="showUnsubscribe = false">取消</el-button>
        <el-button type="danger" :loading="unsubscribing" @click="handleUnsubscribe">确认退订</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEmailBindHint" title="绑定邮箱" width="400px">
      <div class="bind-hint-content">
        <el-icon :size="40" color="#3b82f6"><Message /></el-icon>
        <p>订阅热点日报需要先绑定邮箱</p>
        <p class="bind-hint-sub">绑定后系统将使用该邮箱接收每日推送</p>
      </div>
      <template #footer>
        <el-button @click="showEmailBindHint = false">取消</el-button>
        <el-button type="primary" @click="openEmailBind">去绑定</el-button>
      </template>
    </el-dialog>

    <ProfileDialog v-model="showProfile" :user="currentUser" @success="onProfileSuccess" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { Search, CloseBold, CopyDocument, CircleCheck, Message, Refresh } from '@element-plus/icons-vue'
import { getTodayTrending, getTrendingByDate, getWeeklyTrending, refreshTrending, subscribeEmail, unsubscribeEmail } from '../api/trending'
import { marked } from 'marked'
import ProfileDialog from '../components/ProfileDialog.vue'
import { LANG_COLORS, formatNumber } from '../utils/format'

const router = useRouter()
marked.setOptions({ breaks: true, gfm: true, pedantic: false })

// ===== 状态 =====
const activeTab = ref('daily')
const repos = ref([])
const loading = ref(false)
const refreshing = ref(false)
const selectedDate = ref(null)
const lang = ref('zh')

const showSubscribe = ref(false)
const subscribing = ref(false)
const subForm = ref({ email: '', language: 'zh' })
const showUnsubscribe = ref(false)
const unsubscribing = ref(false)
const unsubEmail = ref('')
const currentUser = ref(null)
const showEmailBindHint = ref(false)
const showProfile = ref(false)

// ===== 工具函数 =====
function langColor(l) { return LANG_COLORS[l] || '#656d76' }
function rankClass(i) {
  if (i === 0) return 'rank-gold'
  if (i === 1) return 'rank-silver'
  if (i === 2) return 'rank-bronze'
  return ''
}
function weekBadgeClass(days) {
  if (days >= 5) return 'badge-hot'
  if (days >= 3) return 'badge-warm'
  return 'badge-normal'
}

const langStats = computed(() => {
  const counts = {}
  let total = 0
  for (const r of repos.value) {
    if (r.language) { counts[r.language] = (counts[r.language] || 0) + 1; total++ }
  }
  if (total === 0) return []
  return Object.entries(counts)
    .sort((a, b) => b[1] - a[1])
    .map(([name, count]) => ({ name, count, pct: Math.round((count / total) * 100) }))
})

const totalStars = computed(() => {
  const sum = repos.value.reduce((s, r) => s + (parseInt(r.stars) || 0), 0)
  return formatNumber(sum)
})

const totalTodayStars = computed(() => {
  const sum = repos.value.reduce((s, r) => s + (parseInt(r.todayStars) || 0), 0)
  return sum > 0 ? '+' + formatNumber(sum) : '-'
})

const weeklyMaxDays = computed(() => {
  if (activeTab.value !== 'weekly' || repos.value.length === 0) return '-'
  return Math.max(...repos.value.map(r => r.daysCount || 0)) + '天'
})

function formatAnalysis(text) {
  if (!text) return ''
  try { return marked.parse(text) }
  catch { return text.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>') }
}

// ===== 数据加载 =====
async function loadToday() {
  loading.value = true
  try { repos.value = await getTodayTrending(lang.value) }
  finally { loading.value = false }
}

async function loadByDate() {
  if (!selectedDate.value) return loadToday()
  loading.value = true
  try { repos.value = await getTrendingByDate(selectedDate.value, lang.value) }
  finally { loading.value = false }
}

async function loadWeekly() {
  loading.value = true
  try { repos.value = await getWeeklyTrending(lang.value) }
  finally { loading.value = false }
}

async function handleRefresh() {
  refreshing.value = true
  try {
    repos.value = await refreshTrending(lang.value)
    selectedDate.value = null
    if (activeTab.value === 'weekly') {
      await loadWeekly()
    }
    ElMessage.success('热点数据已更新')
  } finally { refreshing.value = false }
}

async function switchTab(tab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  selectedDate.value = null
  if (tab === 'daily') {
    await loadToday()
  } else {
    await loadWeekly()
  }
}

async function onLangChange() {
  if (activeTab.value === 'weekly') {
    await loadWeekly()
  } else if (selectedDate.value) {
    await loadByDate()
  } else {
    await loadToday()
  }
}

// ===== 订阅相关 =====
function handleSubscribeClick() {
  if (!currentUser.value) { ElMessage.warning('请先登录'); return }
  if (!currentUser.value.emailVerified) { showEmailBindHint.value = true; return }
  showSubscribe.value = true
}

async function handleSubscribe() {
  subscribing.value = true
  try {
    const email = currentUser.value?.emailVerified ? currentUser.value.email : subForm.value.email
    if (!email) { ElMessage.warning('请输入邮箱地址'); subscribing.value = false; return }
    await subscribeEmail(email, subForm.value.language)
    ElMessage.success('订阅成功！每日早 8 点将推送热点日报')
    showSubscribe.value = false
  } catch (e) {} finally { subscribing.value = false }
}

async function handleUnsubscribe() {
  if (!unsubEmail.value) { ElMessage.warning('请输入邮箱地址'); return }
  unsubscribing.value = true
  try {
    await unsubscribeEmail(unsubEmail.value)
    ElMessage.success('已成功取消订阅')
    showUnsubscribe.value = false; unsubEmail.value = ''
  } catch (e) {} finally { unsubscribing.value = false }
}

function analyzeRepo(repo) { router.push({ path: '/analyze', query: { url: repo.repoUrl } }) }

async function copyClone(repo) {
  try { await navigator.clipboard.writeText('git clone ' + repo.repoUrl); ElMessage.success('Clone 命令已复制') }
  catch { ElMessage.error('复制失败，请手动复制') }
}

function loadUser() {
  const raw = localStorage.getItem('codexray_user')
  currentUser.value = raw ? JSON.parse(raw) : null
}
function onAuthChange() { loadUser() }
function openEmailBind() { showEmailBindHint.value = false; showProfile.value = true }
function onProfileSuccess(data) {
  if (data) { currentUser.value = data; localStorage.setItem('codexray_user', JSON.stringify(data)) }
}

onMounted(() => { loadToday(); loadUser(); window.addEventListener('auth-change', onAuthChange) })
onUnmounted(() => { window.removeEventListener('auth-change', onAuthChange) })
</script>

<style scoped>
.trending-page {
  max-width: 1400px;
  margin: 0 auto;
}

/* ===== 页面头部 ===== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
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

/* ===== Tab + 工具栏 ===== */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.tabs {
  display: flex;
  gap: 2px;
  background: #f0f2f5;
  border-radius: 10px;
  padding: 3px;
}

.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #656d76;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.tab:hover {
  color: #1f2328;
  background: rgba(255,255,255,0.5);
}

.tab.active {
  background: #ffffff;
  color: #1f2328;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

.tab svg { flex-shrink: 0; }

.toolbar-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

/* ===== 布局 ===== */
.trending-body {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.repo-list {
  flex: 3;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.side-panel {
  flex: 1;
  min-width: 240px;
  max-width: 300px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: 28px;
}

.empty-state { padding: 80px 0; }

/* ===== 日榜卡片 ===== */
.repo-card {
  display: flex;
  gap: 16px;
  background: #ffffff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  padding: 20px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.repo-card:hover {
  border-color: #2da44e50;
  box-shadow: 0 2px 12px rgba(45, 164, 78, 0.08);
}

.card-left { flex-shrink: 0; }

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

.card-main { flex: 1; min-width: 0; }

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
.repo-name:hover { color: #2da44e; }

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
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #656d76;
  font-weight: 500;
}
.stat-item.trend-up .stat-value { color: #2da44e; font-weight: 700; }
.stat-icon { flex-shrink: 0; }

.repo-desc {
  margin: 8px 0 0;
  font-size: 13.5px;
  color: #656d76;
  line-height: 1.6;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

/* ===== 周榜徽章 ===== */
.weekly-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 12px;
  flex-shrink: 0;
}

.weekly-badge.badge-hot {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}
.weekly-badge.badge-warm {
  background: #fff7ed;
  color: #ea580c;
  border: 1px solid #fed7aa;
}
.weekly-badge.badge-normal {
  background: #f0f9ff;
  color: #0284c7;
  border: 1px solid #bae6fd;
}

.weekly-card .card-header {
  margin-bottom: 8px;
}

/* ===== AI 分析 ===== */
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

/* ===== 右侧面板 ===== */
.panel-card {
  background: #ffffff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  padding: 16px 18px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: #1f2328;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f2f5;
}

/* Top 3 */
.top-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 8px 0;
  border-bottom: 1px solid #f0f2f5;
}
.top-item:last-child { border-bottom: none; }

.top-rank {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: #f6f8fa;
  color: #656d76;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 11px;
  flex-shrink: 0;
}
.top-rank.rank-gold { background: linear-gradient(135deg, #fef3c7, #fde68a); color: #92400e; }
.top-rank.rank-silver { background: #f3f4f6; color: #4b5563; }
.top-rank.rank-bronze { background: linear-gradient(135deg, #fef2f2, #fed7aa); color: #9a3412; }

.top-info { min-width: 0; }
.top-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2328;
  text-decoration: none;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.top-name:hover { color: #2da44e; }

.top-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 3px;
  font-size: 11px;
  color: #8b949e;
}
.top-star { display: flex; align-items: center; gap: 3px; }

/* 语言分布 */
.lang-stats { display: flex; flex-direction: column; gap: 10px; }
.lang-row-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.lang-row-name {
  font-size: 12px;
  color: #1f2328;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 5px;
}
.lang-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.lang-row-count { font-size: 11px; color: #8b949e; }
.lang-bar-bg { height: 6px; background: #f0f2f5; border-radius: 3px; overflow: hidden; }
.lang-bar-fill { height: 100%; border-radius: 3px; transition: width 0.4s ease; }

/* 数据概览 */
.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.overview-item {
  text-align: center;
  padding: 10px 0;
  background: #f8faf9;
  border-radius: 8px;
}
.overview-num {
  display: block;
  font-size: 20px;
  font-weight: 800;
  color: #1f2328;
}
.overview-label {
  font-size: 11px;
  color: #8b949e;
  margin-top: 2px;
}

/* ===== 弹窗 ===== */
.subscribe-hint { font-size: 13px; color: #656d76; margin: 0 0 16px; }
.sub-email-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  font-size: 13px;
  color: #166534;
  margin-bottom: 12px;
}
.sub-email-info strong { color: #1f2328; }
.bind-hint-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0 10px;
  text-align: center;
}
.bind-hint-content p { margin: 0; font-size: 15px; color: #1f2328; }
.bind-hint-sub { font-size: 13px !important; color: #656d76 !important; }

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .side-panel { display: none; }
}

@media (max-width: 767px) {
  .page-header { flex-direction: column; gap: 12px; }
  .page-title { font-size: 22px; }
  .toolbar { flex-direction: column; align-items: stretch; }
  .toolbar-actions { width: 100%; }
  .repo-card { flex-direction: column; gap: 12px; padding: 16px; }
  .card-left { display: flex; }
  .rank-badge { width: 28px; height: 28px; font-size: 13px; border-radius: 6px; }
  .card-header { flex-direction: column; gap: 8px; }
  .repo-name { font-size: 15px; }
  .repo-stats { gap: 10px; }
  .repo-desc { font-size: 13px; }
  .repo-analysis { padding: 12px; }
}
</style>
