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
            placeholder="新建对话的仓库地址（可选，用于 RAG 精确问答）"
            size="large"
            clearable
          >
            <template #prefix>
              <el-icon><Link /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" size="large" @click="handleNewSession" :disabled="!repoUrl.trim()">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>
            新建对话
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
            <p v-if="!currentSessionId">请先在上方输入仓库地址，点击「新建对话」开始</p>
            <p v-else-if="currentTaskId">已关联分析任务，可对该仓库代码进行精确问答</p>
            <p v-else>输入问题，开始与代码对话</p>
          </div>

          <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'" :size="20"><User /></el-icon>
              <el-icon v-else :size="20" color="#2da44e"><Cpu /></el-icon>
            </div>
            <div class="message-content">
              <!-- 等待中：还没有任何内容 -->
              <div v-if="msg.pending && !msg.streaming" class="message-text markdown-body">
                <span class="loading-dots">
                  <span></span><span></span><span></span>
                </span>
                <span class="pending-text">AI 正在思考中...</span>
              </div>
              <!-- 流式输出中：有内容 + 光标 -->
              <div v-else-if="msg.streaming" class="message-text markdown-body streaming-msg">
                <div v-html="renderMd(msg.content)"></div>
                <span class="stream-cursor"></span>
              </div>
              <!-- 错误 -->
              <div v-else-if="msg.error" class="message-text markdown-body error-text">
                {{ msg.content }}
              </div>
              <!-- 完成 -->
              <div v-else class="message-text markdown-body" v-html="renderMd(msg.content)"></div>
              <!-- 消息操作栏 -->
              <div v-if="!msg.pending && !msg.streaming && !msg.error && msg.content" class="message-actions">
                <button class="action-btn" @click="copyMessage(msg.content)" title="复制内容">
                  <svg viewBox="0 0 16 16" width="14" height="14"><path fill="currentColor" d="M0 6.75C0 5.784.784 5 1.75 5h1.5a.75.75 0 0 1 0 1.5h-1.5a.25.25 0 0 0-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 0 0 .25-.25v-1.5a.75.75 0 0 1 1.5 0v1.5A1.75 1.75 0 0 1 9.25 16h-7.5A1.75 1.75 0 0 1 0 14.25Z"/><path fill="currentColor" d="M5 1.75C5 .784 5.784 0 6.75 0h7.5C15.216 0 16 .784 16 1.75v7.5A1.75 1.75 0 0 1 14.25 11h-7.5A1.75 1.75 0 0 1 5 9.25Zm1.75-.25a.25.25 0 0 0-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 0 0 .25-.25v-7.5a.25.25 0 0 0-.25-.25Z"/></svg>
                </button>
              </div>
            </div>
          </div>
          <div class="scroll-anchor"></div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input" :class="{ 'input-locked': !currentSessionId }">
          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            :placeholder="currentSessionId ? '输入你的问题... (Enter 发送)' : '请先新建对话'"
            :disabled="!currentSessionId"
            resize="none"
            @keyup.enter.exact="handleSend"
          />
          <el-button type="primary" :loading="hasPending" :disabled="!canSend" @click="handleSend">
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getChatHistory, listChatSessions, createChatSession, deleteChatSession, sendChatAsync, getChatResult } from '../api/chat'
import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true,
  pedantic: false,
})

// Cache last parsed result to avoid re-parsing identical content on every poll tick
let _lastMdInput = ''
let _lastMdOutput = ''

function renderMd(text) {
  if (!text) return ''
  if (text === _lastMdInput) return _lastMdOutput
  try {
    _lastMdOutput = marked.parse(text)
  } catch {
    _lastMdOutput = text.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
  }
  _lastMdInput = text
  return _lastMdOutput
}

const route = useRoute()
const router = useRouter()

const repoUrl = ref('')
const question = ref('')
const loadingSessions = ref(false)
const messages = ref([])
const messagesRef = ref(null)
const currentSessionId = ref(null)
const currentTaskId = ref(null)
const sessions = ref([])
const sidebarOpen = ref(false)

// 活跃的轮询: pollId → { timer, messageIndex }
const activePolls = new Map()

// 持久化到 localStorage 的 key
const STORAGE_KEY = 'codexray_chat_state'

const canSend = computed(() => !!currentSessionId.value && question.value.trim() && !hasPending.value)
const hasPending = computed(() => messages.value.some(m => m.pending || m.streaming))

function onAuthChange(e) {
  if (!e.detail) {
    // 用户登出，清除所有对话状态
    for (const [, poll] of activePolls) {
      clearInterval(poll.timer)
    }
    activePolls.clear()
    messages.value = []
    sessions.value = []
    currentSessionId.value = null
    currentTaskId.value = null
    repoUrl.value = ''
    localStorage.removeItem(STORAGE_KEY)
  } else {
    // 用户登录，自动加载会话列表
    loadSessions()
  }
}

onMounted(async () => {
  if (window.innerWidth >= 768) sidebarOpen.value = true

  const fromRepoUrl = route.query.repoUrl
  const fromTaskId = route.query.taskId

  if (fromRepoUrl) repoUrl.value = fromRepoUrl
  if (fromTaskId) currentTaskId.value = fromTaskId

  restoreState()

  // 先加载会话列表
  await loadSessions()

  // 从其他页面跳转过来（带 repoUrl），自动进入会话
  if (fromRepoUrl) {
    await autoEnterSession(fromRepoUrl, fromTaskId)
    // 清除 query 参数，防止重复触发
    router.replace({ path: route.path })
  }

  window.addEventListener('auth-change', onAuthChange)
  nextTick(() => addCodeCopyButtons())
})

onUnmounted(() => {
  saveState()
  for (const [, poll] of activePolls) {
    clearInterval(poll.timer)
  }
  window.removeEventListener('auth-change', onAuthChange)
})

watch(() => route.query, async (q) => {
  if (q.repoUrl) {
    repoUrl.value = q.repoUrl
    if (q.taskId) currentTaskId.value = q.taskId
    await loadSessions()
    await autoEnterSession(q.repoUrl, q.taskId)
  }
})

// 状态持久化
function saveState() {
  const state = {
    repoUrl: repoUrl.value,
    currentSessionId: currentSessionId.value,
    currentTaskId: currentTaskId.value,
    messages: messages.value,
    sessions: sessions.value,
    savedAt: Date.now(),
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch { /* ignore */ }
}

function restoreState() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (!saved) return
    const state = JSON.parse(saved)
    if (state.repoUrl && !repoUrl.value) repoUrl.value = state.repoUrl
    if (state.currentSessionId) currentSessionId.value = state.currentSessionId
    if (state.currentTaskId && !currentTaskId.value) currentTaskId.value = state.currentTaskId
    // 恢复活跃轮询（仅 5 分钟内的，超过的说明后端已清理，由 loadSessions 加载完整结果）
    if (state.messages && state.savedAt) {
      const age = Date.now() - state.savedAt
      if (age < 5 * 60 * 1000) {
        const pollingMsgs = state.messages.filter(m => (m.pending || m.streaming) && m.pollId)
        if (pollingMsgs.length > 0) {
          messages.value = state.messages
          resumePolls()
        }
      }
    }
  } catch { /* ignore */ }
}

function resumePolls() {
  messages.value.forEach((msg, idx) => {
    if ((msg.pending || msg.streaming) && msg.pollId) {
      startPolling(msg.pollId, idx)
    }
  })
}

function startPolling(pollId, messageIndex) {
  if (activePolls.has(pollId)) {
    clearInterval(activePolls.get(pollId).timer)
  }

  const timer = setInterval(async () => {
    try {
      const result = await getChatResult(pollId)

      // 结果已过期（用户离开期间后端已完成并清理），从 API 重新加载
      if (!result) {
        clearInterval(timer)
        activePolls.delete(pollId)
        messages.value[messageIndex] = {
          role: 'assistant',
          content: messages.value[messageIndex]?.content || '（消息已过期，请刷新页面重新查看）',
        }
        saveState()
        loadSessions()
        return
      }

      // 流式进行中：更新内容
      if (result.streaming && !result.done) {
        if (messages.value[messageIndex]) {
          messages.value[messageIndex].content = result.content || ''
          messages.value[messageIndex].streaming = true
          messages.value[messageIndex].pending = false
          scrollToBottom()
        }
        saveState()
        return
      }

      // 完成
      if (result.done) {
        clearInterval(timer)
        activePolls.delete(pollId)
        if (result.error) {
          messages.value[messageIndex] = {
            role: 'assistant',
            content: result.error,
            error: true,
          }
        } else {
          messages.value[messageIndex] = {
            role: 'assistant',
            content: result.content,
          }
        }
        saveState()
        scrollToBottom()
        nextTick(() => addCodeCopyButtons())
        loadSessions()
      }
    } catch (e) {
      console.error('轮询失败:', e)
    }
  }, 800)

  activePolls.set(pollId, { timer, messageIndex })
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    // 始终加载当前用户的所有会话，不按 repoUrl 过滤
    sessions.value = await listChatSessions() || []

    // 同步当前会话的消息：确保左侧列表和右侧消息一致
    if (currentSessionId.value) {
      const exists = sessions.value.some(s => s.sessionId === currentSessionId.value)
      if (exists) {
        // 有活跃轮询时，不要覆盖正在流式输出的消息
        const hasActivePoll = activePolls.size > 0
        if (!hasActivePoll) {
          const history = await getChatHistory(currentSessionId.value)
          messages.value = (history || []).map(h => ({ role: h.role, content: h.content }))
        }
      } else {
        currentSessionId.value = null
        messages.value = []
      }
    } else if (messages.value.length > 0) {
      messages.value = []
    }
    saveState()
  } catch (e) {
    console.error('加载会话列表失败:', e)
  } finally {
    loadingSessions.value = false
  }
}

async function switchSession(session) {
  for (const [, poll] of activePolls) {
    clearInterval(poll.timer)
  }
  activePolls.clear()

  currentSessionId.value = session.sessionId
  // 切换会话时更新仓库地址和任务ID，方便用户继续对话
  if (session.repoUrl) repoUrl.value = session.repoUrl
  if (session.taskId) currentTaskId.value = session.taskId
  if (window.innerWidth < 768) sidebarOpen.value = false
  try {
    const history = await getChatHistory(session.sessionId)
    messages.value = (history || []).map(h => ({ role: h.role, content: h.content }))
    saveState()
    await scrollToBottom()
  } catch (e) {
    console.error('加载历史失败:', e)
    messages.value = []
  }
}

/**
 * 自动进入会话：查找该仓库的已有会话，没有则新建。
 * 用于从分析页面跳转过来时自动进入对话。
 */
async function autoEnterSession(url, taskId) {
  // 查找该仓库的最新会话
  const existing = sessions.value.find(s => s.repoUrl === url)
  if (existing) {
    // 已有会话，直接进入
    await switchSession(existing)
    return
  }

  // 没有已有会话，创建新的
  try {
    const result = await createChatSession(url, taskId)
    currentSessionId.value = result.sessionId
    messages.value = []
    await loadSessions()
    saveState()
  } catch (e) {
    ElMessage.error('创建会话失败: ' + (e.message || '未知错误'))
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
    saveState()
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
    saveState()
    ElMessage.success('已删除')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e.message || '未知错误'))
  }
}

async function handleSend() {
  if (!canSend.value) return

  const q = question.value.trim()
  messages.value.push({ role: 'user', content: q })
  question.value = ''

  const pendingIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', pending: true, streaming: false })
  await scrollToBottom()

  try {
    const resp = await sendChatAsync(repoUrl.value, q, currentSessionId.value, currentTaskId.value)
    const pollId = resp.pollId

    messages.value[pendingIndex].pollId = pollId

    if (!currentSessionId.value) {
      await loadSessions()
      if (sessions.value.length > 0 && !currentSessionId.value) {
        currentSessionId.value = sessions.value[0].sessionId
      }
    }

    saveState()
    startPolling(pollId, pendingIndex)
  } catch (e) {
    messages.value[pendingIndex] = {
      role: 'assistant',
      content: '发送失败：' + (e.message || '未知错误'),
      error: true,
    }
    saveState()
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

let _scrollTimer = null
async function scrollToBottom() {
  if (_scrollTimer) return
  _scrollTimer = requestAnimationFrame(() => {
    _scrollTimer = null
    if (messagesRef.value) {
      messagesRef.value.scrollTo({ top: messagesRef.value.scrollHeight, behavior: 'smooth' })
    }
  })
}

async function copyMessage(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function copyCode(btn) {
  const pre = btn.closest('pre')
  if (!pre) return
  const code = pre.querySelector('code')
  const text = code ? code.textContent : pre.textContent
  try {
    await navigator.clipboard.writeText(text)
    btn.classList.add('copied')
    btn.textContent = '已复制'
    setTimeout(() => {
      btn.classList.remove('copied')
      btn.textContent = '复制'
    }, 2000)
  } catch {
    ElMessage.error('复制失败')
  }
}

function addCodeCopyButtons() {
  if (!messagesRef.value) return
  const pres = messagesRef.value.querySelectorAll('.message-text pre')
  pres.forEach(pre => {
    if (pre.querySelector('.code-copy-btn')) return
    const wrapper = document.createElement('div')
    wrapper.className = 'code-block-wrapper'
    const btn = document.createElement('button')
    btn.className = 'code-copy-btn'
    btn.textContent = '复制'
    btn.addEventListener('click', () => copyCode(btn))
    const header = document.createElement('div')
    header.className = 'code-block-header'
    const lang = pre.querySelector('code')?.className?.match(/language-(\w+)/)?.[1]
    if (lang) {
      const langSpan = document.createElement('span')
      langSpan.className = 'code-lang'
      langSpan.textContent = lang
      header.appendChild(langSpan)
    }
    header.appendChild(btn)
    pre.parentNode.insertBefore(wrapper, pre)
    wrapper.appendChild(header)
    wrapper.appendChild(pre)
  })
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
.chat-messages { flex: 1; overflow-y: auto; overflow-anchor: auto; padding: 16px 0; scroll-behavior: smooth; }
.scroll-anchor { overflow-anchor: auto; height: 1px; }

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

.message-content { max-width: 70%; transition: width 0.1s ease; }

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

/* ===== 消息复制按钮 ===== */
.message-actions {
  display: flex;
  gap: 4px;
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.message:hover .message-actions {
  opacity: 1;
}

.message.user .message-actions {
  justify-content: flex-end;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #8b949e;
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn:hover {
  background: #e8ecf0;
  color: #1f2328;
}

/* ===== 代码块复制按钮 ===== */
.message-text :deep(.code-block-wrapper) {
  position: relative;
  margin: 8px 0;
  border-radius: 8px;
  overflow: hidden;
}

.message-text :deep(.code-block-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: #161b22;
  border-bottom: 1px solid #30363d;
}

.message-text :deep(.code-lang) {
  font-size: 12px;
  font-weight: 600;
  color: #8b949e;
  text-transform: uppercase;
}

.message-text :deep(.code-copy-btn) {
  font-size: 12px;
  color: #8b949e;
  background: transparent;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 3px 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.message-text :deep(.code-copy-btn:hover) {
  color: #e6edf3;
  border-color: #8b949e;
  background: #30363d;
}

.message-text :deep(.code-copy-btn.copied) {
  color: #2da44e;
  border-color: #2da44e;
}

.message-text :deep(.code-block-wrapper pre) {
  margin: 0;
  border-radius: 0;
}

.error-text {
  color: #cf222e !important;
  background: #fef2f2 !important;
  border: 1px solid #fecaca !important;
}

/* Loading dots animation */
.loading-dots {
  display: inline-flex; gap: 4px; align-items: center;
  margin-right: 8px; vertical-align: middle;
}

.loading-dots span {
  width: 8px; height: 8px; border-radius: 50%;
  background: #2da44e; opacity: 0.4;
  animation: dot-pulse 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) { animation-delay: 0s; }
.loading-dots span:nth-child(2) { animation-delay: 0.16s; }
.loading-dots span:nth-child(3) { animation-delay: 0.32s; }

@keyframes dot-pulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

.pending-text {
  font-size: 13px; color: #8b949e; font-style: italic;
}

/* Stream cursor */
.stream-cursor {
  display: inline-block; width: 2px; height: 16px;
  background: #2da44e; margin-left: 2px; vertical-align: text-bottom;
  animation: blink 1s infinite;
}

/* Streaming message - prevent layout thrash */
.streaming-msg {
  contain: layout style;
  will-change: contents;
}

@keyframes blink { 0%, 50% { opacity: 1; } 51%, 100% { opacity: 0; } }

.chat-input {
  display: flex; gap: 12px; padding: 14px 20px;
  border-top: 1px solid #e8ecf0; flex-shrink: 0;
}

.chat-input.input-locked {
  opacity: 0.6;
  background: #f8faf9;
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
