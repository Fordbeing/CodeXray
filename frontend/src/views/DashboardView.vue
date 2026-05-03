<template>
  <div class="dashboard-page">
    <div v-if="loading" class="dashboard-skeleton">
      <div v-for="i in 3" :key="i" style="margin-bottom: 16px;">
        <el-skeleton :rows="3" animated />
      </div>
    </div>
    <div v-else class="grid-layout">
      <div
        v-for="block in orderedBlocks"
        :key="block.id"
        :class="['grid-item', `span-${block.span}`, `span-sm-${block.spanSm || block.span}`, `span-xs-${block.spanXs || 12}`, { dragging: dragState.draggingId === block.id, 'drag-over': dragState.overId === block.id }]"
        draggable="true"
        @dragstart="onDragStart($event, block.id)"
        @dragend="onDragEnd"
        @dragover.prevent="onDragOver($event, block.id)"
        @dragleave="onDragLeave(block.id)"
        @drop="onDrop($event, block.id)"
      >
        <div class="drag-handle" title="拖拽排序">
          <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#b0b8c4" d="M10 13a1 1 0 1 1 0-2 1 1 0 0 1 0 2Zm0-5a1 1 0 1 1 0-2 1 1 0 0 1 0 2Zm0-5a1 1 0 1 1 0-2 1 1 0 0 1 0 2ZM6 13a1 1 0 1 1 0-2 1 1 0 0 1 0 2Zm0-5a1 1 0 1 1 0-2 1 1 0 0 1 0 2Zm0-5a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"/></svg>
        </div>
        <component :is="block.component" v-bind="block.props || {}" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listTasks } from '../api/analysis'
import { getTodayTrending } from '../api/trending'
import { getStatusInfo, shortenUrl } from '../utils/status'
import { formatNumber, formatRelative } from '../utils/format'
import {
  List, CircleCheck, TrendCharts, Search, ChatDotRound, Platform,
  ArrowRight, Link
} from '@element-plus/icons-vue'

const router = useRouter()
const quickUrl = ref('')
const recentTasks = ref([])
const allTasks = ref([])
const hotRepos = ref([])
const user = ref(null)
const loading = ref(false)

const quickActions = [
  { path: '/analyze', label: '仓库分析', desc: '输入仓库地址，深度分析代码', icon: Search, bg: '#f0fdf4', color: '#2da44e' },
  { path: '/chat', label: '代码问答', desc: '基于 RAG 的智能代码问答', icon: ChatDotRound, bg: '#eff6ff', color: '#3b82f6' },
  { path: '/trending', label: '热点推送', desc: '每日 GitHub 热门项目推荐', icon: TrendCharts, bg: '#fefce8', color: '#d29922' },
  { path: '/github', label: '我的 GitHub', desc: '查看仓库、收藏和语言统计', icon: Platform, bg: '#f5f3ff', color: '#8b5cf6' },
]

const completedCount = computed(() => allTasks.value.filter(t => t.status === 'COMPLETED').length)
const langCount = computed(() => {
  const langs = new Set(hotRepos.value.map(r => r.language).filter(Boolean))
  return langs.size
})
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

// ========== 区块定义 ==========
const STORAGE_KEY = 'codexray_dashboard_layout'

const defaultOrder = ['welcome', 'stats', 'recent', 'trending', 'actions']

const blockDefs = {
  welcome: { id: 'welcome', span: 12, spanSm: 12, spanXs: 12, component: null },
  stats:   { id: 'stats',   span: 12, spanSm: 12, spanXs: 12, component: null },
  recent:  { id: 'recent',  span: 7,  spanSm: 12, spanXs: 12, component: null },
  trending:{ id: 'trending',span: 5,  spanSm: 6,  spanXs: 12, component: null },
  actions: { id: 'actions', span: 5,  spanSm: 6,  spanXs: 12, component: null },
}

// 用渲染函数组件替代模板
const WelcomeBlock = {
  setup() {
    return () => h('div', { class: 'welcome-card' }, [
      h('div', { class: 'welcome-left' }, [
        h('h1', { class: 'welcome-title' }, [
          greeting.value,
          user.value ? h('span', {}, '，' + (user.value.nickname || user.value.username)) : null,
        ]),
        h('p', { class: 'welcome-sub' }, '欢迎使用 CodeXray，开始你的代码分析之旅'),
      ]),
      h('div', { class: 'welcome-right' }, [
        h('div', { class: 'quick-row' }, [
          h('input', {
            class: 'quick-input',
            placeholder: '输入 GitHub 仓库地址，快速开始分析...',
            value: quickUrl.value,
            onInput: (e) => { quickUrl.value = e.target.value },
            onKeyup: (e) => { if (e.key === 'Enter') startQuickAnalyze() },
          }),
          h('button', {
            class: 'quick-btn',
            disabled: !quickUrl.value,
            onClick: startQuickAnalyze,
          }, '开始分析'),
        ]),
      ]),
    ])
  },
}

const StatsBlock = {
  setup() {
    return () => h('div', { class: 'stats-row' }, [
      h('div', { class: 'stat-card' }, [
        h('div', { class: 'stat-icon-wrap', style: 'background:linear-gradient(135deg,#f0fdf4,#dcfce7)' }, [
          h('svg', { viewBox: '0 0 16 16', width: '22', height: '22' }, [
            h('path', { fill: '#2da44e', d: 'M2 4a1 1 0 0 1 1-1h10a1 1 0 1 1 0 2H3a1 1 0 0 1-1-1Zm0 4a1 1 0 0 1 1-1h10a1 1 0 1 1 0 2H3a1 1 0 0 1-1-1Zm1 3a1 1 0 1 0 0 2h6a1 1 0 1 0 0-2H3Z' }),
          ]),
        ]),
        h('div', { class: 'stat-body' }, [
          h('div', { class: 'stat-number' }, String(allTasks.value.length)),
          h('div', { class: 'stat-label' }, '总任务数'),
        ]),
        h('div', { class: 'stat-trend' }, [
          h('span', { class: 'trend-badge', style: 'background:#f0fdf4;color:#2da44e' }, completedCount.value + ' 完成'),
        ]),
      ]),
      h('div', { class: 'stat-card' }, [
        h('div', { class: 'stat-icon-wrap', style: 'background:linear-gradient(135deg,#eff6ff,#dbeafe)' }, [
          h('svg', { viewBox: '0 0 16 16', width: '22', height: '22' }, [
            h('path', { fill: '#3b82f6', d: 'M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14Zm3.78-9.72a.751.751 0 0 0-.018-1.042.751.751 0 0 0-1.042-.018L6.75 8.19 5.28 6.72a.751.751 0 0 0-1.042.018.751.751 0 0 0-.018 1.042l2 2a.75.75 0 0 0 1.06 0Z' }),
          ]),
        ]),
        h('div', { class: 'stat-body' }, [
          h('div', { class: 'stat-number', style: 'color:#3b82f6' }, String(completedCount.value)),
          h('div', { class: 'stat-label' }, '已完成'),
        ]),
        h('div', { class: 'stat-trend' }, [
          h('span', { class: 'trend-badge', style: 'background:#eff6ff;color:#3b82f6' },
            (allTasks.value.length > 0 ? Math.round(completedCount.value / allTasks.value.length * 100) : 0) + '%'),
        ]),
      ]),
      h('div', { class: 'stat-card' }, [
        h('div', { class: 'stat-icon-wrap', style: 'background:linear-gradient(135deg,#fefce8,#fef9c3)' }, [
          h('svg', { viewBox: '0 0 16 16', width: '22', height: '22' }, [
            h('path', { fill: '#d29922', d: 'm4.427 9.427 3.396 3.396a.25.25 0 0 0 .354 0l3.396-3.396A.25.25 0 0 0 11.396 7H4.604a.25.25 0 0 0-.177.427Z' }),
          ]),
        ]),
        h('div', { class: 'stat-body' }, [
          h('div', { class: 'stat-number', style: 'color:#d29922' }, String(hotRepos.value.length)),
          h('div', { class: 'stat-label' }, '今日热点'),
        ]),
        h('div', { class: 'stat-trend' }, [
          h('span', { class: 'trend-badge', style: 'background:#fefce8;color:#d29922' }, langCount.value + ' 种语言'),
        ]),
      ]),
    ])
  },
}

const RecentBlock = {
  setup() {
    return () => h('div', { class: 'section-card' }, [
      h('div', { class: 'section-header' }, [
        h('span', { class: 'section-title' }, '最近任务'),
        h('a', { class: 'section-link', onClick: () => router.push('/history') }, '查看全部'),
      ]),
      recentTasks.value.length === 0
        ? h('div', { class: 'empty-hint' }, [
            h('p', {}, '暂无分析记录'),
            h('a', { class: 'empty-link', onClick: () => router.push('/analyze') }, '开始分析'),
          ])
        : recentTasks.value.map(task => h('div', {
            class: 'recent-item',
            key: task.taskId,
            onClick: () => { if (task.status === 'COMPLETED') router.push('/analyze/' + task.taskId) },
          }, [
            h('div', { class: 'recent-left' }, [
              h('div', { class: 'recent-url' }, shortenUrl(task.repoUrl)),
              task.createdAt ? h('div', { class: 'recent-time' }, formatRelative(task.createdAt)) : null,
            ]),
            h('span', { class: 'status-tag ' + (getStatusInfo(task.status).type || 'info') },
              getStatusInfo(task.status).text),
          ])),
    ])
  },
}

const TrendingBlock = {
  setup() {
    return () => h('div', { class: 'section-card' }, [
      h('div', { class: 'section-header' }, [
        h('span', { class: 'section-title' }, '今日热点'),
        h('a', { class: 'section-link', onClick: () => router.push('/trending') }, '查看全部'),
      ]),
      hotRepos.value.length === 0
        ? h('div', { class: 'empty-hint' }, [h('p', {}, '暂无热点数据')])
        : hotRepos.value.map((repo, i) => h('div', { class: 'hot-item', key: i }, [
            h('span', { class: 'hot-rank rank-' + (i + 1) }, String(i + 1)),
            h('div', { class: 'hot-info' }, [
              h('a', { class: 'hot-name', href: repo.repoUrl, target: '_blank' }, repo.repoName),
              h('div', { class: 'hot-meta' }, [
                repo.language ? h('span', { class: 'hot-lang' }, repo.language) : null,
                repo.stars ? h('span', {}, '★ ' + formatNumber(repo.stars)) : null,
              ]),
            ]),
          ])),
    ])
  },
}

const ActionsBlock = {
  setup() {
    return () => h('div', { class: 'section-card' }, [
      h('div', { class: 'section-header' }, [
        h('span', { class: 'section-title' }, '快速操作'),
      ]),
      h('div', { class: 'action-grid' },
        quickActions.map(a => h('div', {
          class: 'action-item',
          key: a.path,
          onClick: () => router.push(a.path),
        }, [
          h('div', { class: 'action-dot', style: 'background:' + a.bg }),
          h('div', { class: 'action-info' }, [
            h('div', { class: 'action-name' }, a.label),
            h('div', { class: 'action-desc' }, a.desc),
          ]),
        ]))),
    ])
  },
}

// ========== 区块顺序 ==========
const blockOrder = ref([...defaultOrder])

function loadOrder() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      if (Array.isArray(parsed) && parsed.every(id => defaultOrder.includes(id))) {
        blockOrder.value = parsed
        return
      }
    }
  } catch { /* ignore */ }
  blockOrder.value = [...defaultOrder]
}

function saveOrder() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(blockOrder.value))
}

const componentMap = {
  welcome: WelcomeBlock,
  stats: StatsBlock,
  recent: RecentBlock,
  trending: TrendingBlock,
  actions: ActionsBlock,
}

const orderedBlocks = computed(() =>
  blockOrder.value.map(id => ({
    ...blockDefs[id],
    component: componentMap[id],
  }))
)

// ========== 拖拽 ==========
const dragState = ref({ draggingId: null, overId: null })

function onDragStart(e, id) {
  dragState.value.draggingId = id
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', id)
}

function onDragEnd() {
  dragState.value.draggingId = null
  dragState.value.overId = null
}

function onDragOver(e, id) {
  if (dragState.value.draggingId === id) return
  dragState.value.overId = id
  e.dataTransfer.dropEffect = 'move'
}

function onDragLeave(id) {
  if (dragState.value.overId === id) {
    dragState.value.overId = null
  }
}

function onDrop(e, targetId) {
  const fromId = dragState.value.draggingId
  if (!fromId || fromId === targetId) return

  const order = [...blockOrder.value]
  const fromIdx = order.indexOf(fromId)
  const toIdx = order.indexOf(targetId)
  if (fromIdx === -1 || toIdx === -1) return

  order.splice(fromIdx, 1)
  order.splice(toIdx, 0, fromId)
  blockOrder.value = order
  saveOrder()
  dragState.value = { draggingId: null, overId: null }
}

// ========== 工具函数 ==========

function startQuickAnalyze() {
  if (!quickUrl.value) return
  router.push({ path: '/analyze', query: { url: quickUrl.value } })
}

function loadUser() {
  const saved = localStorage.getItem('codexray_user')
  if (saved) {
    try { user.value = JSON.parse(saved) } catch { /* ignore */ }
  }
}

async function reloadDashboard() {
  loadUser()
  loadOrder()
  loading.value = true
  try {
    const [tasks, repos] = await Promise.all([
      listTasks(5).catch(() => []),
      getTodayTrending().catch(() => [])
    ])
    allTasks.value = tasks || []
    recentTasks.value = (tasks || []).slice(0, 5)
    hotRepos.value = (repos || []).slice(0, 5)
  } finally {
    loading.value = false
  }
}

function onAuthChange(e) {
  if (!e.detail) {
    // 退出登录
    user.value = null
    recentTasks.value = []
    allTasks.value = []
  } else {
    // 登录成功，重新加载
    reloadDashboard()
  }
}

onMounted(async () => {
  await reloadDashboard()
  window.addEventListener('auth-change', onAuthChange)
})

onUnmounted(() => {
  window.removeEventListener('auth-change', onAuthChange)
})
</script>

<style scoped>
.dashboard-page {
  max-width: 1400px;
  margin: 0 auto;
}

/* ===== Grid 布局 ===== */
.grid-layout {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 18px;
}

.grid-item {
  position: relative;
  border-radius: 14px;
  transition: opacity 0.2s, transform 0.15s, box-shadow 0.2s;
}

.grid-item.span-12 { grid-column: span 12; }
.grid-item.span-7  { grid-column: span 7; }
.grid-item.span-5  { grid-column: span 5; }

/* 拖拽手柄 */
.drag-handle {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 10;
  cursor: grab;
  opacity: 0;
  transition: opacity 0.15s;
  padding: 4px;
  border-radius: 4px;
  background: rgba(255,255,255,0.9);
}

.grid-item:hover .drag-handle {
  opacity: 1;
}

.drag-handle:hover {
  background: #e8ecf0;
}

/* 拖拽状态 */
.grid-item[draggable="true"] {
  cursor: default;
}

.grid-item.dragging {
  opacity: 0.4;
  transform: scale(0.98);
}

.grid-item.drag-over {
  box-shadow: 0 0 0 2px #2da44e;
}

/* ===== 欢迎卡片 ===== */
.grid-item:deep(.welcome-card) {
  background: linear-gradient(135deg, #f0fdf4 0%, #eff6ff 100%);
  border: 1px solid #d8dee4;
  border-radius: 14px;
  padding: 28px 32px;
  display: flex;
  align-items: center;
  gap: 32px;
}

.grid-item:deep(.welcome-left) { flex-shrink: 0; }
.grid-item:deep(.welcome-title) {
  font-size: 24px; font-weight: 700; color: #1f2328; margin: 0 0 4px;
}
.grid-item:deep(.welcome-sub) { font-size: 14px; color: #656d76; margin: 0; }
.grid-item:deep(.welcome-right) { flex: 1; min-width: 0; }
.grid-item:deep(.quick-row) {
  display: flex; gap: 12px; max-width: 600px; margin-left: auto;
}
.grid-item:deep(.quick-input) {
  flex: 1; padding: 10px 14px; border: 1px solid #d0d7de; border-radius: 8px;
  font-size: 14px; outline: none; transition: border-color 0.2s;
}
.grid-item:deep(.quick-input:focus) { border-color: #2da44e; }
.grid-item:deep(.quick-btn) {
  padding: 10px 20px; background: #2da44e; color: #fff; border: none;
  border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer;
  transition: background 0.15s; white-space: nowrap;
}
.grid-item:deep(.quick-btn:hover) { background: #2c974b; }
.grid-item:deep(.quick-btn:disabled) { background: #a8dab5; cursor: not-allowed; }

/* ===== 统计卡片 ===== */
.grid-item:deep(.stats-row) {
  display: flex; gap: 14px;
}

.grid-item:deep(.stat-card) {
  flex: 1; display: flex; align-items: center; gap: 14px;
  background: #fff; border: 1px solid #d8dee4; border-radius: 14px;
  padding: 18px 20px; transition: box-shadow 0.2s, transform 0.2s;
}

.grid-item:deep(.stat-card:hover) {
  box-shadow: 0 4px 16px rgba(45,164,78,0.08); transform: translateY(-1px);
}

.grid-item:deep(.stat-icon-wrap) {
  width: 46px; height: 46px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}

.grid-item:deep(.stat-body) { flex: 1; min-width: 0; }
.grid-item:deep(.stat-number) { font-size: 26px; font-weight: 800; color: #1f2328; line-height: 1.2; }
.grid-item:deep(.stat-label) { font-size: 13px; color: #656d76; margin-top: 2px; }
.grid-item:deep(.stat-trend) { flex-shrink: 0; }
.grid-item:deep(.trend-badge) {
  font-size: 11px; font-weight: 600; padding: 3px 8px; border-radius: 6px;
}

/* ===== 通用 section 卡片 ===== */
.grid-item:deep(.section-card) {
  background: #fff; border: 1px solid #d8dee4; border-radius: 14px;
  padding: 20px 22px; height: 100%;
}

.grid-item:deep(.section-header) {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 14px; padding-bottom: 12px; border-bottom: 1px solid #f0f2f5;
}

.grid-item:deep(.section-title) {
  font-size: 15px; font-weight: 700; color: #1f2328;
}

.grid-item:deep(.section-link) {
  font-size: 13px; color: #3b82f6; cursor: pointer; text-decoration: none;
}
.grid-item:deep(.section-link:hover) { text-decoration: underline; }

.grid-item:deep(.empty-hint) {
  color: #8b949e; font-size: 13px; text-align: center; padding: 32px 0;
}
.grid-item:deep(.empty-hint p) { margin: 0 0 8px; }
.grid-item:deep(.empty-link) {
  color: #3b82f6; cursor: pointer; font-size: 13px; text-decoration: none;
}

/* ===== 最近任务 ===== */
.grid-item:deep(.recent-item) {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 8px; border-bottom: 1px solid #f0f2f5; cursor: pointer;
  transition: background 0.15s; border-radius: 8px;
}
.grid-item:deep(.recent-item:hover) { background: #f6f8fa; }
.grid-item:deep(.recent-item:last-child) { border-bottom: none; }
.grid-item:deep(.recent-left) { flex: 1; min-width: 0; }
.grid-item:deep(.recent-url) { font-size: 13px; font-weight: 500; color: #1f2328; }
.grid-item:deep(.recent-time) { font-size: 11px; color: #8b949e; margin-top: 2px; }
.grid-item:deep(.status-tag) {
  font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px;
  background: #f0fdf4; color: #2da44e;
}
.grid-item:deep(.status-tag.warning) { background: #fefce8; color: #d29922; }
.grid-item:deep(.status-tag.danger)  { background: #fef2f2; color: #cf222e; }
.grid-item:deep(.status-tag.info)    { background: #f6f8fa; color: #656d76; }

/* ===== 热点 ===== */
.grid-item:deep(.hot-item) {
  display: flex; gap: 12px; align-items: flex-start;
  padding: 10px 0; border-bottom: 1px solid #f0f2f5;
}
.grid-item:deep(.hot-item:last-child) { border-bottom: none; }
.grid-item:deep(.hot-rank) {
  width: 24px; height: 24px; border-radius: 6px; background: #f6f8fa; color: #656d76;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 12px; flex-shrink: 0;
}
.grid-item:deep(.hot-rank.rank-1) { background: linear-gradient(135deg,#fef3c7,#fde68a); color: #92400e; }
.grid-item:deep(.hot-rank.rank-2) { background: #f3f4f6; color: #4b5563; }
.grid-item:deep(.hot-rank.rank-3) { background: linear-gradient(135deg,#fef2f2,#fed7aa); color: #9a3412; }
.grid-item:deep(.hot-info) { flex: 1; min-width: 0; }
.grid-item:deep(.hot-name) {
  font-size: 14px; font-weight: 600; color: #1f2328; text-decoration: none;
}
.grid-item:deep(.hot-name:hover) { color: #2da44e; }
.grid-item:deep(.hot-meta) {
  display: flex; gap: 8px; align-items: center; margin-top: 4px;
  font-size: 12px; color: #656d76;
}
.grid-item:deep(.hot-lang) {
  background: #f0fdf4; color: #166534; padding: 1px 6px;
  border-radius: 4px; font-size: 11px; font-weight: 500;
}

/* ===== 快速操作 ===== */
.grid-item:deep(.action-grid) { display: flex; flex-direction: column; gap: 8px; }
.grid-item:deep(.action-item) {
  display: flex; align-items: center; gap: 12px; padding: 12px;
  border: 1px solid #e8ecf0; border-radius: 10px; cursor: pointer;
  transition: all 0.2s;
}
.grid-item:deep(.action-item:hover) {
  border-color: #2da44e40; background: #f8faf9;
}
.grid-item:deep(.action-dot) {
  width: 40px; height: 40px; border-radius: 10px; flex-shrink: 0;
}
.grid-item:deep(.action-info) { flex: 1; min-width: 0; }
.grid-item:deep(.action-name) { font-size: 14px; font-weight: 700; color: #1f2328; }
.grid-item:deep(.action-desc) { font-size: 12px; color: #8b949e; margin-top: 2px; }

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .grid-item.span-7,
  .grid-item.span-5 {
    grid-column: span 6;
  }
  .grid-item.span-sm-12 { grid-column: span 12; }
  .grid-item.span-sm-6  { grid-column: span 6; }

  .grid-item:deep(.stats-row) { flex-wrap: wrap; }
  .grid-item:deep(.stat-card) { flex: 1 1 calc(33% - 10px); min-width: 180px; }
}

@media (max-width: 767px) {
  .grid-layout { gap: 12px; }

  .grid-item.span-7,
  .grid-item.span-5,
  .grid-item.span-sm-6 {
    grid-column: span 12;
  }
  .grid-item.span-xs-12 { grid-column: span 12; }

  .grid-item:deep(.welcome-card) {
    flex-direction: column; gap: 16px; padding: 20px;
  }
  .grid-item:deep(.welcome-right) { width: 100%; }
  .grid-item:deep(.quick-row) { max-width: 100%; margin-left: 0; flex-direction: column; }
  .grid-item:deep(.welcome-title) { font-size: 20px; }

  .grid-item:deep(.stats-row) { flex-direction: column; gap: 10px; }
  .grid-item:deep(.stat-card) { padding: 14px; }
  .grid-item:deep(.stat-number) { font-size: 22px; }

  .drag-handle { opacity: 0.6; }
}
</style>
