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
      <SettingsAI ref="settingsAiRef" />
      <SettingsMail />
      <SettingsGitHub />
      <SettingsChat />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useSettingsForm } from '../composables/useSettingsForm'
import SettingsAI from '../components/settings/SettingsAI.vue'
import SettingsMail from '../components/settings/SettingsMail.vue'
import SettingsChat from '../components/settings/SettingsChat.vue'
import SettingsGitHub from '../components/settings/SettingsGitHub.vue'
import {
  WarningFilled, InfoFilled,
  CircleCheckFilled, CircleCloseFilled, Loading
} from '@element-plus/icons-vue'

const {
  loading, saving, saveStatus,
  form, aiConfigured, mailConfigured,
  saveStatusText, saveStatusClass,
  loadSettings, loadPresets, resetForm
} = useSettingsForm()

const settingsAiRef = ref(null)

function onAuthChange(e) {
  if (!e.detail) {
    resetForm()
  } else {
    loadSettings().then(() => {
      settingsAiRef.value?.detectProvider()
    })
    loadPresets()
  }
}

onMounted(async () => {
  await loadSettings()
  await loadPresets()
  settingsAiRef.value?.detectProvider()
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

@media (max-width: 767px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
