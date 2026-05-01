<template>
  <div class="chat-page">
    <div class="page-header">
      <h1 class="page-title">代码问答</h1>
      <el-button v-if="messages.length > 0" @click="newSession" plain size="small">
        新建会话
      </el-button>
    </div>

    <!-- 仓库 URL -->
    <el-card class="url-card" shadow="never">
      <div class="url-row">
        <el-input
          v-model="repoUrl"
          placeholder="输入 GitHub 仓库地址"
          size="large"
          clearable
        >
          <template #prefix>
            <el-icon><Link /></el-icon>
          </template>
        </el-input>
      </div>
    </el-card>

    <!-- 对话区域 -->
    <el-card class="chat-card" shadow="never">
      <div class="chat-messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="empty-chat">
          <el-icon :size="48" color="#d0d7de"><ChatDotRound /></el-icon>
          <p>输入仓库地址和问题，开始与代码对话</p>
        </div>

        <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
          <div class="message-avatar">
            <el-icon v-if="msg.role === 'user'" :size="20"><User /></el-icon>
            <el-icon v-else :size="20" color="#2da44e"><Cpu /></el-icon>
          </div>
          <div class="message-content">
            <div class="message-text">{{ msg.content }}</div>
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
          placeholder="输入你的问题..."
          resize="none"
          @keyup.enter.exact="handleSend"
        />
        <el-button type="primary" :loading="chatting" :disabled="!canSend" @click="handleSend">
          发送
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { chatWithRepo } from '../api/chat'

const repoUrl = ref('')
const question = ref('')
const chatting = ref(false)
const messages = ref([])
const messagesRef = ref(null)
const sessionId = ref(null)

const canSend = computed(() => repoUrl.value.trim() && question.value.trim() && !chatting.value)

function newSession() {
  sessionId.value = null
  messages.value = []
}

async function handleSend() {
  if (!canSend.value) return

  const q = question.value.trim()
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  chatting.value = true
  await scrollToBottom()

  try {
    const result = await chatWithRepo(repoUrl.value, q, sessionId.value)
    sessionId.value = result.sessionId
    messages.value.push({ role: 'assistant', content: result.answer })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '抱歉，发生了错误：' + (e.message || '未知错误') })
  } finally {
    chatting.value = false
    await scrollToBottom()
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 20px 0;
}

.url-card {
  margin-bottom: 20px;
}

.chat-card {
  display: flex;
  flex-direction: column;
}

.chat-messages {
  min-height: 400px;
  max-height: 600px;
  overflow-y: auto;
  padding: 16px 0;
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
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
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
}

.chat-input .el-input {
  flex: 1;
}
</style>
