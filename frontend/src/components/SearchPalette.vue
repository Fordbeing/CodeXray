<template>
  <Teleport to="body">
    <div v-if="visible" class="search-overlay" @click.self="close">
      <div class="search-palette">
        <div class="search-input-wrap">
          <el-icon :size="18" color="#8b949e"><Search /></el-icon>
          <input
            ref="inputRef"
            v-model="query"
            class="search-input"
            placeholder="搜索分析任务、会话..."
            @keydown.esc="close"
            @keydown.down.prevent="moveDown"
            @keydown.up.prevent="moveUp"
            @keydown.enter.prevent="selectCurrent"
          />
          <kbd class="search-kbd">ESC</kbd>
        </div>
        <div class="search-results" ref="resultsRef">
          <div v-if="!query.trim()" class="search-hint">
            <p>输入关键词搜索分析任务</p>
          </div>
          <div v-else-if="loading" class="search-hint">
            <el-icon class="is-loading"><Loading /></el-icon>
            搜索中...
          </div>
          <div v-else-if="results.length === 0" class="search-hint">
            未找到结果
          </div>
          <template v-else>
            <div v-for="(group, gi) in groupedResults" :key="group.label" class="result-group">
              <div class="group-label">{{ group.label }}</div>
              <div
                v-for="(item, ii) in group.items"
                :key="item.id"
                :class="['result-item', { active: activeIndex === getGlobalIndex(gi, ii) }]"
                @click="navigate(item)"
                @mouseenter="activeIndex = getGlobalIndex(gi, ii)"
              >
                <el-icon :size="16" class="result-icon">
                  <component :is="item.icon" />
                </el-icon>
                <div class="result-info">
                  <div class="result-title">{{ item.title }}</div>
                  <div v-if="item.subtitle" class="result-sub">{{ item.subtitle }}</div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, nextTick, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Loading, Document, ChatDotRound } from '@element-plus/icons-vue'
import { listTasks } from '../api/analysis'
import { listChatSessions } from '../api/chat'

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['update:visible'])

const router = useRouter()
const query = ref('')
const inputRef = ref(null)
const resultsRef = ref(null)
const loading = ref(false)
const activeIndex = ref(0)
const taskResults = ref([])
const sessionResults = ref([])

watch(() => props.visible, (v) => {
  if (v) {
    query.value = ''
    activeIndex.value = 0
    nextTick(() => inputRef.value?.focus())
    loadAll()
  }
})

watch(query, () => {
  activeIndex.value = 0
})

async function loadAll() {
  loading.value = true
  try {
    const [tasks, sessions] = await Promise.all([
      listTasks(50).catch(() => []),
      listChatSessions().catch(() => []),
    ])
    taskResults.value = tasks || []
    sessionResults.value = sessions || []
  } finally {
    loading.value = false
  }
}

const results = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return []
  const items = []
  // 搜索分析任务
  taskResults.value
    .filter(t => (t.repoUrl || '').toLowerCase().includes(q))
    .slice(0, 5)
    .forEach(t => {
      items.push({
        id: 'task-' + t.taskId,
        type: 'task',
        title: t.repoUrl,
        subtitle: t.status,
        icon: Document,
        action: () => router.push('/analyze/' + t.taskId),
      })
    })
  // 搜索会话
  sessionResults.value
    .filter(s => (s.firstQuestion || s.repoUrl || '').toLowerCase().includes(q))
    .slice(0, 5)
    .forEach(s => {
      items.push({
        id: 'session-' + s.sessionId,
        type: 'session',
        title: s.firstQuestion || '会话',
        subtitle: s.repoUrl,
        icon: ChatDotRound,
        action: () => router.push({ path: '/chat', query: { repoUrl: s.repoUrl, taskId: s.taskId } }),
      })
    })
  return items
})

const groupedResults = computed(() => {
  const groups = []
  const tasks = results.value.filter(r => r.type === 'task')
  const sessions = results.value.filter(r => r.type === 'session')
  if (tasks.length) groups.push({ label: '分析任务', items: tasks })
  if (sessions.length) groups.push({ label: '对话', items: sessions })
  return groups
})

function getGlobalIndex(gi, ii) {
  let idx = 0
  for (let i = 0; i < gi; i++) idx += groupedResults.value[i].items.length
  return idx + ii
}

function moveDown() {
  if (activeIndex.value < results.value.length - 1) activeIndex.value++
}
function moveUp() {
  if (activeIndex.value > 0) activeIndex.value--
}
function selectCurrent() {
  if (results.value[activeIndex.value]) navigate(results.value[activeIndex.value])
}
function navigate(item) {
  item.action()
  close()
}
function close() {
  emit('update:visible', false)
}

// Cmd+K 全局快捷键
function onKeydown(e) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    emit('update:visible', !props.visible)
  }
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.search-overlay {
  position: fixed; inset: 0; z-index: 9999;
  background: rgba(0,0,0,0.4); backdrop-filter: blur(4px);
  display: flex; justify-content: center; padding-top: 15vh;
}

.search-palette {
  width: 560px; max-width: 90vw; background: #fff;
  border-radius: 12px; box-shadow: 0 16px 48px rgba(0,0,0,0.2);
  overflow: hidden; max-height: 480px;
  display: flex; flex-direction: column;
}

.search-input-wrap {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 16px; border-bottom: 1px solid #e8ecf0;
}

.search-input {
  flex: 1; border: none; outline: none; font-size: 16px;
  color: #1f2328; background: none;
}
.search-input::placeholder { color: #8b949e; }

.search-kbd {
  font-size: 11px; padding: 2px 6px; border-radius: 4px;
  background: #f6f8fa; border: 1px solid #d0d7de; color: #656d76;
  font-family: monospace;
}

.search-results {
  flex: 1; overflow-y: auto; padding: 8px;
}

.search-hint {
  text-align: center; padding: 32px 16px; color: #8b949e; font-size: 13px;
  display: flex; align-items: center; justify-content: center; gap: 8px;
}

.result-group { margin-bottom: 8px; }
.group-label {
  font-size: 11px; font-weight: 600; color: #8b949e;
  padding: 4px 12px; text-transform: uppercase;
}

.result-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 8px; cursor: pointer;
  transition: background 0.1s;
}

.result-item:hover, .result-item.active { background: #f0fdf4; }
.result-icon { color: #8b949e; flex-shrink: 0; }
.result-info { flex: 1; min-width: 0; }
.result-title {
  font-size: 14px; color: #1f2328;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.result-sub {
  font-size: 12px; color: #8b949e; margin-top: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
</style>
