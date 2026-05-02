<template>
  <div class="settings-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">系统设置</h1>
        <p class="page-desc">修改配置后自动保存，无需手动点击</p>
      </div>
      <div class="save-status" :class="saveStatusClass">
        <el-icon v-if="saving" class="status-spinning"><Loading /></el-icon>
        <el-icon v-else-if="saveStatus === 'saved'"><CircleCheckFilled /></el-icon>
        <el-icon v-else-if="saveStatus === 'error'"><CircleCloseFilled /></el-icon>
        <span>{{ saveStatusText }}</span>
      </div>
    </div>

    <!-- 未配置警告 -->
    <div v-if="!aiConfigured" class="alert-banner alert-warning">
      <el-icon :size="20"><WarningFilled /></el-icon>
      <div class="alert-body">
        <div class="alert-title">AI 模型未配置</div>
        <div class="alert-desc">代码分析和对话功能需要先配置 AI 模型，请在下方「AI 配置」中选择服务商并填写 API Key</div>
      </div>
    </div>
    <div v-if="!mailConfigured" class="alert-banner alert-info">
      <el-icon :size="20"><InfoFilled /></el-icon>
      <div class="alert-body">
        <div class="alert-title">邮件服务未配置</div>
        <div class="alert-desc">热点日报推送需要 SMTP 邮件服务，点击下方快捷按钮配置常用邮箱</div>
      </div>
    </div>

    <div class="settings-grid" v-loading="loading">
      <!-- ==================== AI 配置 ==================== -->
      <div class="setting-card">
        <div class="card-header">
          <div class="card-icon" style="background: linear-gradient(135deg, #f0fdf4, #dcfce7)">
            <el-icon :size="20" color="#2da44e"><Cpu /></el-icon>
          </div>
          <div>
            <div class="card-title">AI 配置</div>
            <div class="card-desc">选择服务商，或使用已保存的配置</div>
          </div>
          <div class="card-header-actions">
            <el-button type="success" plain size="small" :loading="testing" @click="handleTestAi" :disabled="!aiConfigured">
              <el-icon style="margin-right: 4px"><Connection /></el-icon>
              测试连接
            </el-button>
          </div>
        </div>
        <div class="card-body">
          <!-- 已保存配置 -->
          <div v-if="aiPresets.length > 0" class="preset-section">
            <div class="preset-label">
              <el-icon><Clock /></el-icon>
              已保存的配置（点击切换）
            </div>
            <div class="preset-chips">
              <div
                v-for="p in aiPresets"
                :key="p.name"
                class="preset-chip"
                :class="{ active: isAiPresetActive(p) }"
                @click="loadAiPreset(p)"
              >
                <div class="chip-main">
                  <span class="chip-name">{{ p.name }}</span>
                  <span class="chip-sub">{{ p.ai_model }}</span>
                </div>
                <el-icon class="chip-delete" @click.stop="handleDeleteAiPreset(p.name)"><Close /></el-icon>
              </div>
            </div>
          </div>

          <div class="form-row">
            <div class="form-item">
              <div class="label-row">
                <label class="form-label">API Key</label>
                <el-button v-if="aiConfigured" link type="primary" size="small" @click="handleSaveAiPreset">
                  <el-icon style="margin-right: 2px"><FolderAdd /></el-icon>
                  保存当前配置
                </el-button>
              </div>
              <el-input v-model="form.ai_api_key" type="password" show-password placeholder="sk-xxxxxxxx" size="large">
                <template #prefix>
                  <el-icon><Key /></el-icon>
                </template>
              </el-input>
              <div class="form-hint">在对应服务商平台的 API Keys 页面获取</div>
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label class="form-label">服务商</label>
              <el-select v-model="selectedProvider" placeholder="选择服务商" size="large" filterable @change="onProviderChange" style="width: 100%">
                <el-option v-for="p in providers" :key="p.name" :label="p.name" :value="p.name">
                  <div class="provider-option">
                    <span class="provider-label">{{ p.name }}</span>
                    <span class="provider-hint">{{ p.hint }}</span>
                  </div>
                </el-option>
                <el-option label="其他 (自定义)" value="_custom">
                  <div class="provider-option">
                    <span class="provider-label">其他 (自定义)</span>
                    <span class="provider-hint">手动填写地址和模型</span>
                  </div>
                </el-option>
              </el-select>
            </div>
            <div class="form-item">
              <label class="form-label">模型</label>
              <template v-if="!isCustomProvider && selectedProvider">
                <el-select v-model="form.ai_model" placeholder="选择模型" size="large" filterable allow-create style="width: 100%">
                  <el-option v-for="m in currentModels" :key="m.value" :label="m.label" :value="m.value">
                    <div class="model-option">
                      <span class="model-name">{{ m.label }}</span>
                      <span v-if="m.tags" class="model-tags">
                        <el-tag v-for="t in m.tags" :key="t" size="small" :type="t === '最新' ? 'danger' : t === '推荐' ? 'success' : 'info'" effect="plain">{{ t }}</el-tag>
                      </span>
                    </div>
                  </el-option>
                </el-select>
              </template>
              <template v-else>
                <el-input v-model="form.ai_model" placeholder="如 deepseek-chat、gpt-4o" size="large" />
              </template>
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label class="form-label">API Base URL</label>
              <el-input
                v-model="form.ai_base_url"
                :placeholder="isCustomProvider ? 'https://your-api.com/v1' : '选择服务商后自动填充'"
                :disabled="!isCustomProvider && !!selectedProvider"
                size="large"
              />
              <div v-if="!isCustomProvider && selectedProvider" class="form-hint">
                已自动填充，如需修改请切换为「其他」
              </div>
              <div v-else-if="isCustomProvider" class="form-hint">
                兼容 OpenAI 或 Claude API 格式的服务地址
              </div>
            </div>
            <div class="form-item">
              <label class="form-label">最大 Token 数</label>
              <el-input-number v-model="form.ai_max_tokens" :min="1024" :max="128000" :step="1024" size="large" style="width: 200px" />
              <div class="form-hint">单次请求的最大输出长度，默认 4096</div>
            </div>
          </div>

          <!-- 测试结果 -->
          <div v-if="testResult" :class="['test-result', testResult.success ? 'test-success' : 'test-error']">
            <el-icon :size="16">
              <CircleCheckFilled v-if="testResult.success" />
              <CircleCloseFilled v-else />
            </el-icon>
            <span>{{ testResult.message }}</span>
          </div>
        </div>
      </div>

      <!-- ==================== 邮箱配置 ==================== -->
      <div class="setting-card">
        <div class="card-header">
          <div class="card-icon" style="background: linear-gradient(135deg, #eff6ff, #dbeafe)">
            <el-icon :size="20" color="#3b82f6"><Message /></el-icon>
          </div>
          <div>
            <div class="card-title">邮箱配置</div>
            <div class="card-desc">SMTP 邮件发送服务，用于热点日报和验证码</div>
          </div>
        </div>
        <div class="card-body">
          <!-- 快捷配置按钮 -->
          <div class="preset-section">
            <div class="preset-label">
              <el-icon><Promotion /></el-icon>
              快捷配置
            </div>
            <div class="quick-fill-btns">
              <el-button v-for="ep in emailProviders" :key="ep.name" size="small" @click="fillEmailProvider(ep)">
                {{ ep.name }}
              </el-button>
            </div>
          </div>

          <!-- 已保存邮箱配置 -->
          <div v-if="mailPresets.length > 0" class="preset-section" style="margin-top: 12px">
            <div class="preset-label">
              <el-icon><Clock /></el-icon>
              已保存的配置
            </div>
            <div class="preset-chips">
              <div
                v-for="p in mailPresets"
                :key="p.name"
                class="preset-chip"
                :class="{ active: isMailPresetActive(p) }"
                @click="loadMailPreset(p)"
              >
                <div class="chip-main">
                  <span class="chip-name">{{ p.name }}</span>
                  <span class="chip-sub">{{ p.mail_username }}</span>
                </div>
                <el-icon class="chip-delete" @click.stop="handleDeleteMailPreset(p.name)"><Close /></el-icon>
              </div>
            </div>
          </div>

          <div class="form-row" style="margin-top: 16px">
            <div class="form-item">
              <div class="label-row">
                <label class="form-label">邮件发送</label>
                <el-button v-if="mailConfigured" link type="primary" size="small" @click="handleSaveMailPreset">
                  <el-icon style="margin-right: 2px"><FolderAdd /></el-icon>
                  保存当前配置
                </el-button>
              </div>
              <el-switch v-model="form.mail_enabled" active-text="已启用" inactive-text="已禁用" size="large" />
              <div class="form-hint">启用后系统将通过 SMTP 发送热点日报和验证码邮件</div>
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label class="form-label">SMTP Host</label>
              <el-input v-model="form.mail_host" placeholder="smtp.example.com" size="large" />
            </div>
            <div class="form-item">
              <label class="form-label">SMTP Port</label>
              <el-input-number v-model="form.mail_port" :min="1" :max="65535" size="large" style="width: 160px" />
              <div class="form-hint">465 = SSL 加密，587 = TLS 加密</div>
            </div>
          </div>
          <div class="form-row two-col">
            <div class="form-item">
              <label class="form-label">发件人邮箱</label>
              <el-input v-model="form.mail_username" placeholder="your-email@example.com" size="large" />
            </div>
            <div class="form-item">
              <label class="form-label">密码 / 授权码</label>
              <el-input v-model="form.mail_password" type="password" show-password placeholder="非登录密码，是授权码" size="large" />
              <div class="form-hint">国内邮箱需在设置中开启 SMTP 并获取授权码</div>
            </div>
          </div>

          <!-- 邮箱帮助 -->
          <div class="help-guide">
            <div class="guide-title">
              <el-icon><QuestionFilled /></el-icon>
              如何获取授权码？
            </div>
            <div class="guide-tips">
              <div class="guide-tip-item">
                <strong>163 邮箱：</strong>设置 → POP3/SMTP/IMAP → 开启 SMTP → 按提示获取授权码
              </div>
              <div class="guide-tip-item">
                <strong>QQ 邮箱：</strong>设置 → 账户 → 往下翻找到 POP3/SMTP → 开启 → 发送短信获取授权码
              </div>
              <div class="guide-tip-item">
                <strong>Gmail：</strong>Google 账号 → 安全性 → 两步验证 → 应用专用密码
              </div>
              <div class="guide-tip-item">
                <strong>Outlook：</strong>账户设置 → 安全信息 → 应用密码
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== 对话设置 ==================== -->
      <div class="setting-card">
        <div class="card-header">
          <div class="card-icon" style="background: linear-gradient(135deg, #fefce8, #fef9c3)">
            <el-icon :size="20" color="#d29922"><ChatDotRound /></el-icon>
          </div>
          <div>
            <div class="card-title">对话设置</div>
            <div class="card-desc">代码问答的行为参数</div>
          </div>
        </div>
        <div class="card-body">
          <div class="form-row two-col">
            <div class="form-item">
              <label class="form-label">最大历史消息数</label>
              <el-input-number v-model="form.chat_max_history" :min="5" :max="50" size="large" style="width: 160px" />
              <div class="form-hint">每次对话保留的上下文消息数量，越大越准确但消耗更多 Token</div>
            </div>
            <div class="form-item">
              <label class="form-label">默认语言</label>
              <el-radio-group v-model="form.default_language" size="large">
                <el-radio-button value="zh">中文</el-radio-button>
                <el-radio-button value="en">English</el-radio-button>
              </el-radio-group>
              <div class="form-hint">AI 分析和对话的默认输出语言</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSettings, updateSettings, testAiConnection,
  getAiPresets, saveAiPreset, deleteAiPreset,
  getMailPresets, saveMailPreset, deleteMailPreset
} from '../api/settings'
import {
  Cpu, Message, ChatDotRound, QuestionFilled,
  WarningFilled, InfoFilled, Connection,
  CircleCheckFilled, CircleCloseFilled, Loading,
  Close, Clock, Promotion, FolderAdd, Key
} from '@element-plus/icons-vue'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)
const selectedProvider = ref('')
const saveStatus = ref('idle')
const loadDone = ref(false)
const loadingPreset = ref(false)

// Presets
const aiPresets = ref([])
const mailPresets = ref([])

// ---- Provider data ----
const providers = [
  {
    name: 'DeepSeek',
    baseUrl: 'https://api.deepseek.com',
    hint: '国产高性价比',
    models: [
      { value: 'deepseek-chat', label: 'DeepSeek-V3', tags: ['推荐'] },
      { value: 'deepseek-reasoner', label: 'DeepSeek-R1 (推理)', tags: ['最新'] }
    ]
  },
  {
    name: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    hint: 'GPT 系列',
    models: [
      { value: 'gpt-4o', label: 'GPT-4o', tags: ['推荐'] },
      { value: 'gpt-4o-mini', label: 'GPT-4o Mini', tags: ['性价比'] },
      { value: 'gpt-4-turbo', label: 'GPT-4 Turbo' },
      { value: 'gpt-4.1', label: 'GPT-4.1', tags: ['最新'] },
      { value: 'gpt-4.1-mini', label: 'GPT-4.1 Mini' },
      { value: 'gpt-4.1-nano', label: 'GPT-4.1 Nano' },
      { value: 'o3', label: 'o3 (推理)', tags: ['最新'] },
      { value: 'o3-mini', label: 'o3 Mini' },
      { value: 'o4-mini', label: 'o4 Mini', tags: ['最新'] }
    ]
  },
  {
    name: 'Claude (Anthropic)',
    baseUrl: 'https://api.anthropic.com',
    hint: 'Claude 系列',
    models: [
      { value: 'claude-sonnet-4-20250514', label: 'Claude Sonnet 4', tags: ['最新'] },
      { value: 'claude-opus-4-20250514', label: 'Claude Opus 4', tags: ['最强'] },
      { value: 'claude-3-7-sonnet-20250219', label: 'Claude 3.7 Sonnet' },
      { value: 'claude-3-5-sonnet-20241022', label: 'Claude 3.5 Sonnet' },
      { value: 'claude-3-5-haiku-20241022', label: 'Claude 3.5 Haiku', tags: ['性价比'] },
      { value: 'claude-3-haiku-20240307', label: 'Claude 3 Haiku' }
    ]
  },
  {
    name: '通义千问 (阿里云)',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    hint: '阿里云，免费额度',
    models: [
      { value: 'qwen-plus', label: 'Qwen-Plus', tags: ['推荐'] },
      { value: 'qwen-max', label: 'Qwen-Max', tags: ['最强'] },
      { value: 'qwen-turbo', label: 'Qwen-Turbo', tags: ['性价比'] },
      { value: 'qwen-long', label: 'Qwen-Long (长文本)' },
      { value: 'qwen3-235b-a22b', label: 'Qwen3-235B', tags: ['最新'] },
      { value: 'qwen3-32b', label: 'Qwen3-32B' },
      { value: 'qwen3-8b', label: 'Qwen3-8B' }
    ]
  },
  {
    name: 'Moonshot (月之暗面)',
    baseUrl: 'https://api.moonshot.cn/v1',
    hint: '长文本能力强',
    models: [
      { value: 'moonshot-v1-8k', label: 'Moonshot V1 8K', tags: ['推荐'] },
      { value: 'moonshot-v1-32k', label: 'Moonshot V1 32K' },
      { value: 'moonshot-v1-128k', label: 'Moonshot V1 128K' }
    ]
  },
  {
    name: '智谱 AI (GLM)',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    hint: '国产大模型',
    models: [
      { value: 'glm-4-plus', label: 'GLM-4-Plus', tags: ['推荐'] },
      { value: 'glm-4-flash', label: 'GLM-4-Flash', tags: ['免费'] },
      { value: 'glm-4-long', label: 'GLM-4-Long (长文本)' },
      { value: 'glm-zero-preview', label: 'GLM-Zero (推理)', tags: ['最新'] }
    ]
  },
  {
    name: '文心一言 (百度)',
    baseUrl: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop',
    hint: '百度大模型',
    models: [
      { value: 'ernie-4.0-8k', label: 'ERNIE 4.0', tags: ['推荐'] },
      { value: 'ernie-4.0-turbo-8k', label: 'ERNIE 4.0 Turbo' },
      { value: 'ernie-3.5-8k', label: 'ERNIE 3.5', tags: ['性价比'] },
      { value: 'ernie-speed-8k', label: 'ERNIE Speed', tags: ['免费'] }
    ]
  },
  {
    name: 'Groq',
    baseUrl: 'https://api.groq.com/openai/v1',
    hint: '超快推理速度',
    models: [
      { value: 'llama-3.3-70b-versatile', label: 'Llama 3.3 70B', tags: ['推荐'] },
      { value: 'llama-3.1-8b-instant', label: 'Llama 3.1 8B', tags: ['快'] },
      { value: 'mixtral-8x7b-32768', label: 'Mixtral 8x7B' },
      { value: 'gemma2-9b-it', label: 'Gemma 2 9B' }
    ]
  },
  {
    name: 'Ollama (本地)',
    baseUrl: 'http://localhost:11434/v1',
    hint: '本地部署，免费',
    models: [
      { value: 'llama3.3', label: 'Llama 3.3' },
      { value: 'qwen2.5', label: 'Qwen 2.5' },
      { value: 'deepseek-r1', label: 'DeepSeek R1' },
      { value: 'codellama', label: 'Code Llama' },
      { value: 'mistral', label: 'Mistral' }
    ]
  }
]

const emailProviders = [
  { name: '163 邮箱', host: 'smtp.163.com', port: 465 },
  { name: 'QQ 邮箱', host: 'smtp.qq.com', port: 465 },
  { name: 'Gmail', host: 'smtp.gmail.com', port: 587 },
  { name: 'Outlook', host: 'smtp.office365.com', port: 587 }
]

// ---- Computed ----
const isCustomProvider = computed(() => selectedProvider.value === '_custom')

const currentModels = computed(() => {
  const p = providers.find(p => p.name === selectedProvider.value)
  return p ? p.models : []
})

const form = ref({
  ai_api_key: '',
  ai_base_url: '',
  ai_model: '',
  ai_max_tokens: 4096,
  mail_enabled: false,
  mail_host: '',
  mail_port: 587,
  mail_username: '',
  mail_password: '',
  chat_max_history: 20,
  default_language: 'zh'
})

const aiConfigured = computed(() =>
  !!form.value.ai_api_key && !!form.value.ai_base_url && !!form.value.ai_model
)

const mailConfigured = computed(() =>
  !!form.value.mail_host && !!form.value.mail_username && !!form.value.mail_password
)

const saveStatusText = computed(() => {
  if (saving.value) return '正在保存...'
  if (saveStatus.value === 'saved') return '已自动保存'
  if (saveStatus.value === 'error') return '保存失败'
  return ''
})

const saveStatusClass = computed(() => {
  if (saving.value) return 'status-saving'
  if (saveStatus.value === 'saved') return 'status-saved'
  if (saveStatus.value === 'error') return 'status-error'
  return ''
})

// ---- Provider handling ----
function onProviderChange(providerName) {
  testResult.value = null
  if (providerName === '_custom') return
  const p = providers.find(p => p.name === providerName)
  if (p) {
    form.value.ai_base_url = p.baseUrl
    if (p.models.length > 0 && !p.models.some(m => m.value === form.value.ai_model)) {
      form.value.ai_model = p.models[0].value
    }
  }
}

function detectProvider() {
  const url = (form.value.ai_base_url || '').toLowerCase()
  const match = providers.find(p => {
    try { return url.includes(new URL(p.baseUrl).hostname) } catch { return false }
  })
  if (match) {
    selectedProvider.value = match.name
  } else if (form.value.ai_base_url) {
    selectedProvider.value = '_custom'
  }
}

// ---- Email quick-fill ----
function fillEmailProvider(ep) {
  form.value.mail_host = ep.host
  form.value.mail_port = ep.port
  testResult.value = null
}

// ---- Auto-save ----
let saveTimer = null

async function doSave() {
  saving.value = true
  saveStatus.value = 'saving'
  try {
    const entries = {}
    Object.entries(form.value).forEach(([key, val]) => {
      if (key === 'mail_enabled') {
        entries[key] = val ? 'true' : 'false'
      } else {
        entries[key] = String(val ?? '')
      }
    })
    await updateSettings(entries)
    saveStatus.value = 'saved'
  } catch {
    saveStatus.value = 'error'
  } finally {
    saving.value = false
  }
}

function scheduleSave() {
  if (!loadDone.value || loadingPreset.value) return
  testResult.value = null
  clearTimeout(saveTimer)
  saveTimer = setTimeout(doSave, 1200)
}

watch(form, scheduleSave, { deep: true })

// ---- Load settings ----
async function loadSettings() {
  loading.value = true
  loadDone.value = false
  try {
    const data = await getSettings()
    if (data) {
      Object.keys(form.value).forEach(key => {
        if (data[key] !== undefined && data[key] !== null) {
          if (key === 'mail_port' || key === 'ai_max_tokens' || key === 'chat_max_history') {
            form.value[key] = parseInt(data[key]) || form.value[key]
          } else if (key === 'mail_enabled') {
            form.value[key] = data[key] === 'true' || data[key] === true
          } else {
            form.value[key] = data[key]
          }
        }
      })
    }
    detectProvider()
  } catch {
    // First load may have no config
  } finally {
    loading.value = false
    loadDone.value = true
  }
}

// ---- Presets ----
async function loadPresets() {
  try {
    const [ai, mail] = await Promise.all([getAiPresets(), getMailPresets()])
    aiPresets.value = ai || []
    mailPresets.value = mail || []
  } catch { /* ignore */ }
}

function isAiPresetActive(p) {
  return form.value.ai_base_url === p.ai_base_url &&
    form.value.ai_model === p.ai_model &&
    form.value.ai_api_key === p.ai_api_key
}

function isMailPresetActive(p) {
  return form.value.mail_host === p.mail_host &&
    form.value.mail_username === p.mail_username
}

function getAiPresetName() {
  const p = providers.find(p => p.name === selectedProvider.value)
  if (p) return `${p.name} (${form.value.ai_model})`
  try {
    const host = new URL(form.value.ai_base_url).hostname
    return `${host} (${form.value.ai_model})`
  } catch {
    return `${form.value.ai_base_url} (${form.value.ai_model})`
  }
}

async function loadAiPreset(p) {
  clearTimeout(saveTimer)
  loadingPreset.value = true
  form.value.ai_api_key = p.ai_api_key || ''
  form.value.ai_base_url = p.ai_base_url || ''
  form.value.ai_model = p.ai_model || ''
  if (p.ai_max_tokens) form.value.ai_max_tokens = parseInt(p.ai_max_tokens) || 4096
  detectProvider()
  testResult.value = null
  await nextTick()
  loadingPreset.value = false
}

async function handleSaveAiPreset() {
  if (!aiConfigured.value) return
  const name = getAiPresetName()
  try {
    await saveAiPreset({
      name,
      ai_base_url: form.value.ai_base_url,
      ai_api_key: form.value.ai_api_key,
      ai_model: form.value.ai_model,
      ai_max_tokens: String(form.value.ai_max_tokens)
    })
    await loadPresets()
    ElMessage.success(`配置已保存: ${name}`)
  } catch {
    ElMessage.error('保存配置失败')
  }
}

async function handleDeleteAiPreset(name) {
  try {
    await ElMessageBox.confirm(`删除配置「${name}」？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteAiPreset(name)
    await loadPresets()
    ElMessage.success('已删除')
  } catch {}
}

function loadMailPreset(p) {
  clearTimeout(saveTimer)
  loadingPreset.value = true
  form.value.mail_host = p.mail_host || ''
  form.value.mail_port = parseInt(p.mail_port) || 587
  form.value.mail_username = p.mail_username || ''
  form.value.mail_password = p.mail_password || ''
  testResult.value = null
  nextTick(() => { loadingPreset.value = false })
}

async function handleSaveMailPreset() {
  if (!mailConfigured.value) return
  const name = form.value.mail_host || '邮箱配置'
  try {
    await saveMailPreset({
      name,
      mail_host: form.value.mail_host,
      mail_port: String(form.value.mail_port),
      mail_username: form.value.mail_username,
      mail_password: form.value.mail_password
    })
    await loadPresets()
    ElMessage.success(`邮箱配置已保存: ${name}`)
  } catch {
    ElMessage.error('保存配置失败')
  }
}

async function handleDeleteMailPreset(name) {
  try {
    await ElMessageBox.confirm(`删除配置「${name}」？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteMailPreset(name)
    await loadPresets()
    ElMessage.success('已删除')
  } catch {}
}

// ---- Test AI ----
async function handleTestAi() {
  testing.value = true
  testResult.value = null
  clearTimeout(saveTimer)
  await doSave()
  try {
    const res = await testAiConnection()
    testResult.value = { success: true, message: `连接成功！模型回复: ${res}` }
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '连接失败'
    testResult.value = { success: false, message: msg }
  } finally {
    testing.value = false
  }
}

// ---- Auth change ----
function onAuthChange(e) {
  if (!e.detail) {
    // 退出登录，重置表单
    form.value = {
      ai_api_key: '', ai_base_url: '', ai_model: '', ai_max_tokens: 4096,
      mail_enabled: false, mail_host: '', mail_port: 587,
      mail_username: '', mail_password: '',
      chat_max_history: 20, default_language: 'zh'
    }
    selectedProvider.value = ''
    aiPresets.value = []
    mailPresets.value = []
    testResult.value = null
    saveStatus.value = 'idle'
    loadDone.value = false
  } else {
    // 登录成功，重新加载
    loadSettings()
    loadPresets()
  }
}

// ---- Init ----
onMounted(async () => {
  await loadSettings()
  await loadPresets()
  window.addEventListener('auth-change', onAuthChange)
})

onUnmounted(() => {
  window.removeEventListener('auth-change', onAuthChange)
})
</script>

<style scoped>
.settings-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2328;
  margin: 0 0 4px 0;
}

.page-desc {
  font-size: 14px;
  color: #656d76;
  margin: 0;
}

.save-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  padding: 6px 14px;
  border-radius: 20px;
  transition: all 0.3s;
  flex-shrink: 0;
}

.status-saving { color: #3b82f6; background: #eff6ff; }
.status-saved { color: #2da44e; background: #f0fdf4; }
.status-error { color: #dc2626; background: #fef2f2; }

.status-spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg) }
  to { transform: rotate(360deg) }
}

/* 警告横幅 */
.alert-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 18px;
  border-radius: 10px;
  margin-bottom: 20px;
}

.alert-warning {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.alert-info {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e40af;
}

.alert-body { flex: 1; }
.alert-title { font-size: 14px; font-weight: 700; margin-bottom: 2px; }
.alert-desc { font-size: 13px; opacity: 0.85; }

.settings-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 设置卡片 */
.setting-card {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 14px;
  overflow: hidden;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.setting-card:hover {
  border-color: #2da44e40;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f2f5;
  background: #fafbfc;
}

.card-header-actions { margin-left: auto; }

.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-title { font-size: 16px; font-weight: 700; color: #1f2328; }
.card-desc { font-size: 12px; color: #8b949e; margin-top: 1px; }

.card-body { padding: 20px 24px; }

.form-row { margin-bottom: 16px; }
.form-row:last-child { margin-bottom: 0; }

.form-row.two-col {
  display: flex;
  gap: 16px;
}

.form-row.two-col .form-item {
  flex: 1;
  min-width: 0;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: #1f2328;
}

.form-hint {
  font-size: 12px;
  color: #8b949e;
  margin-top: 2px;
}

/* ---- Preset section ---- */
.preset-section {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 16px;
}

.preset-section:last-child {
  margin-bottom: 0;
}

.preset-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 10px;
}

.preset-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preset-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.preset-chip:hover {
  border-color: #2da44e;
  box-shadow: 0 2px 8px rgba(45,164,78,0.1);
}

.preset-chip.active {
  border-color: #2da44e;
  background: #f0fdf4;
}

.chip-main {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.chip-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2328;
}

.chip-sub {
  font-size: 11px;
  color: #8b949e;
}

.chip-delete {
  font-size: 14px;
  color: #ccc;
  cursor: pointer;
  padding: 2px;
  border-radius: 4px;
  transition: all 0.2s;
}

.chip-delete:hover {
  color: #dc2626;
  background: #fef2f2;
}

/* Quick fill buttons */
.quick-fill-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* Provider & Model select */
.provider-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.provider-label { font-weight: 500; }
.provider-hint { font-size: 12px; color: #8b949e; }

.model-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.model-name { font-weight: 500; }
.model-tags { display: flex; gap: 4px; }

/* Test result */
.test-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 13px;
}

.test-success {
  background: #f0fdf4;
  border: 1px solid #86efac;
  color: #166534;
}

.test-error {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

/* Help guide */
.help-guide {
  margin-top: 20px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px 18px;
}

.guide-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 12px;
}

.guide-tips {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.guide-tip-item {
  font-size: 12.5px;
  color: #64748b;
  line-height: 1.6;
  padding: 8px 12px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.guide-tip-item strong {
  color: #1f2328;
}

/* Responsive */
@media (max-width: 767px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .form-row.two-col {
    flex-direction: column;
    gap: 16px;
  }

  .card-header {
    padding: 16px 18px;
    flex-wrap: wrap;
  }

  .card-header-actions {
    width: 100%;
    margin-top: 8px;
  }

  .card-body { padding: 16px 18px; }

  .preset-chips {
    flex-direction: column;
  }
}
</style>
