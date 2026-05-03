import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSettings, updateSettings,
  getAiPresets, saveAiPreset, deleteAiPreset,
  getMailPresets, saveMailPreset, deleteMailPreset
} from '../api/settings'

const loading = ref(false)
const saving = ref(false)
const saveStatus = ref('idle')
const loadDone = ref(false)
const loadingPreset = ref(false)

const aiPresets = ref([])
const mailPresets = ref([])

const form = ref({
  ai_api_key: '',
  ai_base_url: '',
  ai_model: '',
  ai_embedding_model: '',
  ai_embedding_url: '',
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

async function handleSaveAiPreset(name, presetData) {
  try {
    await saveAiPreset(presetData)
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

async function handleSaveMailPreset(name, presetData) {
  try {
    await saveMailPreset(presetData)
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

function loadAiPreset(p) {
  clearTimeout(saveTimer)
  loadingPreset.value = true
  form.value.ai_api_key = p.ai_api_key || ''
  form.value.ai_base_url = p.ai_base_url || ''
  form.value.ai_model = p.ai_model || ''
  if (p.ai_max_tokens) form.value.ai_max_tokens = parseInt(p.ai_max_tokens) || 4096
  loadingPreset.value = false
  doSave()
}

function loadMailPreset(p) {
  clearTimeout(saveTimer)
  loadingPreset.value = true
  form.value.mail_host = p.mail_host || ''
  form.value.mail_port = parseInt(p.mail_port) || 587
  form.value.mail_username = p.mail_username || ''
  form.value.mail_password = p.mail_password || ''
  loadingPreset.value = false
  doSave()
}

function resetForm() {
  form.value = {
    ai_api_key: '', ai_base_url: '', ai_model: '', ai_embedding_model: '', ai_embedding_url: '', ai_max_tokens: 4096,
    mail_enabled: false, mail_host: '', mail_port: 587,
    mail_username: '', mail_password: '',
    chat_max_history: 20, default_language: 'zh'
  }
  aiPresets.value = []
  mailPresets.value = []
  saveStatus.value = 'idle'
  loadDone.value = false
}

export function useSettingsForm() {
  return {
    loading,
    saving,
    saveStatus,
    loadDone,
    form,
    aiPresets,
    mailPresets,
    aiConfigured,
    mailConfigured,
    saveStatusText,
    saveStatusClass,
    doSave,
    loadSettings,
    loadPresets,
    loadAiPreset,
    loadMailPreset,
    handleSaveAiPreset,
    handleDeleteAiPreset,
    handleSaveMailPreset,
    handleDeleteMailPreset,
    resetForm,
  }
}
