<template>
  <el-dialog
    v-model="visible"
    title="个人设置"
    width="460px"
    class="profile-dialog"
  >
    <el-tabs v-model="activeTab">
      <!-- 基本信息 -->
      <el-tab-pane label="基本信息" name="profile">
        <el-form :model="form" label-position="top">
          <el-form-item label="用户名">
            <el-input :value="user?.username" disabled size="large" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" placeholder="你的显示名称" size="large" clearable />
          </el-form-item>
          <el-form-item label="GitHub 用户名">
            <el-input v-model="form.githubUsername" placeholder="关联 GitHub 账号，查看项目数据" size="large" clearable>
              <template #prefix>
                <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#656d76" d="M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z"/></svg>
              </template>
            </el-input>
            <div class="form-hint">设置后可在仪表盘查看你的 GitHub 仓库数据</div>
          </el-form-item>
        </el-form>

        <div class="tab-footer">
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="loading" @click="handleSave">保存</el-button>
        </div>
      </el-tab-pane>

      <!-- 邮箱绑定 -->
      <el-tab-pane label="邮箱绑定" name="email">
        <div v-if="user?.emailVerified" class="email-bound">
          <el-icon :size="40" color="#2da44e"><CircleCheck /></el-icon>
          <div class="email-bound-info">
            <div class="email-bound-title">邮箱已绑定</div>
            <div class="email-bound-addr">{{ user.email }}</div>
          </div>
          <el-button text type="primary" @click="resetEmail">更换邮箱</el-button>
        </div>
        <template v-else>
          <div class="email-hint-box">
            <el-icon :size="18" color="#3b82f6"><InfoFilled /></el-icon>
            <span>绑定邮箱后可订阅每日热点日报推送</span>
          </div>
          <el-form label-position="top">
            <el-form-item label="邮箱地址">
              <el-input v-model="emailForm.email" placeholder="your@email.com" size="large" :disabled="emailForm.codeSent" />
            </el-form-item>
            <el-form-item v-if="emailForm.codeSent" label="验证码">
              <div class="code-row">
                <el-input v-model="emailForm.code" placeholder="6 位验证码" size="large" maxlength="6" />
                <el-button size="large" :disabled="emailForm.countdown > 0" @click="handleSendCode">
                  {{ emailForm.countdown > 0 ? emailForm.countdown + 's' : '重新发送' }}
                </el-button>
              </div>
              <div class="form-hint">验证码已发送至 {{ emailForm.email }}，5 分钟内有效</div>
            </el-form-item>
          </el-form>
          <div class="tab-footer">
            <el-button @click="visible = false">取消</el-button>
            <el-button v-if="!emailForm.codeSent" type="primary" :loading="emailForm.sending" @click="handleSendCode">
              发送验证码
            </el-button>
            <el-button v-else type="primary" :loading="emailForm.verifying" @click="handleVerifyEmail">
              确认绑定
            </el-button>
          </div>
        </template>
      </el-tab-pane>

      <!-- 修改密码 -->
      <el-tab-pane label="修改密码" name="password">
        <el-form :model="pwdForm" label-position="top">
          <el-form-item label="当前密码">
            <el-input v-model="pwdForm.oldPassword" type="password" placeholder="请输入当前密码" size="large" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwdForm.newPassword" type="password" placeholder="至少 6 位" size="large" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="再次输入新密码" size="large" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
        </el-form>

        <div class="tab-footer">
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="pwdLoading" @click="handleChangePassword">修改密码</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, CircleCheck, InfoFilled } from '@element-plus/icons-vue'
import { updateProfile, changePassword, sendVerificationCode, verifyEmail } from '../api/auth'

const visible = defineModel({ type: Boolean })
const props = defineProps({ user: Object })
const emit = defineEmits(['success'])

const activeTab = ref('profile')
const loading = ref(false)
const pwdLoading = ref(false)

const form = reactive({
  nickname: '',
  githubUsername: ''
})

const emailForm = reactive({
  email: '',
  code: '',
  codeSent: false,
  sending: false,
  verifying: false,
  countdown: 0
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

let countdownTimer = null

watch(visible, (v) => {
  if (v && props.user) {
    form.nickname = props.user.nickname || ''
    form.githubUsername = props.user.githubUsername || ''
    activeTab.value = 'profile'
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    resetEmailForm()
  }
})

function resetEmailForm() {
  emailForm.email = ''
  emailForm.code = ''
  emailForm.codeSent = false
  emailForm.sending = false
  emailForm.verifying = false
  emailForm.countdown = 0
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function resetEmail() {
  resetEmailForm()
}

async function handleSave() {
  loading.value = true
  try {
    const data = await updateProfile({
      nickname: form.nickname || null,
      githubUsername: form.githubUsername || null
    })
    emit('success', data)
    visible.value = false
    ElMessage.success('保存成功')
  } catch (e) {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function handleSendCode() {
  if (!emailForm.email) {
    ElMessage.warning('请输入邮箱地址')
    return
  }
  emailForm.sending = true
  try {
    await sendVerificationCode(emailForm.email)
    emailForm.codeSent = true
    emailForm.countdown = 60
    countdownTimer = setInterval(() => {
      emailForm.countdown--
      if (emailForm.countdown <= 0) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }, 1000)
    ElMessage.success('验证码已发送')
  } catch (e) {
    // handled by interceptor
  } finally {
    emailForm.sending = false
  }
}

async function handleVerifyEmail() {
  if (!emailForm.code) {
    ElMessage.warning('请输入验证码')
    return
  }
  emailForm.verifying = true
  try {
    const data = await verifyEmail(emailForm.email, emailForm.code)
    emit('success', data)
    ElMessage.success('邮箱绑定成功')
  } catch (e) {
    // handled by interceptor
  } finally {
    emailForm.verifying = false
  }
}

async function handleChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写所有密码字段')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdLoading.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码修改成功')
    visible.value = false
  } catch (e) {
    // handled by interceptor
  } finally {
    pwdLoading.value = false
  }
}
</script>

<style scoped>
.form-hint {
  font-size: 12px;
  color: #8b949e;
  margin-top: 4px;
}

.tab-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.email-hint-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  font-size: 13px;
  color: #1e40af;
  margin-bottom: 16px;
}

.email-bound {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 24px 0;
}

.email-bound-info {
  flex: 1;
}

.email-bound-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2328;
}

.email-bound-addr {
  font-size: 13px;
  color: #656d76;
  margin-top: 2px;
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-row :deep(.el-input) {
  flex: 1;
}
</style>
