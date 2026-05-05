<template>
  <div class="github-page">
    <div class="page-header">
      <h1 class="page-title">
        <GitHubIcon :size="26" color="#1f2328" :style="{ marginRight: '8px', verticalAlign: '-4px' }" />
        我的 GitHub
      </h1>
      <div class="header-right">
        <div v-if="ghProfile" class="header-actions">
          <span v-if="autoRefreshSec !== null" class="refresh-countdown" title="下次自动刷新">
            <svg viewBox="0 0 16 16" width="12" height="12"><path fill="#8b949e" d="M1.643 3.143L.427 1.927A.25.25 0 0 0 0 2.104V5.75c0 .138.112.25.25.25h3.646a.25.25 0 0 0 .177-.427L2.715 4.215a6.5 6.5 0 1 1-1.18 4.458.75.75 0 1 0-1.493.154 8.001 8.001 0 1 0 1.6-5.684Z"/></svg>
            {{ autoRefreshSec }}s
          </span>
          <el-button text size="small" @click="handleRefresh" :loading="refreshing">
            <el-icon style="margin-right: 2px"><Refresh /></el-icon>刷新
          </el-button>
          <el-button text size="small" @click="resetGithub">更换用户</el-button>
        </div>
      </div>
    </div>

    <!-- 未输入用户名 -->
    <div v-if="!ghUsername && !ghLoading && !ghError" class="setup-card">
      <el-icon :size="48" color="#d0d7de"><Platform /></el-icon>
      <h2 class="setup-title">连接你的 GitHub</h2>
      <p class="setup-hint">输入 GitHub 用户名，查看仓库概览、动态事件、语言统计和技术雷达</p>
      <div class="setup-row">
        <el-input v-model="ghInput" placeholder="GitHub 用户名" size="large" clearable @keyup.enter="loadGithub">
          <template #prefix><GitHubIcon :size="14" /></template>
        </el-input>
        <el-button type="primary" size="large" :loading="ghLoading" @click="loadGithub">加载</el-button>
      </div>
      <p v-if="!user" class="setup-login-hint">登录后可自动关联 GitHub 账号</p>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="ghError" class="setup-card">
      <el-icon :size="48" color="#f87171"><CircleClose /></el-icon>
      <h2 class="setup-title">加载失败</h2>
      <p class="setup-hint">{{ ghError }}</p>
      <div class="setup-row">
        <el-input v-model="ghInput" placeholder="重新输入 GitHub 用户名" size="large" clearable @keyup.enter="loadGithub">
          <template #prefix><GitHubIcon :size="14" /></template>
        </el-input>
        <el-button type="primary" size="large" :loading="ghLoading" @click="loadGithub">重新加载</el-button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-else-if="ghLoading && !ghProfile" class="loading-card">
      <el-icon class="is-loading" :size="36" color="#2da44e"><Loading /></el-icon>
      <span>正在加载 GitHub 数据...</span>
    </div>

    <!-- ====================== 主内容 ====================== -->
    <template v-else-if="ghProfile">
      <!-- ===== 用户名片 ===== -->
      <div class="profile-card">
        <img :src="ghProfile.avatar" :alt="ghProfile.login" class="profile-avatar" />
        <div class="profile-body">
          <div class="profile-name-row">
            <span class="profile-name">{{ ghProfile.name || ghProfile.login }}</span>
            <span class="profile-login">@{{ ghProfile.login }}</span>
          </div>
          <div v-if="ghProfile.bio" class="profile-bio">{{ ghProfile.bio }}</div>
          <div class="profile-meta-row">
            <span v-if="ghProfile.company" class="profile-meta">
              <svg viewBox="0 0 16 16" width="13" height="13"><path fill="#8b949e" d="M1.75 16A1.75 1.75 0 0 1 0 14.25V1.75C0 .784.784 0 1.75 0h8.5C11.216 0 12 .784 12 1.75v12.5c0 .085-.006.168-.018.25h2.268a.75.75 0 0 1 0 1.5H1.75Zm10.232-1.5H12V1.75a.25.25 0 0 0-.25-.25h-8.5a.25.25 0 0 0-.25.25v12.5c0 .138.112.25.25.25h8.5Z"/></svg>
              {{ ghProfile.company }}
            </span>
            <span v-if="ghProfile.location" class="profile-meta">
              <el-icon :size="13"><Location /></el-icon>{{ ghProfile.location }}
            </span>
            <a v-if="ghProfile.blog" :href="ghProfile.blog.startsWith('http') ? ghProfile.blog : 'https://' + ghProfile.blog" target="_blank" class="profile-meta profile-link">
              <el-icon :size="13"><Link /></el-icon>{{ ghProfile.blog.replace(/^https?:\/\//, '') }}
            </a>
            <span v-if="ghProfile.createdAt" class="profile-meta">
              <el-icon :size="13"><Clock /></el-icon>加入 {{ accountAge }}
            </span>
          </div>
          <!-- 组织徽章 -->
          <div v-if="ghOrgs.length > 0" class="org-section">
            <span class="org-label">所属组织</span>
            <a v-for="org in ghOrgs" :key="org.login" :href="'https://github.com/' + org.login" target="_blank" class="org-badge" :title="org.description || org.login">
              <img :src="org.avatar" :alt="org.login" />
              <span>{{ org.login }}</span>
            </a>
          </div>
        </div>

        <!-- 核心数字 -->
        <div class="profile-stats">
          <div class="profile-stat">
            <span class="profile-stat-num">{{ ghProfile.publicRepos }}</span>
            <span class="profile-stat-label">仓库</span>
          </div>
          <div class="profile-stat">
            <span class="profile-stat-num">{{ ghProfile.followers }}</span>
            <span class="profile-stat-label">粉丝</span>
          </div>
          <div class="profile-stat">
            <span class="profile-stat-num">{{ ghProfile.following }}</span>
            <span class="profile-stat-label">关注</span>
          </div>
        </div>
      </div>

      <!-- ===== 缓存提示 ===== -->
      <div v-if="cacheTime" class="cache-hint">
        <svg viewBox="0 0 16 16" width="13" height="13"><path fill="#8b949e" d="M8 0a8 8 0 1 1 0 16A8 8 0 0 1 8 0Zm.5 4.75a.75.75 0 0 0-1.5 0v3.5c0 .414.336.75.75.75h2.5a.75.75 0 0 0 0-1.5H8.5ZM8 12a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z"/></svg>
        <span>数据缓存于 {{ formatCacheTime(cacheTime) }}，后台自动刷新。已配置 GitHub Token 可享 5000 次/小时配额</span>
      </div>

      <!-- ===== 统计面板 ===== -->
      <div class="stats-row">
        <div class="stat-mini-card" v-for="stat in statCards" :key="stat.label">
          <div class="stat-mini-icon" :style="{ background: stat.bg }">
            <component :is="stat.icon" v-if="typeof stat.icon === 'object'" v-bind="stat.iconProps || {}" />
            <el-icon v-else :size="18" :color="stat.color"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-mini-body">
            <div class="stat-mini-num">{{ stat.value }}</div>
            <div class="stat-mini-label">{{ stat.label }}</div>
          </div>
        </div>
      </div>

      <!-- ===== 语言分布 & 热力图区 ===== -->
      <div class="mid-row">
        <!-- 语言分布 -->
        <div class="lang-card">
          <div class="section-title">语言分布（按字节数）</div>
          <div class="lang-bar">
            <div v-for="lang in topLanguages" :key="lang.name" class="lang-segment"
              :style="{ width: lang.pct + '%', background: langColor(lang.name) }"
              :title="lang.name + ' ' + lang.pct + '% (' + formatBytes(lang.bytes) + ')'"></div>
          </div>
          <div class="lang-legend">
            <div v-for="lang in topLanguages.slice(0, 8)" :key="lang.name" class="lang-legend-item">
              <span class="lang-dot" :style="{ background: langColor(lang.name) }"></span>
              <span class="lang-name">{{ lang.name }}</span>
              <span class="lang-pct">{{ lang.pct }}%</span>
            </div>
          </div>
        </div>

        <!-- 贡献热力图 -->
        <div class="heatmap-card">
          <div class="section-title">贡献热力图（近 3 月 Push 活动）</div>
          <div class="heatmap-grid">
            <div v-for="(day, i) in heatmapData" :key="i"
              class="heatmap-cell"
              :style="{ background: heatmapColor(day.count) }"
              :title="day.date + ': ' + day.count + ' 次 Push'"></div>
          </div>
          <div class="heatmap-legend">
            <span>少</span>
            <span class="heatmap-swatch" style="background:#ebedf0"></span>
            <span class="heatmap-swatch" style="background:#9be9a8"></span>
            <span class="heatmap-swatch" style="background:#40c463"></span>
            <span class="heatmap-swatch" style="background:#30a14e"></span>
            <span class="heatmap-swatch" style="background:#216e39"></span>
            <span>多</span>
          </div>
        </div>
      </div>

      <!-- ===== 主三栏布局 ===== -->
      <div class="content-row">
        <!-- 左栏：动态时间线 -->
        <div class="side-left">
          <div class="panel-card">
            <div class="panel-title">
              <svg viewBox="0 0 16 16" width="14" height="14" style="margin-right:4px"><path fill="#656d76" d="M8 16A8 8 0 1 1 8 0a8 8 0 0 1 0 16Zm.5-4.75a.75.75 0 0 0-1.5 0v3.5a.75.75 0 0 0 1.5 0ZM8 8a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z"/></svg>
              近期动态
            </div>
            <div class="event-list">
              <div v-for="event in displayEvents" :key="event.id" class="event-item">
                <div class="event-icon" :style="{ background: eventIconBg(event.type) }" v-html="eventIcon(event.type)"></div>
                <div class="event-body">
                  <div class="event-text">
                    <a :href="event.repoUrl" target="_blank" class="event-repo">{{ event.repo }}</a>
                    <span class="event-desc">{{ eventDesc(event) }}</span>
                  </div>
                  <span class="event-time">{{ timeAgo(event.createdAt) }}</span>
                </div>
              </div>
              <div v-if="ghEvents.length > displayCount" class="event-more">
                <el-button text size="small" @click="displayCount += 20">加载更多动态</el-button>
              </div>
              <div v-if="ghEvents.length === 0" class="event-empty">暂无近期动态</div>
            </div>
          </div>
        </div>

        <!-- 中栏：仓库列表 -->
        <div class="content-center">
          <el-tabs v-model="activeTab" class="repo-tabs">
            <el-tab-pane :label="'仓库 (' + (repoStats ? repoStats.totalRepos : ghRepos.length) + ')'" name="repos">
              <div class="tab-toolbar">
                <el-input v-model="repoSearch" placeholder="搜索仓库..." prefix-icon="Search" clearable style="width: 220px" size="small" />
                <el-radio-group v-model="repoSort" size="small">
                  <el-radio-button value="updated">最近更新</el-radio-button>
                  <el-radio-button value="stars">最多 Star</el-radio-button>
                  <el-radio-button value="forks">最多 Fork</el-radio-button>
                  <el-radio-button value="size">最大体积</el-radio-button>
                </el-radio-group>
              </div>
              <div class="repo-list">
                <div v-for="repo in filteredRepos" :key="repo.name" class="repo-item">
                  <div class="repo-item-top">
                    <a :href="repo.url" target="_blank" class="repo-item-name">
                      <svg viewBox="0 0 16 16" width="16" height="16"><path fill="#656d76" d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8Z"/></svg>
                      {{ repo.name }}
                    </a>
                    <span v-if="repo.fork" class="fork-badge">fork</span>
                    <span v-if="repo.license" class="license-badge">{{ repo.license }}</span>
                  </div>
                  <p v-if="repo.description" class="repo-item-desc">{{ repo.description }}</p>
                  <div v-if="repo.topics && repo.topics.length > 0" class="repo-topics">
                    <span v-for="t in repo.topics.slice(0, 6)" :key="t" class="topic-tag">{{ t }}</span>
                  </div>
                  <div class="repo-item-meta">
                    <span v-if="repo.language" class="repo-lang">
                      <span class="lang-dot" :style="{ background: langColor(repo.language) }"></span>{{ repo.language }}
                    </span>
                    <span class="repo-stat-link">
                      <svg viewBox="0 0 16 16" width="13" height="13"><path fill="#e3b341" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>
                      {{ formatNumber(repo.stars) }}
                    </span>
                    <span class="repo-stat-link">
                      <svg viewBox="0 0 16 16" width="13" height="13"><path fill="#8b949e" d="M5 5.372v.878c0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75v-.878a2.25 2.25 0 1 1 1.5 0v.878a2.25 2.25 0 0 1-2.25 2.25h-1.5v2.128a2.251 2.251 0 1 1-1.5 0V8.5h-1.5A2.25 2.25 0 0 1 3.5 6.25v-.878a2.25 2.25 0 1 1 1.5 0ZM5 3.25a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Zm6.75.75a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5Zm-3 8.75a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Z"/></svg>
                      {{ formatNumber(repo.forks) }}
                    </span>
                    <span v-if="repo.homepage" class="repo-homepage">
                      <a :href="repo.homepage" target="_blank" title="项目主页">
                        <el-icon :size="12"><Link /></el-icon>
                      </a>
                    </span>
                    <span class="repo-updated">{{ formatRelative(repo.updatedAt) }}</span>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="收藏仓库" name="starred">
              <div v-if="ghStarred.length === 0 && !starredLoading" class="empty-hint">暂无收藏数据</div>
              <div v-else-if="starredLoading" class="loading-card" style="padding:40px">
                <el-icon class="is-loading" :size="24" color="#2da44e"><Loading /></el-icon>
              </div>
              <div v-else class="repo-list">
                <div v-for="repo in ghStarred" :key="repo.name" class="repo-item">
                  <div class="repo-item-top">
                    <a :href="repo.url" target="_blank" class="repo-item-name">
                      <svg viewBox="0 0 16 16" width="16" height="16"><path fill="#656d76" d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8Z"/></svg>
                      {{ repo.name }}
                    </a>
                  </div>
                  <p v-if="repo.description" class="repo-item-desc">{{ repo.description }}</p>
                  <div class="repo-item-meta">
                    <span v-if="repo.language" class="repo-lang">
                      <span class="lang-dot" :style="{ background: langColor(repo.language) }"></span>{{ repo.language }}
                    </span>
                    <span class="repo-stat-link">
                      <svg viewBox="0 0 16 16" width="13" height="13"><path fill="#e3b341" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>
                      {{ formatNumber(repo.stars) }}
                    </span>
                  </div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <!-- 右栏：排行面板 -->
        <div class="side-right">
          <!-- Star 排行 -->
          <div class="panel-card" v-if="topStarredRepos.length">
            <div class="panel-title">
              <svg viewBox="0 0 16 16" width="14" height="14" style="margin-right:4px"><path fill="#e3b341" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>
              Star 排行
            </div>
            <div v-for="(repo, i) in topStarredRepos" :key="repo.name" class="rank-item">
              <span class="rank-num" :class="{ 'rank-gold': i===0, 'rank-silver': i===1, 'rank-bronze': i===2 }">{{ i+1 }}</span>
              <div class="rank-info">
                <a :href="repo.url" target="_blank" class="rank-name">{{ repo.name }}</a>
                <div class="rank-bar-wrap">
                  <div class="rank-bar" :style="{ width: repo.starPct + '%', background: langColor(repo.language) || '#2da44e' }"></div>
                </div>
              </div>
              <span class="rank-value">{{ formatNumber(repo.stars) }}</span>
            </div>
          </div>

          <!-- 最大仓库 -->
          <div class="panel-card" v-if="biggestRepos.length">
            <div class="panel-title">
              <svg viewBox="0 0 16 16" width="14" height="14" style="margin-right:4px"><path fill="#8b949e" d="M6.354 5.5H4a3 3 0 0 0 0 6h3a3 3 0 0 0 2.83-4H9c-.086 0-.17.01-.25.031A2 2 0 0 1 7 10.5H4a2 2 0 1 1 0-4h1.535c.218-.376.495-.714.82-1Z"/></svg>
              最大仓库
            </div>
            <div v-for="repo in biggestRepos" :key="repo.name" class="simple-rank">
              <a :href="repo.url" target="_blank" class="simple-rank-name">{{ repo.name }}</a>
              <span class="simple-rank-val">{{ formatBytes(repo.size * 1024) }}</span>
            </div>
          </div>

          <!-- 仓库类型 & 统计 -->
          <div class="panel-card" v-if="repoStats">
            <div class="panel-title">仓库概览</div>
            <div class="type-row">
              <span class="type-label">原创</span>
              <div class="type-bar-wrap"><div class="type-bar" :style="{ width: originalPct + '%' }"></div></div>
              <span class="type-value">{{ repoStats.originalRepos }}</span>
            </div>
            <div class="type-row">
              <span class="type-label">Fork</span>
              <div class="type-bar-wrap"><div class="type-bar fork-bar" :style="{ width: (100 - originalPct) + '%' }"></div></div>
              <span class="type-value">{{ repoStats.forkedRepos }}</span>
            </div>
            <div v-if="repoStats.oldestRepo" class="insight-line">
              最早仓库 <a :href="'https://github.com/' + repoStats.oldestRepo" target="_blank">{{ repoStats.oldestRepo }}</a>
            </div>
          </div>

          <!-- 最近活跃 -->
          <div class="panel-card" v-if="recentActiveRepos.length">
            <div class="panel-title">最近活跃</div>
            <div v-for="repo in recentActiveRepos" :key="repo.name" class="active-item">
              <a :href="repo.url" target="_blank" class="active-name">{{ repo.name }}</a>
              <span class="active-time">{{ formatRelative(repo.updatedAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Location, Link, Clock, Loading, Platform, CircleClose, TrendCharts, CircleCheck, Refresh, Star, Folder, Connection } from '@element-plus/icons-vue'
import { getUserProfile, getUserRepos, getUserStarred, getUserEvents, getUserOrgs, getUserRepoStats } from '../api/github'
import { LANG_COLORS, formatNumber } from '../utils/format'
import GitHubIcon from '../components/icons/GitHubIcon.vue'

// ===== 状态 =====
const user = ref(null)
const ghInput = ref('')
const ghUsername = ref('')
const ghLoading = ref(false)
const ghProfile = ref(null)
const ghError = ref('')
const ghRepos = ref([])
const ghStarred = ref([])
const ghEvents = ref([])
const ghOrgs = ref([])
const repoStats = ref(null)
const activeTab = ref('repos')
const repoSearch = ref('')
const repoSort = ref('updated')
const cacheTime = ref(null)
const refreshing = ref(false)
const starredLoading = ref(false)
const displayCount = ref(20)

// ===== 自动刷新 =====
const autoRefreshSec = ref(null)
const REFRESH_INTERVAL = 300 // 5 分钟
let refreshTimer = null
let countdownTimer = null

function startAutoRefresh() {
  stopAutoRefresh()
  autoRefreshSec.value = REFRESH_INTERVAL
  countdownTimer = setInterval(() => {
    autoRefreshSec.value--
    if (autoRefreshSec.value <= 0) {
      autoRefreshSec.value = REFRESH_INTERVAL
      silentRefresh()
    }
  }, 1000)
  refreshTimer = setTimeout(() => {
    silentRefresh()
  }, REFRESH_INTERVAL * 1000)
}

function stopAutoRefresh() {
  if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null }
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  autoRefreshSec.value = null
}

async function silentRefresh() {
  if (!ghUsername.value || refreshing.value) return
  refreshing.value = true
  try {
    await Promise.all([
      loadProfileEvents(ghUsername.value),
      loadRepos(ghUsername.value)
    ])
    cacheTime.value = new Date()
    // Schedule next refresh
    autoRefreshSec.value = REFRESH_INTERVAL
    refreshTimer = setTimeout(() => silentRefresh(), REFRESH_INTERVAL * 1000)
  } catch {
    // Silent fail — don't bother user
  } finally {
    refreshing.value = false
  }
}

// ===== 工具函数 =====
function langColor(lang) { return LANG_COLORS[lang] || '#8b949e' }
function formatBytes(bytes) { if (bytes >= 1e9) return (bytes / 1e9).toFixed(1) + ' GB'; if (bytes >= 1e6) return (bytes / 1e6).toFixed(0) + ' MB'; if (bytes >= 1e3) return (bytes / 1e3).toFixed(0) + ' KB'; return bytes + ' B' }
function formatCacheTime(d) { if (!d) return ''; return new Date(d).toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' }) }

function formatRelative(dateStr) {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return mins + ' 分钟前'
  const hours = Math.floor(mins / 60)
  if (hours < 24) return hours + ' 小时前'
  const days = Math.floor(hours / 24)
  if (days < 30) return days + ' 天前'
  return Math.floor(days / 30) + ' 个月前'
}

function timeAgo(dateStr) {
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return mins + '分钟前'
  const hours = Math.floor(mins / 60)
  if (hours < 24) return hours + '小时前'
  return Math.floor(hours / 24) + '天前'
}

function calcAccountAge(dateStr) {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const years = Math.floor(diff / (365.25 * 86400000))
  const months = Math.floor((diff % (365.25 * 86400000)) / (30.44 * 86400000))
  if (years > 0) return years + ' 年' + (months > 0 ? months + ' 个月' : '')
  return months > 0 ? months + ' 个月' : '不到 1 个月'
}

// ===== 事件图标 =====
function eventIcon(type) {
  const map = {
    PushEvent: '<svg viewBox="0 0 16 16" width="12" height="12"><path fill="#fff" d="M3.5 3.5V2a.5.5 0 0 1 .5-.5h8a.5.5 0 0 1 .5.5v1.5a.5.5 0 0 1-.5.5H10a.5.5 0 0 0 0 1h2a.5.5 0 0 1 .5.5V10a.5.5 0 0 1-.5.5h-2v1.5a.5.5 0 0 1-.854.354L6.854 10.06a.5.5 0 0 1 0-.708L9.146 7.06A.5.5 0 0 1 10 7.414V9h1.5V5.5H6.5V7a.5.5 0 0 1-1 0V4a.5.5 0 0 1 .5-.5H8V2H4.5v1.5a.5.5 0 0 1-1 0Z"/></svg>',
    PullRequestEvent: '<svg viewBox="0 0 16 16" width="12" height="12"><path fill="#fff" d="M7.177 3.073L9.573.677A.25.25 0 0 1 10 .854v4.792a.25.25 0 0 1-.427.177L7.177 3.427a.25.25 0 0 1 0-.354ZM3.75 2.5a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5Zm-2.25.75a2.25 2.25 0 1 1 3 2.122v5.256a2.251 2.251 0 1 1-1.5 0V5.372A2.25 2.25 0 0 1 1.5 3.25Zm11.5 8.5a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5Zm-2.25.75a2.25 2.25 0 0 1 3-2.122V5.372a2.25 2.25 0 1 1-1.5 0v5.256a2.251 2.251 0 0 1-1.5 2.122Z"/></svg>',
    IssuesEvent: '<svg viewBox="0 0 16 16" width="12" height="12"><path fill="#fff" d="M8 9.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z"/><path fill="#fff" d="M8 0a8 8 0 1 1 0 16A8 8 0 0 1 8 0ZM1.5 8a6.5 6.5 0 1 0 13 0 6.5 6.5 0 0 0-13 0Z"/></svg>',
    WatchEvent: '<svg viewBox="0 0 16 16" width="12" height="12"><path fill="#fff" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>',
    ForkEvent: '<svg viewBox="0 0 16 16" width="12" height="12"><path fill="#fff" d="M5 5.372v.878c0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75v-.878a2.25 2.25 0 1 1 1.5 0v.878a2.25 2.25 0 0 1-2.25 2.25h-1.5v2.128a2.251 2.251 0 1 1-1.5 0V8.5h-1.5A2.25 2.25 0 0 1 3.5 6.25v-.878a2.25 2.25 0 1 1 1.5 0ZM5 3.25a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Zm6.75.75a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5Zm-3 8.75a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Z"/></svg>',
    CreateEvent: '<svg viewBox="0 0 16 16" width="12" height="12"><path fill="#fff" d="M7.25 2a.75.75 0 0 1 .75.75V7h4.25a.75.75 0 0 1 0 1.5H8v4.25a.75.75 0 0 1-1.5 0V8.5H2.25a.75.75 0 0 1 0-1.5H6.5V2.75A.75.75 0 0 1 7.25 2Z"/></svg>',
    DeleteEvent: '<svg viewBox="0 0 16 16" width="12" height="12"><path fill="#fff" d="M6.5 1.75a.25.25 0 0 1 .25-.25h2.5a.25.25 0 0 1 .25.25V3h-3V1.75Zm4.5 0V3h2.25a.75.75 0 0 1 0 1.5h-.507l-.632 10.11A1.75 1.75 0 0 1 10.369 16h-4.74a1.75 1.75 0 0 1-1.742-1.39L3.257 4.5H2.75a.75.75 0 0 1 0-1.5H5V1.75C5 .784 5.784 0 6.75 0h2.5C10.216 0 11 .784 11 1.75Z"/></svg>',
    MemberEvent: '<svg viewBox="0 0 16 16" width="12" height="12"><path fill="#fff" d="M5.5 3.5a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM2 5.5a3.5 3.5 0 1 1 5.898 2.549 5.508 5.508 0 0 1 3.034 4.084.75.75 0 1 1-1.482.235 4.001 4.001 0 0 0-7.9 0 .75.75 0 0 1-1.482-.236A5.507 5.507 0 0 1 3.102 8.05 3.493 3.493 0 0 1 2 5.5ZM11 4a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z"/></svg>',
    PublicEvent: '<svg viewBox="0 0 16 16" width="12" height="12"><path fill="#fff" d="M8 0a8 8 0 1 1 0 16A8 8 0 0 1 8 0ZM1.5 8a6.5 6.5 0 1 0 13 0 6.5 6.5 0 0 0-13 0Zm5-3.75a.75.75 0 0 1 .75-.75h1.5a.75.75 0 0 1 .75.75v1.5a.75.75 0 0 1-.75.75h-1.5a.75.75 0 0 1-.75-.75v-1.5Zm0 5a.75.75 0 0 1 .75-.75h1.5a.75.75 0 0 1 .75.75v1.5a.75.75 0 0 1-.75.75h-1.5a.75.75 0 0 1-.75-.75v-1.5Z"/></svg>'
  }
  return map[type] || map.PushEvent
}

function eventIconBg(type) {
  const map = {
    PushEvent: '#2da44e', PullRequestEvent: '#1f883d', IssuesEvent: '#1a7f37',
    WatchEvent: '#e3b341', ForkEvent: '#8b949e', CreateEvent: '#58a6ff',
    DeleteEvent: '#f85149', MemberEvent: '#8250df', PublicEvent: '#58a6ff'
  }
  return map[type] || '#656d76'
}

function eventDesc(event) {
  const repoName = event.repo.split('/').pop()
  switch (event.type) {
    case 'PushEvent': return `向 ${event.payload.ref?.replace('refs/heads/', '') || ''} 推送了 ${event.payload.commits?.length || 0} 个提交`
    case 'PullRequestEvent': return `${event.payload.action} PR #${event.payload.pull_request?.number || ''}`
    case 'IssuesEvent': return `${event.payload.action} Issue #${event.payload.issue?.number || ''}`
    case 'WatchEvent': return 'Star 了 ' + repoName
    case 'ForkEvent': return 'Fork 了 ' + repoName
    case 'CreateEvent': return `创建了 ${event.payload.ref_type || ''} ${event.payload.ref || ''}`
    case 'DeleteEvent': return `删除了 ${event.payload.ref_type || ''} ${event.payload.ref || ''}`
    case 'MemberEvent': return `${event.payload.action} ${event.payload.member?.login || ''}`
    case 'PublicEvent': return '开源了 ' + repoName
    default: return event.type.replace('Event', '')
  }
}

// ===== 社区热力图数据 =====
const heatmapData = computed(() => {
  const days = []
  const now = new Date()
  const pushMap = {}
  ghEvents.value.filter(e => e.type === 'PushEvent').forEach(e => {
    const d = new Date(e.createdAt).toISOString().slice(0, 10)
    pushMap[d] = (pushMap[d] || 0) + (e.payload?.commits?.length || 1)
  })
  for (let i = 89; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const key = d.toISOString().slice(0, 10)
    days.push({ date: key, count: pushMap[key] || 0 })
  }
  return days
})

function heatmapColor(count) {
  if (count === 0) return '#ebedf0'
  if (count <= 2) return '#9be9a8'
  if (count <= 5) return '#40c463'
  if (count <= 10) return '#30a14e'
  return '#216e39'
}

// ===== 统计卡片 =====
const topLanguages = computed(() => {
  if (repoStats.value?.topLanguages) {
    const langs = repoStats.value.topLanguages
    const total = langs.reduce((s, l) => s + l.bytes, 0) || 1
    return langs.map(l => ({ ...l, pct: Math.round(l.bytes / total * 100) }))
  }
  // Fallback: from repos
  const counts = {}; let total = 0
  ghRepos.value.forEach(r => { if (r.language) { counts[r.language] = (counts[r.language] || 0) + 1; total++ } })
  return Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 10)
    .map(([name, count]) => ({ name, pct: Math.round(count / total * 100), bytes: 0, repos: count }))
})

const statCards = computed(() => {
  const stats = repoStats.value
  return [
    { label: '总 Stars', value: formatNumber(stats?.totalStars ?? totalStars.value), bg: 'linear-gradient(135deg,#fef3c7,#fde68a)', color: '#92400e', icon: Star },
    { label: '总 Forks', value: formatNumber(stats?.totalForks ?? totalForks.value), bg: 'linear-gradient(135deg,#f0fdf4,#dcfce7)', color: '#2da44e', icon: Connection },
    { label: '总 Issues', value: formatNumber(stats?.totalIssues ?? 0), bg: 'linear-gradient(135deg,#eff6ff,#dbeafe)', color: '#3b82f6', icon: TrendCharts },
    { label: '仓库大小', value: formatBytes((stats?.totalSize ?? 0) * 1024), bg: 'linear-gradient(135deg,#f5f3ff,#ede9fe)', color: '#8b5cf6', icon: Folder },
    { label: '原创仓库', value: stats?.originalRepos ?? originalRepos.value, bg: 'linear-gradient(135deg,#fef2f2,#fecaca)', color: '#ef4444', icon: CircleCheck },
    { label: '总仓库数', value: stats?.totalRepos ?? ghRepos.value.length, bg: 'linear-gradient(135deg,#f0fdf4,#a7f3d0)', color: '#059669', icon: Platform },
  ]
})

// ===== 仓库计算 =====
const totalStars = computed(() => ghRepos.value.reduce((s, r) => s + (r.stars || 0), 0))
const totalForks = computed(() => ghRepos.value.reduce((s, r) => s + (r.forks || 0), 0))
const originalRepos = computed(() => ghRepos.value.filter(r => !r.fork).length)
const originalPct = computed(() => ghRepos.value.length ? Math.round(originalRepos.value / ghRepos.value.length * 100) : 0)

const filteredRepos = computed(() => {
  let list = ghRepos.value
  if (repoSearch.value) {
    const q = repoSearch.value.toLowerCase()
    list = list.filter(r => r.name.toLowerCase().includes(q) || (r.description || '').toLowerCase().includes(q))
  }
  if (repoSort.value === 'stars') list = [...list].sort((a, b) => (b.stars||0) - (a.stars||0))
  else if (repoSort.value === 'forks') list = [...list].sort((a, b) => (b.forks||0) - (a.forks||0))
  else if (repoSort.value === 'size') list = [...list].sort((a, b) => (b.size||0) - (a.size||0))
  return list
})

const topStarredRepos = computed(() => {
  const sorted = [...ghRepos.value].filter(r => r.stars > 0).sort((a, b) => b.stars - a.stars).slice(0, 5)
  const max = sorted[0]?.stars || 1
  return sorted.map(r => ({ ...r, starPct: Math.round(r.stars / max * 100) }))
})

const biggestRepos = computed(() =>
  [...ghRepos.value].sort((a, b) => (b.size||0) - (a.size||0)).slice(0, 5)
)

const recentActiveRepos = computed(() =>
  [...ghRepos.value].filter(r => r.updatedAt).sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt)).slice(0, 5)
)

const displayEvents = computed(() => ghEvents.value.slice(0, displayCount.value))

// ===== 缓存 =====
const GH_CACHE_KEY = 'codexray_gh_cache_v2'

function saveGhCache(username) {
  try {
    localStorage.setItem(GH_CACHE_KEY, JSON.stringify({
      username,
      profile: ghProfile.value,
      repos: ghRepos.value,
      events: ghEvents.value,
      orgs: ghOrgs.value,
      stats: repoStats.value,
      time: Date.now()
    }))
  } catch { /* ignore */ }
}

function loadGhCache(username) {
  try {
    const raw = localStorage.getItem(GH_CACHE_KEY)
    if (!raw) return null
    const c = JSON.parse(raw)
    if (c.username !== username) return null
    return c
  } catch { return null }
}

function loadFromCache(username) {
  const c = loadGhCache(username)
  if (!c) return false
  ghUsername.value = username
  ghProfile.value = c.profile
  ghRepos.value = c.repos || []
  ghEvents.value = c.events || []
  ghOrgs.value = c.orgs || []
  repoStats.value = c.stats || null
  cacheTime.value = new Date(c.time)
  return true
}

// ===== API 调用 =====
async function loadGithub(username) {
  const name = username || ghInput.value.trim()
  if (!name) { ElMessage.warning('请输入 GitHub 用户名'); return }
  ghUsername.value = name
  ghLoading.value = true
  ghError.value = ''
  try {
    const [profile, repos, events, orgs, stats] = await Promise.allSettled([
      getUserProfile(name),
      getUserRepos(name, 100),
      getUserEvents(name, 100),
      getUserOrgs(name),
      getUserRepoStats(name)
    ])
    if (profile.status === 'rejected') throw new Error(profile.reason?.message || '获取用户信息失败')
    ghProfile.value = profile.value
    ghRepos.value = repos.status === 'fulfilled' ? repos.value : []
    ghEvents.value = events.status === 'fulfilled' ? events.value : []
    ghOrgs.value = orgs.status === 'fulfilled' ? orgs.value : []
    repoStats.value = stats.status === 'fulfilled' ? stats.value : null
    cacheTime.value = new Date()
    saveGhCache(name)
    localStorage.setItem('codexray_gh_user', name)
    startAutoRefresh()
  } catch (e) {
    const msg = e?.message || ''
    ghError.value = msg.includes('404') || msg.includes('Not Found')
      ? '用户 "' + name + '" 不存在'
      : '加载失败：' + (msg || '网络错误')
    ghProfile.value = null
    ghRepos.value = []
    ghUsername.value = ''
    stopAutoRefresh()
  } finally {
    ghLoading.value = false
  }
}

async function loadProfileEvents(username) {
  const [profile, events, orgs, stats] = await Promise.allSettled([
    getUserProfile(username), getUserEvents(username, 100), getUserOrgs(username), getUserRepoStats(username)
  ])
  if (profile.status === 'fulfilled') ghProfile.value = profile.value
  if (events.status === 'fulfilled') ghEvents.value = events.value
  if (orgs.status === 'fulfilled') ghOrgs.value = orgs.value
  if (stats.status === 'fulfilled') repoStats.value = stats.value
  saveGhCache(username)
}

async function loadRepos(username) {
  try { ghRepos.value = await getUserRepos(username, 100) } catch { /* ignore */ }
  saveGhCache(username)
}

async function loadStarred() {
  if (!ghUsername.value) return
  starredLoading.value = true
  try { ghStarred.value = await getUserStarred(ghUsername.value, 30) }
  catch { ghStarred.value = [] }
  finally { starredLoading.value = false }
}

async function handleRefresh() {
  if (!ghUsername.value) return
  refreshing.value = true
  try {
    // Clear cache first
    stopAutoRefresh()
    localStorage.removeItem(GH_CACHE_KEY)
    await loadGithub(ghUsername.value)
    ElMessage.success('数据已刷新')
  } catch { ElMessage.error('刷新失败') }
  finally { refreshing.value = false }
}

function resetGithub() {
  stopAutoRefresh()
  ghProfile.value = null; ghRepos.value = []; ghStarred.value = []; ghEvents.value = []; ghOrgs.value = []
  ghUsername.value = ''; ghInput.value = ''; ghError.value = ''; repoStats.value = null
  localStorage.removeItem('codexray_gh_user'); localStorage.removeItem(GH_CACHE_KEY)
}

// ===== 初始化 =====
function loadUser() {
  const saved = localStorage.getItem('codexray_user')
  if (saved) { try { user.value = JSON.parse(saved) } catch { /* ignore */ } }
}

async function initFromUser() {
  const ghUser = user.value?.githubUsername || localStorage.getItem('codexray_gh_user')
  if (ghUser && !ghProfile.value) {
    ghInput.value = ghUser
    if (loadFromCache(ghUser)) { startAutoRefresh(); return }
    if (user.value?.githubUsername) await loadGithub(ghUser)
  }
}

function onAuthChange(e) {
  if (!e.detail) {
    ghProfile.value = null; ghRepos.value = []; ghStarred.value = []; ghEvents.value = []; ghOrgs.value = []
    ghUsername.value = ''; ghInput.value = ''; ghError.value = ''; repoStats.value = null
    localStorage.removeItem('codexray_gh_user'); localStorage.removeItem(GH_CACHE_KEY)
    stopAutoRefresh()
  } else {
    loadUser(); initFromUser()
  }
}

watch(activeTab, (tab) => { if (tab === 'starred' && ghStarred.value.length === 0) loadStarred() })

onMounted(() => {
  loadUser()
  window.addEventListener('auth-change', onAuthChange)
  initFromUser()
})

onUnmounted(() => {
  window.removeEventListener('auth-change', onAuthChange)
  stopAutoRefresh()
})
</script>

<style scoped>
.github-page { max-width: 1400px; margin: 0 auto; }

.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; flex-wrap: wrap; gap: 12px;
}
.page-title { font-size: 24px; font-weight: 700; color: #1f2328; margin: 0; display: flex; align-items: center; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.refresh-countdown {
  display: flex; align-items: center; gap: 3px; font-size: 11px;
  color: #8b949e; font-family: 'SF Mono', monospace; min-width: 28px;
}

/* 设置卡片 */
.setup-card {
  display: flex; flex-direction: column; align-items: center; padding: 60px 20px;
  background: #fff; border: 1px dashed #d0d7de; border-radius: 16px;
}
.setup-title { font-size: 20px; font-weight: 700; color: #1f2328; margin: 16px 0 8px; }
.setup-hint { font-size: 14px; color: #656d76; margin: 0 0 24px; text-align: center; }
.setup-row { display: flex; gap: 10px; width: 100%; max-width: 400px; }
.setup-row :deep(.el-input) { flex: 1; }
.setup-login-hint { font-size: 12px; color: #a8a8a8; margin-top: 16px; }
.loading-card { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 80px 0; color: #656d76; }

/* 用户名片 */
.profile-card {
  display: flex; align-items: flex-start; gap: 24px;
  background: #fff; border: 1px solid #d8dee4; border-radius: 16px; padding: 28px; margin-bottom: 16px;
}
.profile-avatar { width: 88px; height: 88px; border-radius: 50%; border: 3px solid #e8ecf0; flex-shrink: 0; }
.profile-body { flex: 1; min-width: 0; }
.profile-name-row { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.profile-name { font-size: 22px; font-weight: 800; color: #1f2328; }
.profile-login { font-size: 14px; color: #656d76; }
.profile-bio { font-size: 14px; color: #656d76; margin-top: 6px; line-height: 1.6; }
.profile-meta-row { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 10px; }
.profile-meta { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #8b949e; }
.profile-link { color: #2da44e; text-decoration: none; }
.profile-link:hover { text-decoration: underline; }

.org-section { display: flex; align-items: center; gap: 8px; margin-top: 12px; flex-wrap: wrap; }
.org-label { font-size: 12px; color: #8b949e; font-weight: 600; }
.org-badge { display: flex; align-items: center; gap: 4px; background: #f6f8fa; border: 1px solid #d0d7de; border-radius: 16px; padding: 3px 10px 3px 4px; text-decoration: none; transition: border-color .15s; }
.org-badge:hover { border-color: #2da44e; }
.org-badge img { width: 18px; height: 18px; border-radius: 50%; }
.org-badge span { font-size: 12px; color: #1f2328; font-weight: 500; }

.profile-stats { display: flex; gap: 0; margin-left: auto; padding: 16px 0; border-left: 1px solid #f0f2f5; padding-left: 24px; flex-shrink: 0; }
.profile-stat { text-align: center; padding: 0 16px; border-right: 1px solid #f0f2f5; }
.profile-stat:last-child { border-right: none; }
.profile-stat-num { display: block; font-size: 26px; font-weight: 800; color: #1f2328; }
.profile-stat-label { font-size: 13px; color: #8b949e; }

/* 缓存提示 */
.cache-hint {
  display: flex; align-items: center; gap: 6px; padding: 8px 14px;
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px;
  font-size: 12px; color: #8b949e; margin-bottom: 16px;
}
.cache-hint span { flex: 1; }

/* 统计面板 */
.stats-row { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.stat-mini-card {
  flex: 1; min-width: 150px; display: flex; align-items: center; gap: 12px;
  background: #fff; border: 1px solid #d8dee4; border-radius: 12px; padding: 14px 16px;
  transition: box-shadow .2s, border-color .2s;
}
.stat-mini-card:hover { border-color: #2da44e40; box-shadow: 0 2px 8px rgba(0,0,0,.04); }
.stat-mini-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.stat-mini-num { font-size: 20px; font-weight: 800; color: #1f2328; line-height: 1.2; }
.stat-mini-label { font-size: 12px; color: #8b949e; margin-top: 1px; }

/* 语言 & 热力图区 */
.mid-row { display: flex; gap: 16px; margin-bottom: 20px; }
.lang-card, .heatmap-card {
  background: #fff; border: 1px solid #d8dee4; border-radius: 14px; padding: 18px 20px;
}
.lang-card { flex: 1; min-width: 0; }
.heatmap-card { flex: 1; min-width: 0; }
.section-title { font-size: 13px; font-weight: 700; color: #1f2328; margin-bottom: 12px; }

.lang-bar { display: flex; height: 12px; border-radius: 6px; overflow: hidden; gap: 1px; }
.lang-segment { min-width: 4px; transition: width .4s; }
.lang-legend { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 10px; }
.lang-legend-item { display: flex; align-items: center; gap: 5px; font-size: 12px; color: #656d76; }
.lang-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.lang-name { font-weight: 600; }
.lang-pct { color: #a8a8a8; }

.heatmap-grid { display: flex; flex-wrap: wrap; gap: 2px; }
.heatmap-cell { width: 12px; height: 12px; border-radius: 2px; }
.heatmap-legend { display: flex; align-items: center; gap: 4px; margin-top: 10px; font-size: 11px; color: #8b949e; }
.heatmap-swatch { width: 12px; height: 12px; border-radius: 2px; display: inline-block; }

/* 三栏布局 */
.content-row { display: flex; gap: 16px; align-items: flex-start; }
.side-left { width: 280px; flex-shrink: 0; }
.content-center { flex: 1; min-width: 0; }
.side-right { width: 260px; flex-shrink: 0; }

.panel-card {
  background: #fff; border: 1px solid #d8dee4; border-radius: 14px; padding: 16px; margin-bottom: 16px;
}
.panel-title {
  font-size: 13px; font-weight: 700; color: #1f2328; margin-bottom: 12px;
  padding-bottom: 10px; border-bottom: 1px solid #f0f2f5; display: flex; align-items: center;
}

/* 事件列表 */
.event-list { max-height: 600px; overflow-y: auto; }
.event-item { display: flex; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f0f2f5; }
.event-item:last-child { border-bottom: none; }
.event-icon { width: 22px; height: 22px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.event-body { flex: 1; min-width: 0; }
.event-text { font-size: 12px; line-height: 1.5; color: #1f2328; }
.event-repo { font-weight: 600; color: #2da44e; text-decoration: none; }
.event-repo:hover { text-decoration: underline; }
.event-desc { color: #656d76; margin-left: 4px; }
.event-time { font-size: 11px; color: #a8a8a8; }
.event-more { text-align: center; padding: 8px 0; }
.event-empty { text-align: center; color: #8b949e; font-size: 13px; padding: 32px 0; }

/* 仓库 */
.repo-tabs { background: #fff; border: 1px solid #d8dee4; border-radius: 14px; padding: 16px 20px; }
.tab-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.repo-list { display: flex; flex-direction: column; gap: 10px; }
.repo-item { padding: 14px 16px; background: #f8faf9; border: 1px solid #e8ecf0; border-radius: 10px; transition: border-color .2s; }
.repo-item:hover { border-color: #2da44e40; }
.repo-item-top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.repo-item-name { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 700; color: #1f2328; text-decoration: none; }
.repo-item-name:hover { color: #2da44e; }
.fork-badge { font-size: 10px; padding: 1px 8px; border-radius: 10px; background: #e8ecf0; color: #8b949e; }
.license-badge { font-size: 10px; padding: 1px 6px; border-radius: 10px; background: #f0fdf4; color: #2da44e; border: 1px solid #2da44e33; }
.repo-item-desc { font-size: 13px; color: #656d76; margin: 6px 0 0; line-height: 1.5; }
.repo-topics { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
.topic-tag { font-size: 11px; padding: 2px 8px; background: #eff6ff; color: #3b82f6; border-radius: 10px; }
.repo-item-meta { display: flex; gap: 14px; align-items: center; margin-top: 8px; font-size: 12px; color: #8b949e; }
.repo-lang, .repo-stat-link { display: flex; align-items: center; gap: 4px; }
.repo-homepage a { color: #8b949e; display: flex; }
.repo-homepage a:hover { color: #2da44e; }
.repo-updated { margin-left: auto; }

/* 排行面板 */
.rank-item { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px solid #f0f2f5; }
.rank-item:last-child { border-bottom: none; }
.rank-num {
  width: 20px; height: 20px; border-radius: 5px; background: #f6f8fa; color: #656d76;
  display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 11px; flex-shrink: 0;
}
.rank-num.rank-gold { background: linear-gradient(135deg,#fef3c7,#fde68a); color: #92400e; }
.rank-num.rank-silver { background: #f3f4f6; color: #4b5563; }
.rank-num.rank-bronze { background: linear-gradient(135deg,#fef2f2,#fed7aa); color: #9a3412; }
.rank-info { flex: 1; min-width: 0; }
.rank-name { font-size: 12px; font-weight: 600; color: #1f2328; text-decoration: none; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-name:hover { color: #2da44e; }
.rank-bar-wrap { height: 4px; background: #f0f2f5; border-radius: 2px; margin-top: 3px; overflow: hidden; }
.rank-bar { height: 100%; border-radius: 2px; transition: width .6s; }
.rank-value { font-size: 12px; font-weight: 700; color: #656d76; flex-shrink: 0; }

.simple-rank { display: flex; justify-content: space-between; padding: 7px 0; border-bottom: 1px solid #f0f2f5; }
.simple-rank:last-child { border-bottom: none; }
.simple-rank-name { font-size: 12px; font-weight: 600; color: #1f2328; text-decoration: none; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.simple-rank-name:hover { color: #2da44e; }
.simple-rank-val { font-size: 12px; color: #656d76; flex-shrink: 0; }

.type-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.type-label { font-size: 12px; color: #656d76; width: 36px; }
.type-bar-wrap { flex: 1; height: 6px; background: #f0f2f5; border-radius: 3px; overflow: hidden; }
.type-bar { height: 100%; background: #2da44e; border-radius: 3px; transition: width .4s; }
.type-bar.fork-bar { background: #8b949e; }
.type-value { font-size: 13px; font-weight: 700; color: #1f2328; }

.insight-line { font-size: 12px; color: #656d76; margin-top: 10px; padding-top: 10px; border-top: 1px solid #f0f2f5; }
.insight-line a { color: #2da44e; text-decoration: none; }
.insight-line a:hover { text-decoration: underline; }

.active-item { display: flex; justify-content: space-between; align-items: center; padding: 7px 0; border-bottom: 1px solid #f0f2f5; }
.active-item:last-child { border-bottom: none; }
.active-name { font-size: 12px; font-weight: 600; color: #1f2328; text-decoration: none; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.active-name:hover { color: #2da44e; }
.active-time { font-size: 11px; color: #8b949e; flex-shrink: 0; margin-left: 8px; }
.empty-hint { color: #8b949e; font-size: 13px; text-align: center; padding: 40px 0; }

/* 响应式 */
@media (max-width: 1200px) {
  .side-left { display: none; }
  .content-row { flex-direction: column; }
  .side-right { width: 100%; max-width: none; display: flex; flex-wrap: wrap; gap: 12px; }
  .side-right .panel-card { flex: 1; min-width: 220px; margin-bottom: 0; }
}
@media (max-width: 767px) {
  .page-header { flex-direction: column; align-items: flex-start; }
  .page-title { font-size: 20px; }
  .profile-card { flex-direction: column; align-items: center; text-align: center; }
  .profile-stats { border-left: none; padding-left: 0; margin-left: 0; margin-top: 12px; border-top: 1px solid #f0f2f5; padding-top: 12px; width: 100%; justify-content: center; }
  .profile-meta-row { justify-content: center; }
  .mid-row { flex-direction: column; }
  .stats-row { flex-direction: column; }
  .stat-mini-card { min-width: auto; }
  .tab-toolbar { flex-direction: column; }
  .repo-tabs { padding: 12px; }
  .setup-row { flex-direction: column; }
  .side-right { display: none; }
}
</style>
