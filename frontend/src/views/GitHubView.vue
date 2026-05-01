<template>
  <div class="github-page">
    <div class="page-header">
      <h1 class="page-title">
        <svg viewBox="0 0 16 16" width="26" height="26" style="margin-right: 8px; vertical-align: -4px"><path fill="#1f2328" d="M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z"/></svg>
        我的 GitHub
      </h1>
      <div class="header-right">
        <div v-if="ghProfile" class="gh-user-tag">
          <img :src="ghProfile.avatar" class="tag-avatar" alt="" />
          <span>{{ ghProfile.login }}</span>
          <el-button text size="small" @click="resetGithub">更换</el-button>
        </div>
      </div>
    </div>

    <!-- 未输入用户名 -->
    <div v-if="!ghUsername && !ghLoading" class="setup-card">
      <el-icon :size="48" color="#d0d7de"><Platform /></el-icon>
      <h2 class="setup-title">连接你的 GitHub</h2>
      <p class="setup-hint">输入 GitHub 用户名，查看仓库概览、收藏项目和语言统计</p>
      <div class="setup-row">
        <el-input v-model="ghInput" placeholder="GitHub 用户名" size="large" clearable @keyup.enter="loadGithub">
          <template #prefix>
            <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#656d76" d="M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z"/></svg>
          </template>
        </el-input>
        <el-button type="primary" size="large" :loading="ghLoading" @click="loadGithub">加载</el-button>
      </div>
      <p v-if="!user" class="setup-login-hint">登录后可自动关联 GitHub 账号</p>
    </div>

    <!-- 加载中 -->
    <div v-else-if="ghLoading && !ghProfile" class="loading-card">
      <el-icon class="is-loading" :size="36" color="#2da44e"><Loading /></el-icon>
      <span>正在加载 GitHub 数据...</span>
    </div>

    <!-- 已加载 -->
    <template v-else-if="ghProfile">
      <!-- 用户概览卡片 -->
      <div class="profile-card">
        <div class="profile-left">
          <img :src="ghProfile.avatar" :alt="ghProfile.login" class="profile-avatar" />
          <div class="profile-info">
            <div class="profile-name">{{ ghProfile.name || ghProfile.login }}</div>
            <div class="profile-login">@{{ ghProfile.login }}</div>
            <div v-if="ghProfile.bio" class="profile-bio">{{ ghProfile.bio }}</div>
            <div class="profile-meta-row">
              <span v-if="ghProfile.location" class="profile-meta">
                <el-icon :size="13"><Location /></el-icon>{{ ghProfile.location }}
              </span>
              <a v-if="ghProfile.blog" :href="ghProfile.blog.startsWith('http') ? ghProfile.blog : 'https://' + ghProfile.blog" target="_blank" class="profile-meta profile-link">
                <el-icon :size="13"><Link /></el-icon>{{ ghProfile.blog }}
              </a>
              <span v-if="ghProfile.createdAt" class="profile-meta">
                <el-icon :size="13"><Clock /></el-icon>加入 {{ accountAge }}
              </span>
            </div>
          </div>
        </div>

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

        <!-- 语言分布 -->
        <div v-if="ghLangStats.length > 0" class="lang-section">
          <div class="lang-section-title">语言分布</div>
          <div class="lang-bar">
            <div
              v-for="lang in ghLangStats"
              :key="lang.name"
              class="lang-segment"
              :style="{ width: lang.pct + '%', background: langColor(lang.name) }"
              :title="lang.name + ' ' + lang.pct + '%'"
            ></div>
          </div>
          <div class="lang-legend">
            <div v-for="lang in ghLangStats.slice(0, 8)" :key="lang.name" class="lang-legend-item">
              <span class="lang-dot" :style="{ background: langColor(lang.name) }"></span>
              <span class="lang-name">{{ lang.name }}</span>
              <span class="lang-pct">{{ lang.pct }}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab 标签页 -->
      <el-tabs v-model="activeTab" class="repo-tabs">
        <el-tab-pane :label="'仓库 (' + ghProfile.publicRepos + ')'" name="repos">
          <!-- 搜索和排序 -->
          <div class="tab-toolbar">
            <el-input
              v-model="repoSearch"
              placeholder="搜索仓库..."
              prefix-icon="Search"
              clearable
              style="width: 240px"
              size="small"
            />
            <el-radio-group v-model="repoSort" size="small">
              <el-radio-button value="updated">最近更新</el-radio-button>
              <el-radio-button value="stars">最多 Star</el-radio-button>
              <el-radio-button value="forks">最多 Fork</el-radio-button>
            </el-radio-group>
          </div>

          <!-- 仓库列表 -->
          <div class="repo-list">
            <div v-for="repo in filteredRepos" :key="repo.name" class="repo-item">
              <div class="repo-item-header">
                <a :href="repo.url" target="_blank" class="repo-item-name">
                  <svg viewBox="0 0 16 16" width="16" height="16"><path fill="#656d76" d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8ZM5 12.25a.25.25 0 0 1 .25-.25h3.5a.25.25 0 0 1 .25.25v3.25a.25.25 0 0 1-.4.2l-1.45-1.087a.249.249 0 0 0-.3 0L5.4 15.7a.25.25 0 0 1-.4-.2Z"/></svg>
                  {{ repo.name }}
                </a>
                <span v-if="repo.fork" class="fork-badge">fork</span>
              </div>
              <p v-if="repo.description" class="repo-item-desc">{{ repo.description }}</p>
              <div class="repo-item-meta">
                <span v-if="repo.language" class="repo-lang">
                  <span class="lang-dot" :style="{ background: langColor(repo.language) }"></span>
                  {{ repo.language }}
                </span>
                <span v-if="repo.stars" class="repo-stat-link">
                  <svg viewBox="0 0 16 16" width="13" height="13"><path fill="#e3b341" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>
                  {{ formatNumber(repo.stars) }}
                </span>
                <span v-if="repo.forks" class="repo-stat-link">
                  <svg viewBox="0 0 16 16" width="13" height="13"><path fill="#8b949e" d="M5 5.372v.878c0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75v-.878a2.25 2.25 0 1 1 1.5 0v.878a2.25 2.25 0 0 1-2.25 2.25h-1.5v2.128a2.251 2.251 0 1 1-1.5 0V8.5h-1.5A2.25 2.25 0 0 1 3.5 6.25v-.878a2.25 2.25 0 1 1 1.5 0ZM5 3.25a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Zm6.75.75a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5Zm-3 8.75a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Z"/></svg>
                  {{ formatNumber(repo.forks) }}
                </span>
                <span class="repo-updated">{{ formatRelative(repo.updatedAt) }}</span>
              </div>
            </div>
          </div>

          <div v-if="ghRepos.length < ghProfile.publicRepos" class="load-more">
            <el-button plain @click="loadMoreRepos" :loading="loadingMore">加载更多</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="收藏仓库" name="starred">
          <div v-if="ghStarred.length === 0 && !starredLoading" class="empty-hint">暂无收藏数据</div>
          <div v-else-if="starredLoading" class="loading-card" style="padding: 40px">
            <el-icon class="is-loading" :size="24" color="#2da44e"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          <div v-else class="repo-list">
            <div v-for="repo in ghStarred" :key="repo.name" class="repo-item">
              <div class="repo-item-header">
                <a :href="repo.url" target="_blank" class="repo-item-name">
                  <svg viewBox="0 0 16 16" width="16" height="16"><path fill="#656d76" d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8ZM5 12.25a.25.25 0 0 1 .25-.25h3.5a.25.25 0 0 1 .25.25v3.25a.25.25 0 0 1-.4.2l-1.45-1.087a.249.249 0 0 0-.3 0L5.4 15.7a.25.25 0 0 1-.4-.2Z"/></svg>
                  {{ repo.name }}
                </a>
              </div>
              <p v-if="repo.description" class="repo-item-desc">{{ repo.description }}</p>
              <div class="repo-item-meta">
                <span v-if="repo.language" class="repo-lang">
                  <span class="lang-dot" :style="{ background: langColor(repo.language) }"></span>
                  {{ repo.language }}
                </span>
                <span v-if="repo.stars" class="repo-stat-link">
                  <svg viewBox="0 0 16 16" width="13" height="13"><path fill="#e3b341" d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/></svg>
                  {{ formatNumber(repo.stars) }}
                </span>
                <span v-if="repo.forks" class="repo-stat-link">
                  <svg viewBox="0 0 16 16" width="13" height="13"><path fill="#8b949e" d="M5 5.372v.878c0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75v-.878a2.25 2.25 0 1 1 1.5 0v.878a2.25 2.25 0 0 1-2.25 2.25h-1.5v2.128a2.251 2.251 0 1 1-1.5 0V8.5h-1.5A2.25 2.25 0 0 1 3.5 6.25v-.878a2.25 2.25 0 1 1 1.5 0ZM5 3.25a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Zm6.75.75a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5Zm-3 8.75a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Z"/></svg>
                  {{ formatNumber(repo.forks) }}
                </span>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Location, Link, Clock, Loading, Platform } from '@element-plus/icons-vue'
import { getUserProfile, getUserRepos, getUserStarred } from '../api/github'

const user = ref(null)
const ghInput = ref('')
const ghUsername = ref('')
const ghLoading = ref(false)
const ghProfile = ref(null)
const ghRepos = ref([])
const ghStarred = ref([])
const activeTab = ref('repos')
const repoSearch = ref('')
const repoSort = ref('updated')
const loadingMore = ref(false)
const starredLoading = ref(false)
const repoPerPage = ref(30)

const LANG_COLORS = {
  JavaScript: '#f1e05a', TypeScript: '#3178c6', Python: '#3572A5', Java: '#b07219',
  Go: '#00ADD8', Rust: '#dea584', 'C++': '#f34b7d', C: '#555555', 'C#': '#178600',
  Ruby: '#701516', PHP: '#4F5D95', Swift: '#F05138', Kotlin: '#A97BFF', Dart: '#00B4AB',
  Shell: '#89e051', HTML: '#e34c26', CSS: '#563d7c', Vue: '#41b883', Zig: '#ec915c',
  'Jupyter Notebook': '#DA5B0B', Scala: '#c22d40', Lua: '#000080',
}

function langColor(lang) {
  return LANG_COLORS[lang] || '#8b949e'
}

function formatNumber(n) {
  const num = parseInt(n) || 0
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'm'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return String(num)
}

function formatRelative(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diffMs = now - d
  const diffDays = Math.floor(diffMs / 86400000)
  if (diffDays === 0) return '今天更新'
  if (diffDays === 1) return '昨天更新'
  if (diffDays < 30) return diffDays + ' 天前更新'
  if (diffDays < 365) return Math.floor(diffDays / 30) + ' 个月前更新'
  return Math.floor(diffDays / 365) + ' 年前更新'
}

function calcAccountAge(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const years = Math.floor((now - d) / (365.25 * 86400000))
  const months = Math.floor(((now - d) % (365.25 * 86400000)) / (30.44 * 86400000))
  if (years > 0) return years + ' 年' + (months > 0 ? months + ' 个月' : '')
  return months > 0 ? months + ' 个月' : '不到 1 个月'
}

const accountAge = computed(() => ghProfile.value?.createdAt ? calcAccountAge(ghProfile.value.createdAt) : '')

const ghLangStats = computed(() => {
  const counts = {}
  let total = 0
  for (const r of ghRepos.value) {
    if (r.language) {
      counts[r.language] = (counts[r.language] || 0) + 1
      total++
    }
  }
  if (total === 0) return []
  return Object.entries(counts)
    .sort((a, b) => b[1] - a[1])
    .map(([name, count]) => ({ name, pct: Math.round((count / total) * 100) }))
})

const filteredRepos = computed(() => {
  let list = ghRepos.value
  if (repoSearch.value) {
    const q = repoSearch.value.toLowerCase()
    list = list.filter(r => r.name.toLowerCase().includes(q) || (r.description || '').toLowerCase().includes(q))
  }
  if (repoSort.value === 'stars') {
    list = [...list].sort((a, b) => (b.stars || 0) - (a.stars || 0))
  } else if (repoSort.value === 'forks') {
    list = [...list].sort((a, b) => (b.forks || 0) - (a.forks || 0))
  }
  return list
})

async function loadGithub(username) {
  const name = username || ghInput.value.trim()
  if (!name) {
    ElMessage.warning('请输入 GitHub 用户名')
    return
  }
  ghUsername.value = name
  ghLoading.value = true
  try {
    const [profile, repos] = await Promise.all([
      getUserProfile(name),
      getUserRepos(name, repoPerPage.value)
    ])
    ghProfile.value = profile
    ghRepos.value = repos
    localStorage.setItem('codexray_gh_user', name)
  } catch (e) {
    const msg = e?.message || ''
    if (msg.includes('404') || msg.includes('Not Found')) {
      ElMessage.error('用户 "' + name + '" 不存在')
    } else {
      ElMessage.error('加载失败：' + (msg || '网络错误'))
    }
    ghProfile.value = null
    ghRepos.value = []
    ghUsername.value = ''
  } finally {
    ghLoading.value = false
  }
}

async function loadMoreRepos() {
  if (!ghUsername.value) return
  loadingMore.value = true
  repoPerPage.value += 30
  try {
    ghRepos.value = await getUserRepos(ghUsername.value, repoPerPage.value)
  } finally {
    loadingMore.value = false
  }
}

async function loadStarred() {
  if (!ghUsername.value) return
  starredLoading.value = true
  try {
    ghStarred.value = await getUserStarred(ghUsername.value, 30)
  } catch (e) {
    ghStarred.value = []
  } finally {
    starredLoading.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'starred' && ghStarred.value.length === 0) {
    loadStarred()
  }
})

function resetGithub() {
  ghProfile.value = null
  ghRepos.value = []
  ghStarred.value = []
  ghUsername.value = ''
  ghInput.value = ''
  repoPerPage.value = 30
  localStorage.removeItem('codexray_gh_user')
}

function loadUser() {
  const saved = localStorage.getItem('codexray_user')
  if (saved) {
    try { user.value = JSON.parse(saved) } catch { /* ignore */ }
  }
}

async function initFromUser() {
  const ghUser = user.value?.githubUsername || localStorage.getItem('codexray_gh_user')
  if (ghUser && !ghProfile.value) {
    ghInput.value = ghUser
    await loadGithub(ghUser)
  }
}

function onAuthChange() {
  loadUser()
  initFromUser()
}

onMounted(() => {
  loadUser()
  window.addEventListener('auth-change', onAuthChange)
  initFromUser()
})
</script>

<style scoped>
.github-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2328;
  margin: 0;
  display: flex;
  align-items: center;
}

.gh-user-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #f6f8fa;
  border: 1px solid #d8dee4;
  border-radius: 20px;
  font-size: 13px;
  color: #1f2328;
  font-weight: 500;
}

.tag-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
}

/* 设置卡片 */
.setup-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  background: #fff;
  border: 1px dashed #d0d7de;
  border-radius: 16px;
}

.setup-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2328;
  margin: 16px 0 8px;
}

.setup-hint {
  font-size: 14px;
  color: #656d76;
  margin: 0 0 24px;
}

.setup-row {
  display: flex;
  gap: 10px;
  width: 100%;
  max-width: 400px;
}

.setup-row :deep(.el-input) { flex: 1; }

.setup-login-hint {
  font-size: 12px;
  color: #a8a8a8;
  margin-top: 16px;
}

/* 加载卡片 */
.loading-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 0;
  color: #656d76;
  font-size: 14px;
}

/* 用户概览卡片 */
.profile-card {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 16px;
  padding: 28px;
  margin-bottom: 20px;
}

.profile-left {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.profile-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 3px solid #e8ecf0;
  flex-shrink: 0;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  font-size: 22px;
  font-weight: 800;
  color: #1f2328;
}

.profile-login {
  font-size: 14px;
  color: #656d76;
  margin-top: 2px;
}

.profile-bio {
  font-size: 14px;
  color: #656d76;
  margin-top: 8px;
  line-height: 1.6;
}

.profile-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 10px;
}

.profile-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #8b949e;
}

.profile-link {
  color: #2da44e;
  text-decoration: none;
}

.profile-link:hover {
  text-decoration: underline;
}

.profile-stats {
  display: flex;
  gap: 0;
  margin-top: 20px;
  padding: 16px 0;
  border-top: 1px solid #f0f2f5;
  border-bottom: 1px solid #f0f2f5;
}

.profile-stat {
  flex: 1;
  text-align: center;
  border-right: 1px solid #f0f2f5;
}

.profile-stat:last-child {
  border-right: none;
}

.profile-stat-num {
  display: block;
  font-size: 26px;
  font-weight: 800;
  color: #1f2328;
}

.profile-stat-label {
  font-size: 13px;
  color: #8b949e;
  margin-top: 2px;
}

/* 语言分布 */
.lang-section {
  margin-top: 20px;
}

.lang-section-title {
  font-size: 13px;
  font-weight: 700;
  color: #1f2328;
  margin-bottom: 10px;
}

.lang-bar {
  display: flex;
  height: 12px;
  border-radius: 6px;
  overflow: hidden;
  gap: 1px;
}

.lang-segment {
  min-width: 4px;
  transition: width 0.4s ease;
}

.lang-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 12px;
}

.lang-legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #656d76;
}

.lang-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.lang-name {
  font-weight: 600;
}

.lang-pct {
  color: #a8a8a8;
}

/* Tab */
.repo-tabs {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 16px;
  padding: 20px 24px;
}

.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.repo-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.repo-item {
  padding: 16px;
  background: #f8faf9;
  border: 1px solid #e8ecf0;
  border-radius: 10px;
  transition: border-color 0.2s;
}

.repo-item:hover {
  border-color: #2da44e40;
}

.repo-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.repo-item-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  color: #1f2328;
  text-decoration: none;
}

.repo-item-name:hover {
  color: #2da44e;
}

.fork-badge {
  font-size: 10px;
  padding: 1px 8px;
  border-radius: 10px;
  background: #e8ecf0;
  color: #8b949e;
  font-weight: 500;
}

.repo-item-desc {
  font-size: 13px;
  color: #656d76;
  margin: 8px 0 0;
  line-height: 1.5;
}

.repo-item-meta {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-top: 10px;
  font-size: 12px;
  color: #8b949e;
}

.repo-lang, .repo-stat-link {
  display: flex;
  align-items: center;
  gap: 4px;
}

.repo-updated {
  margin-left: auto;
}

.empty-hint {
  color: #8b949e;
  font-size: 13px;
  text-align: center;
  padding: 40px 0;
}

.load-more {
  text-align: center;
  padding: 20px 0;
}

/* 响应式 */
@media (max-width: 767px) {
  .page-header { flex-direction: column; align-items: flex-start; }
  .page-title { font-size: 20px; }
  .profile-left { flex-direction: column; align-items: center; text-align: center; }
  .profile-meta-row { justify-content: center; }
  .profile-avatar { width: 64px; height: 64px; }
  .profile-stat-num { font-size: 20px; }
  .tab-toolbar { flex-direction: column; }
  .repo-tabs { padding: 16px; }
  .repo-item { padding: 12px; }
  .setup-row { flex-direction: column; }
}
</style>
