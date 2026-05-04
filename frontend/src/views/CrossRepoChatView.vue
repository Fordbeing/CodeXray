<template>
  <div class="cross-chat-page">
    <h1 class="page-title">跨仓库问答</h1>
    <p class="page-desc">同时分析多个已分析的仓库，发现跨仓库的模式和问题</p>

    <!-- 仓库选择 -->
    <el-card class="repo-select-card" shadow="never">
      <div class="select-header">
        <span>选择仓库</span>
        <el-checkbox v-model="selectAll" @change="toggleSelectAll">全部仓库</el-checkbox>
      </div>
      <div v-if="loadingTasks" class="select-loading">
        <el-skeleton :rows="2" animated />
      </div>
      <div v-else-if="completedTasks.length === 0" class="select-empty">
        暂无已完成的分析任务，请先
        <el-button text type="primary" @click="$router.push('/analyze')">分析仓库</el-button>
      </div>
      <el-checkbox-group v-else v-model="selectedTaskIds" class="repo-checkboxes">
        <el-checkbox
          v-for="task in completedTasks"
          :key="task.taskId"
          :value="task.taskId"
          class="repo-checkbox"
        >
          <div class="repo-info">
            <span class="repo-name">{{ shortenUrl(task.repoUrl) }}</span>
            <span class="repo-time" v-if="task.createdAt">{{ formatRelative(task.createdAt) }}</span>
          </div>
        </el-checkbox>
      </el-checkbox-group>
    </el-card>

    <!-- 对话区域 -->
    <el-card class="chat-card" shadow="never">
      <div class="messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="empty-chat">
          <el-icon :size="48" color="#d0d7de"><ChatDotRound /></el-icon>
          <p>选择仓库后，开始跨仓库智能问答</p>
          <div class="suggestion-tags">
            <el-tag
              v-for="q in suggestions"
              :key="q"
              class="suggestion-tag"
              effect="plain"
              type="success"
              @click="sendSuggestion(q)"
            >{{ q }}</el-tag>
          </div>
        </div>
        <div
          v-for="(msg, i) in messages"
          :key="i"
          :class="['message', msg.role]"
        >
          <div class="msg-avatar">
            <span v-if="msg.role === 'user'">你</span>
            <span v-else class="ai-avatar">AI</span>
          </div>
          <div class="msg-body">
            <div
              class="msg-content"
              v-html="cachedMd(msg)"
            ></div>
            <div v-if="msg.role === 'assistant' && msg.source" class="msg-source">
              来源: {{ msg.source }}
            </div>
          </div>
        </div>
        <div v-if="streaming" class="message assistant">
          <div class="msg-avatar"><span class="ai-avatar">AI</span></div>
          <div class="msg-body">
            <div class="msg-content" v-html="cachedMd(streamMsg)"></div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          placeholder="输入跨仓库问题，如：这些项目中哪个的测试覆盖率最高？"
          :disabled="streaming || selectedTaskIds.length === 0"
          @keyup.enter.ctrl="sendQuestion"
        />
        <el-button
          type="primary"
          :loading="streaming"
          :disabled="!question.trim() || selectedTaskIds.length === 0"
          @click="sendQuestion"
        >
          发送
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import { listTasks } from '../api/analysis'
import { sendCrossRepoChatStream } from '../api/chat'
import { shortenUrl } from '../utils/status'
import { formatRelative } from '../utils/format'
import { marked } from 'marked'

marked.setOptions({ breaks: true, gfm: true, pedantic: false })

const completedTasks = ref([])
const selectedTaskIds = ref([])
const selectAll = ref(false)
const loadingTasks = ref(true)
const messages = ref([])
const question = ref('')
const streaming = ref(false)
const streamMsg = reactive({ content: '', _html: '', _raw: '' })
const messagesRef = ref(null)

const suggestions = [
  '这些项目的架构模式有什么异同？',
  '哪个项目的代码质量最好？为什么？',
  '这些项目中常见的安全风险有哪些？',
  '对比这些项目的测试策略'
]

// 缓存 Markdown 渲染
function cachedMd(msg) {
  if (!msg.content) {
    msg._html = ''
    return ''
  }
  if (msg._html !== undefined && msg._raw === msg.content) return msg._html
  try {
    msg._html = marked.parse(msg.content)
  } catch {
    msg._html = msg.content.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
  }
  msg._raw = msg.content
  return msg._html
}

// 流式渲染节流：用 requestAnimationFrame 避免闪烁
let _renderTimer = null

function scheduleStreamRender() {
  if (!_renderTimer) {
    _renderTimer = requestAnimationFrame(() => {
      _renderTimer = null
      const atBottom = isNearBottom()
      if (atBottom) {
        nextTick(() => scrollToBottom())
      }
    })
  }
}

function isNearBottom() {
  if (!messagesRef.value) return true
  const { scrollTop, scrollHeight, clientHeight } = messagesRef.value
  return scrollHeight - scrollTop - clientHeight < 100
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function toggleSelectAll(val) {
  if (val) {
    selectedTaskIds.value = completedTasks.value.map(t => t.taskId)
  } else {
    selectedTaskIds.value = []
  }
}

watch(selectedTaskIds, (val) => {
  selectAll.value = val.length === completedTasks.value.length
})

async function sendQuestion() {
  const q = question.value.trim()
  if (!q || streaming.value) return
  question.value = ''

  messages.value.push({ role: 'user', content: q, _html: '', _raw: '' })
  streaming.value = true
  streamMsg.content = ''
  streamMsg._html = ''
  streamMsg._raw = ''
  scrollToBottom()

  let tokenTimer = null

  try {
    const { stream } = await sendCrossRepoChatStream(q, selectedTaskIds.value)
    for await (const event of stream) {
      if (event.type === 'token') {
        streamMsg.content += event.data
        scheduleStreamRender()
        // 重置超时：2s 无新 token 则自动停止
        clearTimeout(tokenTimer)
        tokenTimer = setTimeout(() => {
          finishStreaming()
        }, 2000)
      } else if (event.type === 'done') {
        clearTimeout(tokenTimer)
        break
      } else if (event.type === 'error') {
        clearTimeout(tokenTimer)
        ElMessage.error(event.data)
        break
      }
    }
  } catch (e) {
    clearTimeout(tokenTimer)
    ElMessage.error('发送失败: ' + e.message)
  }

  finishStreaming()
}

function finishStreaming() {
  if (!streaming.value) return
  if (streamMsg.content) {
    messages.value.push({ role: 'assistant', content: streamMsg.content, _html: '', _raw: '' })
  }
  streaming.value = false
  streamMsg.content = ''
  streamMsg._html = ''
  streamMsg._raw = ''
  scrollToBottom()
}

function sendSuggestion(q) {
  question.value = q
  sendQuestion()
}

onMounted(async () => {
  try {
    const tasks = await listTasks(50)
    completedTasks.value = (tasks || []).filter(t => t.status === 'COMPLETED')
    if (completedTasks.value.length > 0) {
      selectedTaskIds.value = completedTasks.value.map(t => t.taskId)
      selectAll.value = true
    }
  } catch {
    // ignore
  } finally {
    loadingTasks.value = false
  }
})
</script>

<style scoped>
.cross-chat-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 8px 0;
}

.page-desc {
  color: #656d76;
  font-size: 14px;
  margin: 0 0 20px 0;
}

.repo-select-card {
  margin-bottom: 16px;
}

.select-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2328;
}

.select-loading, .select-empty {
  padding: 16px 0;
  color: #8b949e;
  font-size: 13px;
  text-align: center;
}

.repo-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.repo-checkbox {
  margin-right: 0 !important;
}

.repo-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.repo-name {
  font-size: 13px;
  color: #1f2328;
}

.repo-time {
  font-size: 11px;
  color: #8b949e;
}

.chat-card {
  min-height: 500px;
  display: flex;
  flex-direction: column;
}

.messages {
  flex: 1;
  overflow-y: auto;
  max-height: 500px;
  padding: 16px 0;
}

.empty-chat {
  text-align: center;
  padding: 60px 20px;
  color: #8b949e;
}

.empty-chat p {
  margin: 16px 0;
  font-size: 14px;
}

.suggestion-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 16px;
}

.suggestion-tag {
  cursor: pointer;
  height: auto;
  padding: 6px 12px;
  white-space: normal;
  line-height: 1.5;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.user .msg-avatar {
  background: #3b82f6;
  color: #fff;
}

.ai-avatar {
  background: #2da44e;
  color: #fff;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.msg-body {
  flex: 1;
  min-width: 0;
}

.msg-content {
  font-size: 14px;
  color: #1f2328;
  line-height: 1.8;
  background: #f6f8fa;
  padding: 12px 16px;
  border-radius: 8px;
}

.user .msg-content {
  background: #eff6ff;
}

.msg-content :deep(code) {
  background: #e8ecf0;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.msg-content :deep(pre) {
  background: #1f2328;
  color: #e6edf3;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}

.msg-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}

.msg-content :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
}

.msg-content :deep(th),
.msg-content :deep(td) {
  border: 1px solid #d0d7de;
  padding: 6px 12px;
  text-align: left;
}

.msg-content :deep(th) {
  background: #f6f8fa;
  font-weight: 600;
}

.msg-content :deep(ul),
.msg-content :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.msg-content :deep(li) {
  margin: 4px 0;
}

.msg-content :deep(blockquote) {
  border-left: 4px solid #d0d7de;
  margin: 8px 0;
  padding: 4px 16px;
  color: #656d76;
}

.msg-source {
  font-size: 11px;
  color: #8b949e;
  margin-top: 6px;
}

.chat-input {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding-top: 16px;
  border-top: 1px solid #e8ecf0;
}

.chat-input .el-input {
  flex: 1;
}

:global(html.dark) .page-title,
:global(html.dark) .select-header {
  color: #e6edf3;
}

:global(html.dark) .msg-content {
  background: #2d333b;
  color: #e6edf3;
}

:global(html.dark) .user .msg-content {
  background: rgba(59, 130, 246, 0.15);
}

:global(html.dark) .msg-content :deep(th) {
  background: #2d333b;
}

:global(html.dark) .msg-content :deep(th),
:global(html.dark) .msg-content :deep(td) {
  border-color: #444c56;
}
</style>
