<template>
  <div class="chat-page">
    <!-- 会话侧边栏 -->
    <div class="session-sidebar" :class="{ 'sidebar-open': sidebarOpen }">
      <div class="sidebar-header">
        <span class="sidebar-title">对话历史</span>
        <div class="sidebar-actions">
          <el-button type="primary" size="small" @click="handleNewSession" :disabled="!repoUrl.trim()">
            <el-icon><Plus /></el-icon>
            <span class="btn-text">新建</span>
          </el-button>
          <el-button class="sidebar-close-btn" size="small" text @click="sidebarOpen = false">
            <el-icon :size="18"><Close /></el-icon>
          </el-button>
        </div>
      </div>
      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.sessionId"
          :class="['session-item', { active: session.sessionId === currentSessionId }]"
          @click="switchSession(session)"
        >
          <div class="session-info">
            <div class="session-repo">
              {{ formatRepoName(session.repoUrl) }}
              <el-tag v-if="session.taskId" size="small" type="success" effect="light" style="margin-left: 4px">RAG</el-tag>
            </div>
            <div class="session-preview">{{ session.firstQuestion || '新对话' }}</div>
            <div class="session-time">{{ formatTime(session.createdAt) }}</div>
          </div>
          <el-button
            class="session-delete"
            type="danger"
            size="small"
            text
            @click.stop="handleDeleteSession(session.sessionId)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <div v-if="sessions.length === 0" class="no-sessions">
          暂无对话记录
        </div>
      </div>
    </div>
    <div class="sidebar-overlay" v-if="sidebarOpen" @click="sidebarOpen = false"></div>

    <!-- 主对话区 -->
    <div class="chat-main">
      <div class="chat-header">
        <button class="sidebar-toggle" @click="sidebarOpen = !sidebarOpen">
          <el-icon :size="18"><Expand /></el-icon>
        </button>
        <div class="page-header">
          <h1 class="page-title">代码问答</h1>
          <el-tag v-if="currentTaskId" type="success" effect="light" size="large">
            <el-icon style="margin-right: 4px"><Connection /></el-icon>
            RAG 模式
          </el-tag>
        </div>
      </div>

      <!-- 仓库 URL -->
      <div class="url-card">
        <div class="url-row">
          <el-input
            v-model="repoUrl"
            placeholder="输入 GitHub 仓库地址，按回车加载对话"
            size="large"
            clearable
            @keyup.enter="loadSessions"
          >
            <template #prefix>
              <el-icon><Link /></el-icon>
            </template>
          </el-input>
          <el-button size="large" @click="loadSessions" :loading="loadingSessions">
            加载
          </el-button>
        </div>
        <div v-if="currentTaskId" class="task-id-hint">
          任务ID: {{ currentTaskId.slice(0, 8) }}...（基于此分析结果进行精确问答）
        </div>
      </div>

      <!-- 对话区域 -->
      <div class="chat-card">
        <div class="chat-messages" ref="messagesRef">
          <div v-if="messages.length === 0" class="empty-chat">
            <el-icon :size="48" color="#d0d7de"><ChatDotRound /></el-icon>
            <p v-if="currentTaskId">已关联分析任务，可对该仓库代码进行精确问答</p>
            <p v-else>输入仓库地址和问题，开始与代码对话</p>
          </div>

          <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'" :size="20"><User /></el-icon>
              <el-icon v-else :size="20" color="#2da44e"><Cpu /></el-icon>
            </div>
            <div class="message-content">
              <div class="message-text markdown-body" v-html="renderMd(msg.content)"></div>
            </div>
          </div>

          <div v-if="streaming" class="message assistant">
            <div class="message-avatar">
              <el-icon :size="20" color="#2da44e"><Cpu /></el-icon>
            </div>
            <div class="message-content">
              <div class="message-text markdown-body" v-html="renderMd(streamContent)"></div>
              <span class="stream-cursor"></span>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input">
          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            placeholder="输入你的问题... (Enter 发送)"
            resize="none"
            @keyup.enter.exact="handleSend"
          />
          <el-button type="primary" :loading="streaming" :disabled="!canSend" @click="handleSend">
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getChatHistory, listChatSessions, createChatSession, deleteChatSession } from '../api/chat'
import { marked } from 'marked'

// Configure marked for safe rendering
marked.setOptions({
  breaks: true,
  gfm: true,
  pedantic: false,
})

function renderMd(text) {
  if (!text) return ''
  try {
    return marked.parse(text)
  } catch {
    return text.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
  }
}

const route = useRoute()
const token = localStorage.getItem('codexray_token') || ''

const repoUrl = ref('')
const question = ref('')
const streaming = ref(false)
const streamContent = ref('')
const loadingSessions = ref(false)
const messages = ref([])
const messagesRef = ref(null)
const currentSessionId = ref(null)
const currentTaskId = ref(null)
const sessions = ref([])
const sidebarOpen = ref(false)

let abortController = null

const canSend = computed(() => repoUrl.value.trim() && question.value.trim() && !streaming.value)

onMounted(() => {
  if (route.query.repoUrl) repoUrl.value = route.query.repoUrl
  if (route.query.taskId) currentTaskId.value = route.query.taskId
  if (repoUrl.value) loadSessions()
  if (window.innerWidth >= 768) sidebarOpen.value = true
})

onUnmounted(() => {
  if (abortController) abortController.abort()
})

watch(() => route.query, (q) => {
  if (q.repoUrl && q.repoUrl !== repoUrl.value) repoUrl.value = q.repoUrl
  if (q.taskId) currentTaskId.value = q.taskId
  if (q.repoUrl) loadSessions()
})

async function loadSessions() {
  if (!repoUrl.value.trim()) return
  loadingSessions.value = true
  try {
    sessions.value = await listChatSessions(repoUrl.value) || []
  } catch (e) {
    console.error('加载会话列表失败:', e)
  } finally {
    loadingSessions.value = false
  }
}

async function switchSession(session) {
  currentSessionId.value = session.sessionId
  repoUrl.value = session.repoUrl || repoUrl.value
  currentTaskId.value = session.taskId || currentTaskId.value
  if (window.innerWidth < 768) sidebarOpen.value = false
  try {
    const history = await getChatHistory(session.sessionId)
    messages.value = (history || []).map(h => ({ role: h.role, content: h.content }))
    await scrollToBottom()
  } catch (e) {
    console.error('加载历史失败:', e)
    messages.value = []
  }
}

async function handleNewSession() {
  if (!repoUrl.value.trim()) {
    ElMessage.warning('请先输入仓库地址')
    return
  }
  try {
    const result = await createChatSession(repoUrl.value, currentTaskId.value)
    currentSessionId.value = result.sessionId
    messages.value = []
    await loadSessions()
    ElMessage.success('新会话已创建')
  } catch (e) {
    ElMessage.error('创建会话失败: ' + (e.message || '未知错误'))
  }
}

async function handleDeleteSession(sessionId) {
  try {
    await ElMessageBox.confirm('确定删除该对话？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteChatSession(sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      messages.value = []
    }
    ElMessage.success('已删除')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e.message || '未知错误'))
  }
}

async function handleSend() {
  if (!canSend.value) return
  if (streaming.value) return

  const q = question.value.trim()
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  streaming.value = true
  streamContent.value = ''
  await scrollToBottom()

  try {
    abortController = new AbortController()
    const headers = { 'Content-Type': 'application/json' }
    if (token) headers['Authorization'] = 'Bearer ' + token

    const resp = await fetch('/api/chat/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify({
        sessionId: currentSessionId.value,
        repoUrl: repoUrl.value,
        taskId: currentTaskId.value,
        question: q
      }),
      signal: abortController.signal
    })

    if (!resp.ok) {
      const err = await resp.json().catch(() => ({}))
      throw new Error(err.message || '请求失败')
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5)
          if (data === '[DONE]') continue
          try {
            const evt = JSON.parse(data)
            if (evt.type === 'session') {
              currentSessionId.value = evt.sessionId
            } else if (evt.type === 'token') {
              streamContent.value += evt.content
              await scrollToBottom()
            }
          } catch { /* skip malformed lines */ }
        }
      }
    }

    if (streamContent.value) {
      messages.value.push({ role: 'assistant', content: streamContent.value })
    }
    streamContent.value = ''
    await loadSessions()
  } catch (e) {
    if (e.name !== 'AbortError') {
      messages.value.push({ role: 'assistant', content: '抱歉，发生了错误：' + (e.message || '未知错误') })
    }
  } finally {
    streaming.value = false
    abortController = null
    await scrollToBottom()
  }
}

function formatRepoName(url) {
  if (!url) return '未知仓库'
  const match = url.match(/github\.com\/([^/]+\/[^/]+)/)
  return match ? match[1] : url.length > 30 ? url.slice(0, 30) + '...' : url
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const diffMs = Date.now() - d
  if (diffMs < 60000) return '刚刚'
  if (diffMs < 3600000) return Math.floor(diffMs / 60000) + '分钟前'
  if (diffMs < 86400000) return Math.floor(diffMs / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
}
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 56px);
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #d8dee4;
  overflow: hidden;
}

/* ===== 侧边栏 ===== */
.session-sidebar {
  width: 260px;
  min-width: 260px;
  border-right: 1px solid #e8ecf0;
  background: #f8faf9;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 14px;
  border-bottom: 1px solid #e8ecf0;
}

.sidebar-title { font-size: 14px; font-weight: 600; color: #1f2328; }
.sidebar-actions { display: flex; gap: 4px; align-items: center; }
.sidebar-close-btn { display: none; }

.session-list { flex: 1; overflow-y: auto; padding: 8px; }

.session-item {
  display: flex; align-items: center;
  padding: 10px 12px; border-radius: 8px;
  cursor: pointer; margin-bottom: 2px;
  transition: background 0.15s;
}

.session-item:hover { background: #e8ecf0; }
.session-item.active { background: #f0fdf4; border: 1px solid #2da44e33; }

.session-info { flex: 1; min-width: 0; }

.session-repo {
  font-size: 13px; font-weight: 600; color: #1f2328;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  display: flex; align-items: center;
}

.session-preview {
  font-size: 12px; color: #656d76;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px;
}

.session-time { font-size: 11px; color: #8b949e; margin-top: 2px; }
.session-delete { opacity: 0; transition: opacity 0.15s; }
.session-item:hover .session-delete { opacity: 1; }
.no-sessions { text-align: center; color: #8b949e; font-size: 13px; padding: 40px 16px; }
.sidebar-overlay { display: none; }

/* ===== 主对话区 ===== */
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }

.chat-header {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 20px; border-bottom: 1px solid #e8ecf0; flex-shrink: 0;
}

.sidebar-toggle {
  display: none; width: 32px; height: 32px;
  border-radius: 6px; border: 1px solid #d0d7de;
  background: #fff; cursor: pointer;
  align-items: center; justify-content: center; color: #656d76; flex-shrink: 0;
  transition: all 0.15s;
}

.sidebar-toggle:hover { border-color: #2da44e; color: #2da44e; background: #f0fdf4; }

.page-header { display: flex; justify-content: space-between; align-items: center; flex: 1; }
.page-title { font-size: 18px; font-weight: 600; color: #1f2328; margin: 0; }

.url-card { padding: 12px 20px; border-bottom: 1px solid #e8ecf0; flex-shrink: 0; }
.url-row { display: flex; gap: 8px; }
.url-row :deep(.el-input) { flex: 1; }
.task-id-hint { font-size: 12px; color: #2da44e; margin-top: 6px; }

/* ===== 聊天区 ===== */
.chat-card { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.chat-messages { flex: 1; overflow-y: auto; padding: 16px 0; }

.empty-chat {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; height: 100%; min-height: 200px; color: #8b949e;
}

.empty-chat p { margin-top: 12px; font-size: 14px; }

.message { display: flex; gap: 12px; margin-bottom: 20px; padding: 0 20px; }
.message.user { flex-direction: row-reverse; }

.message-avatar {
  width: 36px; height: 36px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}

.message.user .message-avatar { background: #f6f8fa; color: #656d76; }
.message.assistant .message-avatar { background: #f0fdf4; }

.message-content { max-width: 70%; }

.message-text {
  padding: 10px 14px; border-radius: 10px;
  font-size: 14px; line-height: 1.7; word-break: break-word;
}

/* Markdown styles */
.message-text :deep(code) {
  background: #e8ecf0; padding: 2px 6px; border-radius: 4px;
  font-family: 'SF Mono', 'Fira Code', monospace; font-size: 13px;
}

.message-text :deep(pre) {
  background: #1f2328; color: #e6edf3;
  padding: 12px 16px; border-radius: 8px;
  overflow-x: auto; margin: 8px 0;
}

.message-text :deep(pre code) { background: none; padding: 0; color: inherit; }

.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3),
.message-text :deep(h4) {
  font-weight: 700; color: #1f2328; margin: 12px 0 6px;
}

.message-text :deep(h1) { font-size: 18px; }
.message-text :deep(h2) { font-size: 16px; }
.message-text :deep(h3) { font-size: 15px; }

.message-text :deep(ul),
.message-text :deep(ol) {
  padding-left: 20px; margin: 6px 0;
}

.message-text :deep(li) { margin: 3px 0; }

.message-text :deep(p) { margin: 6px 0; }
.message-text :deep(p:first-child) { margin-top: 0; }
.message-text :deep(p:last-child) { margin-bottom: 0; }

.message-text :deep(blockquote) {
  border-left: 3px solid #2da44e; padding-left: 12px;
  color: #656d76; margin: 8px 0;
}

.message-text :deep(table) {
  border-collapse: collapse; margin: 8px 0; font-size: 13px;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid #d8dee4; padding: 6px 10px; text-align: left;
}

.message-text :deep(th) { background: #f6f8fa; font-weight: 600; }

.message.user .message-text {
  background: #f0fdf4; color: #1f2328; border-bottom-right-radius: 2px;
}

.message.assistant .message-text {
  background: #f6f8fa; color: #1f2328; border-bottom-left-radius: 2px;
}

/* Stream cursor */
.stream-cursor {
  display: inline-block; width: 2px; height: 16px;
  background: #2da44e; margin-left: 2px; vertical-align: text-bottom;
  animation: blink 1s infinite;
}

@keyframes blink { 0%, 50% { opacity: 1; } 51%, 100% { opacity: 0; } }

.chat-input {
  display: flex; gap: 12px; padding: 14px 20px;
  border-top: 1px solid #e8ecf0; flex-shrink: 0;
}

.chat-input :deep(.el-textarea) { flex: 1; }

/* ===== 响应式 ===== */
@media (max-width: 767px) {
  .chat-page { height: calc(100vh - 72px); border-radius: 0; border: none; }

  .session-sidebar {
    position: fixed; left: 0; top: 0; bottom: 0; z-index: 200;
    width: 280px; min-width: 280px;
    transform: translateX(-100%); transition: transform 0.25s ease;
  }

  .session-sidebar.sidebar-open { transform: translateX(0); box-shadow: 4px 0 24px rgba(0,0,0,0.12); }
  .sidebar-close-btn { display: inline-flex; }

  .sidebar-overlay {
    display: block; position: fixed; inset: 0;
    background: rgba(0,0,0,0.3); z-index: 199; backdrop-filter: blur(2px);
  }

  .sidebar-toggle { display: flex; }
  .chat-header { padding: 12px 16px; }
  .page-title { font-size: 16px; }
  .url-card { padding: 10px 16px; }
  .url-row { flex-direction: column; }
  .message { padding: 0 12px; }
  .message-content { max-width: 85%; }
  .chat-input { padding: 12px 16px; }
}

@media (min-width: 768px) and (max-width: 1024px) {
  .session-sidebar { width: 220px; min-width: 220px; }
}
</style>
