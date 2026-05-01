<template>
  <el-dialog
    v-model="visible"
    :title="isLogin ? '登录' : '注册'"
    width="400px"
    :close-on-click-modal="false"
    class="auth-dialog"
  >
    <el-form :model="form" label-position="top" @keyup.enter="handleSubmit">
      <el-form-item label="用户名">
        <el-input v-model="form.username" placeholder="请输入用户名" size="large" clearable>
          <template #prefix><el-icon><User /></el-icon></template>
        </el-input>
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password>
          <template #prefix><el-icon><Lock /></el-icon></template>
        </el-input>
      </el-form-item>
      <template v-if="!isLogin">
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="选填，默认使用用户名" size="large" clearable />
        </el-form-item>
        <el-form-item label="GitHub 用户名">
          <el-input v-model="form.githubUsername" placeholder="选填，用于关联 GitHub 项目" size="large" clearable>
            <template #prefix><el-icon><Link /></el-icon></template>
          </el-input>
        </el-form-item>
      </template>
    </el-form>

    <div class="auth-switch">
      <span v-if="isLogin">还没有账号？</span>
      <span v-else>已有账号？</span>
      <el-button link type="primary" @click="isLogin = !isLogin">
        {{ isLogin ? '立即注册' : '去登录' }}
      </el-button>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ isLogin ? '登录' : '注册' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { login, register } from '../api/auth'

const visible = defineModel({ type: Boolean })
const emit = defineEmits(['success'])

const isLogin = ref(true)
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  githubUsername: ''
})

watch(visible, (v) => {
  if (v) {
    form.username = ''
    form.password = ''
    form.nickname = ''
    form.githubUsername = ''
    isLogin.value = true
  }
})

async function handleSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  if (!isLogin.value && form.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }

  loading.value = true
  try {
    let data
    if (isLogin.value) {
      data = await login(form.username, form.password)
    } else {
      data = await register(form.username, form.password, form.nickname, form.githubUsername)
    }
    localStorage.setItem('codexray_token', data.token)
    localStorage.setItem('codexray_user', JSON.stringify(data))
    emit('success', data)
    visible.value = false
    ElMessage.success(isLogin.value ? '登录成功' : '注册成功')
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-switch {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  color: #656d76;
}
</style>
