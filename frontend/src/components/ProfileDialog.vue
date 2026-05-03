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
                <GitHubIcon :size="14" />
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
import GitHubIcon from './icons/GitHubIcon.vue'

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
