<template>
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
            @click="onLoadPreset(p)"
          >
            <div class="chip-main">
              <span class="chip-name">{{ p.name }}</span>
              <span class="chip-sub">{{ p.mail_username }}</span>
            </div>
            <el-icon class="chip-delete" @click.stop="onDeletePreset(p.name)"><Close /></el-icon>
          </div>
        </div>
      </div>

      <div class="form-row" style="margin-top: 16px">
        <div class="form-item">
          <div class="label-row">
            <label class="form-label">邮件发送</label>
            <el-button v-if="mailConfigured" link type="primary" size="small" @click="onSavePreset">
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
</template>

<script setup>
import { useSettingsForm } from '../../composables/useSettingsForm'
import {
  Message, QuestionFilled, Close, Clock, Promotion, FolderAdd
} from '@element-plus/icons-vue'

const {
  form, mailPresets, mailConfigured,
  loadMailPreset, handleSaveMailPreset, handleDeleteMailPreset
} = useSettingsForm()

const emailProviders = [
  { name: '163 邮箱', host: 'smtp.163.com', port: 465 },
  { name: 'QQ 邮箱', host: 'smtp.qq.com', port: 465 },
  { name: 'Gmail', host: 'smtp.gmail.com', port: 587 },
  { name: 'Outlook', host: 'smtp.office365.com', port: 587 }
]

function fillEmailProvider(ep) {
  form.value.mail_host = ep.host
  form.value.mail_port = ep.port
}

function isMailPresetActive(p) {
  return form.value.mail_host === p.mail_host &&
    form.value.mail_username === p.mail_username
}

function onLoadPreset(p) {
  loadMailPreset(p)
}

function onSavePreset() {
  if (!mailConfigured.value) return
  const name = form.value.mail_host || '邮箱配置'
  handleSaveMailPreset(name, {
    name,
    mail_host: form.value.mail_host,
    mail_port: String(form.value.mail_port),
    mail_username: form.value.mail_username,
    mail_password: form.value.mail_password
  })
}

function onDeletePreset(name) {
  handleDeleteMailPreset(name)
}
</script>

<style scoped>
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
.card-icon {
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.card-title { font-size: 16px; font-weight: 700; color: #1f2328; }
.card-desc { font-size: 12px; color: #8b949e; margin-top: 1px; }
.card-body { padding: 20px 24px; }
.form-row { margin-bottom: 16px; }
.form-row:last-child { margin-bottom: 0; }
.form-row.two-col { display: flex; gap: 16px; }
.form-row.two-col .form-item { flex: 1; min-width: 0; }
.form-item { display: flex; flex-direction: column; gap: 6px; }
.label-row { display: flex; justify-content: space-between; align-items: center; }
.form-label { font-size: 13px; font-weight: 600; color: #1f2328; }
.form-hint { font-size: 12px; color: #8b949e; margin-top: 2px; }
.preset-section {
  background: #f8fafc; border: 1px solid #e2e8f0;
  border-radius: 10px; padding: 12px 14px; margin-bottom: 16px;
}
.preset-section:last-child { margin-bottom: 0; }
.preset-label {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; font-weight: 600; color: #64748b; margin-bottom: 10px;
}
.quick-fill-btns { display: flex; flex-wrap: wrap; gap: 8px; }
.preset-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.preset-chip {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; background: #fff; border: 1px solid #e2e8f0;
  border-radius: 8px; cursor: pointer; transition: all 0.2s;
}
.preset-chip:hover { border-color: #2da44e; box-shadow: 0 2px 8px rgba(45,164,78,0.1); }
.preset-chip.active { border-color: #2da44e; background: #f0fdf4; }
.chip-main { display: flex; flex-direction: column; gap: 1px; }
.chip-name { font-size: 13px; font-weight: 600; color: #1f2328; }
.chip-sub { font-size: 11px; color: #8b949e; }
.chip-delete {
  font-size: 14px; color: #ccc; cursor: pointer; padding: 2px;
  border-radius: 4px; transition: all 0.2s;
}
.chip-delete:hover { color: #dc2626; background: #fef2f2; }
.help-guide {
  margin-top: 20px; background: #f8fafc; border: 1px solid #e2e8f0;
  border-radius: 10px; padding: 16px 18px;
}
.guide-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 700; color: #475569; margin-bottom: 12px;
}
.guide-tips { display: flex; flex-direction: column; gap: 8px; }
.guide-tip-item {
  font-size: 12.5px; color: #64748b; line-height: 1.6;
  padding: 8px 12px; background: #fff; border-radius: 6px; border: 1px solid #e2e8f0;
}
.guide-tip-item strong { color: #1f2328; }

@media (max-width: 767px) {
  .form-row.two-col { flex-direction: column; gap: 16px; }
  .card-header { padding: 16px 18px; flex-wrap: wrap; }
  .card-body { padding: 16px 18px; }
  .preset-chips { flex-direction: column; }
}
</style>
