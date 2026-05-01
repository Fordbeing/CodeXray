<template>
  <div class="chat-layout">
    <!-- 会话侧边栏 -->
    <div class="session-sidebar">
      <div class="sidebar-header">
        <span class="sidebar-title">对话历史</span>
        <el-button type="primary" size="small" @click="handleNewSession" :disabled="!repoUrl.trim()">
          <el-icon><Plus /></el-icon>
          新建
        </el-button>
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

    <!-- 主对话区 -->
    <div class="chat-main">
      <div class="page-header">
        <h1 class="page-title">代码问答</h1>
        <el-tag v-if="currentTaskId" type="success" effect="light" size="large">
          <el-icon style="margin-right: 4px"><Connection /></el-icon>
          RAG 模式（已关联分析任务）
        </el-tag>
      </div>

      <!-- 仓库 URL -->
      <el-card class="url-card" shadow="never">
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
      </el-card>

      <!-- 对话区域 -->
      <el-card class="chat-card" shadow="never">
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
              <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
            </div>
          </div>

          <div v-if="chatting" class="message assistant">
            <div class="message-avatar">
              <el-icon :size="20" color="#2da44e"><Cpu /></el-icon>
            </div>
            <div class="message-content">
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
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
          <el-button type="primary" :loading="chatting" :disabled="!canSend" @click="handleSend">
            发送
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { chatWithRepo, getChatHistory, listChatSessions, createChatSession, deleteChatSession } from '../api/chat'

const route = useRoute()

const repoUrl = ref('')
const question = ref('')
const chatting = ref(false)
const loadingSessions = ref(false)
const messages = ref([])
const messagesRef = ref(null)
const currentSessionId = ref(null)
const currentTaskId = ref(null)
const sessions = ref([])

const canSend = computed(() => repoUrl.value.trim() && question.value.trim() && !chatting.value)

onMounted(() => {
  if (route.query.repoUrl) {
    repoUrl.value = route.query.repoUrl
  }
  if (route.query.taskId) {
    currentTaskId.value = route.query.taskId
  }
  if (repoUrl.value) {
    loadSessions()
  }
})

watch(() => route.query, (q) => {
  if (q.repoUrl && q.repoUrl !== repoUrl.value) {
    repoUrl.value = q.repoUrl
  }
  if (q.taskId) {
    currentTaskId.value = q.taskId
  }
  if (q.repoUrl) {
    loadSessions()
  }
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
  try {
    const history = await getChatHistory(session.sessionId)
    messages.value = (history || []).map(h => ({
      role: h.role,
      content: h.content
    }))
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
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

async function handleSend() {
  if (!canSend.value) return

  const q = question.value.trim()
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  chatting.value = true
  await scrollToBottom()

  try {
    const result = await chatWithRepo(repoUrl.value, q, currentSessionId.value, currentTaskId.value)
    currentSessionId.value = result.sessionId
    messages.value.push({ role: 'assistant', content: result.answer })
    await loadSessions()
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '抱歉，发生了错误：' + (e.message || '未知错误') })
  } finally {
    chatting.value = false
    await scrollToBottom()
  }
}

function renderMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}

function formatRepoName(url) {
  if (!url) return '未知仓库'
  const match = url.match(/github\.com\/([^/]+\/[^/]+)/)
  return match ? match[1] : url.length > 30 ? url.slice(0, 30) + '...' : url
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const now = new Date()
  const diffMs = now - d
  if (diffMs < 60000) return '刚刚'
  if (diffMs < 3600000) return Math.floor(diffMs / 60000) + '分钟前'
  if (diffMs < 86400000) return Math.floor(diffMs / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  gap: 0;
  height: calc(100vh - 76px);
  margin: -28px -36px -48px;
}

.session-sidebar {
  width: 260px;
  border-right: 1px solid #d0d7de;
  background: #f6f8fa;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #d0d7de;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2328;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s;
}

.session-item:hover {
  background: #e8ecf0;
}

.session-item.active {
  background: #f0fdf4;
  border: 1px solid #2da44e33;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-repo {
  font-size: 13px;
  font-weight: 600;
  color: #1f2328;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
}

.session-preview {
  font-size: 12px;
  color: #656d76;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

.session-time {
  font-size: 11px;
  color: #8b949e;
  margin-top: 2px;
}

.session-delete {
  opacity: 0;
  transition: opacity 0.15s;
}

.session-item:hover .session-delete {
  opacity: 1;
}

.no-sessions {
  text-align: center;
  color: #8b949e;
  font-size: 13px;
  padding: 40px 16px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px 24px;
  min-width: 0;
  overflow: hidden;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2328;
  margin: 0;
}

.url-card {
  margin-bottom: 16px;
}

.url-card :deep(.el-card__body) {
  padding: 12px 16px;
}

.url-row {
  display: flex;
  gap: 8px;
}

.task-id-hint {
  font-size: 12px;
  color: #2da44e;
  margin-top: 6px;
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
  min-height: 300px;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: #8b949e;
}

.empty-chat p {
  margin-top: 12px;
  font-size: 14px;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding: 0 16px;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: #f6f8fa;
  color: #656d76;
}

.message.assistant .message-avatar {
  background: #f0fdf4;
}

.message-content {
  max-width: 70%;
}

.message-text {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.message-text :deep(code) {
  background: #e8ecf0;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 13px;
}

.message-text :deep(pre) {
  background: #1f2328;
  color: #e6edf3;
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-text :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}

.message.user .message-text {
  background: #f0fdf4;
  color: #1f2328;
  border-bottom-right-radius: 2px;
}

.message.assistant .message-text {
  background: #f6f8fa;
  color: #1f2328;
  border-bottom-left-radius: 2px;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px 16px;
  background: #f6f8fa;
  border-radius: 8px;
  border-bottom-left-radius: 2px;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8b949e;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1); }
}

.chat-input {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #e8ecf0;
  flex-shrink: 0;
}

.chat-input .el-input {
  flex: 1;
}
</style>
