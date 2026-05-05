<template>
  <div class="setting-card">
    <div class="card-header">
      <div class="card-icon" style="background: linear-gradient(135deg, #f6f8fa, #d0d7de)">
        <svg viewBox="0 0 16 16" width="20" height="20"><path fill="#1f2328" d="M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z"/></svg>
      </div>
      <div>
        <div class="card-title">GitHub 集成</div>
        <div class="card-desc">配置 Token 提升 API 配额，绑定账号查看项目数据</div>
      </div>
    </div>

    <div class="card-body">
      <!-- GitHub Username 绑定 -->
      <div class="section">
        <div class="section-title">账号绑定</div>
        <div class="form-row">
          <div class="form-item" style="flex:1">
            <label class="form-label">GitHub 用户名</label>
            <el-input
              v-model="githubUsername"
              placeholder="输入你的 GitHub 用户名"
              size="large"
              clearable
              @blur="onUsernameBlur"
            >
              <template #prefix>
                <svg viewBox="0 0 16 16" width="14" height="14"><path fill="#8b949e" d="M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z"/></svg>
              </template>
            </el-input>
            <div class="form-hint">
              <template v-if="bindStatus === 'saved'">
                <el-icon :size="12" color="#2da44e"><CircleCheck /></el-icon>
                已绑定，可在 GitHub 页面查看你的项目数据
              </template>
              <template v-else-if="bindStatus === 'saving'">
                <el-icon :size="12" color="#d29922" class="status-spinning"><Loading /></el-icon>
                正在保存...
              </template>
              <template v-else>绑定后可自动加载你的 GitHub 项目数据</template>
            </div>
          </div>

          <div class="form-item" style="flex-shrink:0">
            <label class="form-label">&nbsp;</label>
            <el-button size="large" @click="goToGithubPage" :disabled="!githubUsername.trim()">
              查看 GitHub 页面
              <el-icon :size="14" style="margin-left:4px"><TopRight /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <el-divider />

      <!-- API Token -->
      <div class="section">
        <div class="section-title">API 访问令牌</div>
        <div class="form-item">
          <label class="form-label">Personal Access Token</label>
          <el-input
            v-model="form.github_token"
            type="password"
            show-password
            size="large"
            placeholder="ghp_xxxxxxxxxxxxxxxxxxxx"
          />
          <div class="token-info">
            <div class="token-info-row">
              <el-icon :size="14" color="#656d76"><InfoFilled /></el-icon>
              <span>不配置 Token：<strong>60 次/小时</strong> · 配置后：<strong>5,000 次/小时</strong></span>
            </div>
            <div class="token-info-row">
              <a href="https://github.com/settings/tokens" target="_blank" class="token-link">
                创建 Token <el-icon :size="12"><TopRight /></el-icon>
              </a>
              <span class="token-hint">— 选择 <strong>Fine-grained token</strong>，无需勾选任何权限，只需给予读取公开数据的权限即可</span>
            </div>
            <div v-if="form.github_token" class="token-status">
              <template v-if="testing">
                <el-icon :size="14" class="status-spinning" color="#d29922"><Loading /></el-icon>
                <span>正在测试连接...</span>
              </template>
              <template v-else-if="testResult === 'ok'">
                <el-icon :size="14" color="#2da44e"><CircleCheck /></el-icon>
                <span style="color:#2da44e">连接正常 · {{ testDetail }}</span>
              </template>
              <template v-else-if="testResult === 'fail'">
                <el-icon :size="14" color="#dc2626"><CircleClose /></el-icon>
                <span style="color:#dc2626">{{ testDetail }}</span>
              </template>
              <el-button v-if="!testing" text size="small" type="primary" @click="testToken">测试连接</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, Loading, InfoFilled, CircleClose, TopRight } from '@element-plus/icons-vue'
import { useSettingsForm } from '../../composables/useSettingsForm'
import { updateProfile } from '../../api/auth'
import { getUserProfile } from '../../api/github'

const { form } = useSettingsForm()
const router = useRouter()

const githubUsername = ref('')
const bindStatus = ref('idle') // idle | saving | saved
const testing = ref(false)
const testResult = ref(null) // null | 'ok' | 'fail'
const testDetail = ref('')

// Load from localStorage
function loadGithubUsername() {
  const saved = localStorage.getItem('codexray_user')
  if (saved) {
    try {
      const u = JSON.parse(saved)
      githubUsername.value = u.githubUsername || ''
      if (u.githubUsername) bindStatus.value = 'saved'
    } catch { /* ignore */ }
  }
  // Also check localStorage fallback
  if (!githubUsername.value) {
    githubUsername.value = localStorage.getItem('codexray_gh_user') || ''
  }
}

// Save to user profile
let saveTimer = null
function onUsernameBlur() {
  clearTimeout(saveTimer)
  const name = githubUsername.value.trim()
  if (!name) return
  bindStatus.value = 'saving'
  saveTimer = setTimeout(async () => {
    try {
      await updateProfile({ githubUsername: name })
      // Update localStorage
      const saved = localStorage.getItem('codexray_user')
      if (saved) {
        const u = JSON.parse(saved)
        u.githubUsername = name
        localStorage.setItem('codexray_user', JSON.stringify(u))
      }
      localStorage.setItem('codexray_gh_user', name)
      bindStatus.value = 'saved'
    } catch (e) {
      ElMessage.error('绑定失败: ' + (e.message || '未知错误'))
      bindStatus.value = 'idle'
    }
  }, 600)
}

function goToGithubPage() {
  const name = githubUsername.value.trim()
  if (name) {
    localStorage.setItem('codexray_gh_user', name)
    router.push('/github')
  }
}

async function testToken() {
  if (!form.value.github_token) return
  testing.value = true
  testResult.value = null
  try {
    // Use a lightweight API call to verify the token
    const resp = await fetch('https://api.github.com/rate_limit', {
      headers: { Authorization: 'Bearer ' + form.value.github_token }
    })
    if (resp.ok) {
      const data = await resp.json()
      const core = data.resources?.core
      testResult.value = 'ok'
      testDetail.value = core
        ? `剩余 ${core.remaining}/${core.limit} 次，${new Date(core.reset * 1000).toLocaleTimeString('zh-CN')} 重置`
        : 'Token 有效'
    } else if (resp.status === 401) {
      testResult.value = 'fail'
      testDetail.value = 'Token 无效，请检查是否正确'
    } else {
      testResult.value = 'fail'
      testDetail.value = '请求失败 (' + resp.status + ')'
    }
  } catch (e) {
    testResult.value = 'fail'
    testDetail.value = '网络错误，无法连接 GitHub API'
  } finally {
    testing.value = false
  }
}

// Watch for auto-save
watch(() => form.value.github_token, () => {
  if (testResult.value) testResult.value = null
})

loadGithubUsername()
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

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f2328;
  margin-bottom: 14px;
}

.form-row { display: flex; gap: 16px; align-items: flex-end; margin-bottom: 16px; }
.form-row:last-child { margin-bottom: 0; }
.form-item { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 13px; font-weight: 600; color: #1f2328; }
.form-hint {
  font-size: 12px; color: #8b949e; display: flex; align-items: center; gap: 4px;
}

.token-info {
  margin-top: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.token-info-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #656d76;
}
.token-info-row strong { color: #1f2328; }
.token-link {
  color: #2da44e;
  text-decoration: none;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.token-link:hover { text-decoration: underline; }
.token-hint { font-size: 12px; color: #8b949e; }
.token-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #656d76;
  padding-top: 6px;
  border-top: 1px solid #e2e8f0;
}

.status-spinning { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg) } to { transform: rotate(360deg) } }

@media (max-width: 767px) {
  .card-header { padding: 16px 18px; }
  .card-body { padding: 16px 18px; }
  .form-row { flex-direction: column; gap: 12px; }
}
</style>
