<template>
  <div class="shared-page">
    <!-- 密码输入 -->
    <div v-if="needPassword" class="password-section">
      <el-card class="password-card" shadow="never">
        <div class="password-inner">
          <el-icon :size="48" color="#d29922"><Lock /></el-icon>
          <h2>此报告受密码保护</h2>
          <p>请输入访问密码</p>
          <el-input
            v-model="password"
            type="password"
            placeholder="输入密码"
            show-password
            style="max-width: 300px; margin-bottom: 16px"
            @keyup.enter="loadReport"
          />
          <el-button type="primary" :loading="loading" @click="loadReport">
            验证并查看
          </el-button>
          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon style="margin-top: 16px; max-width: 300px" />
        </div>
      </el-card>
    </div>

    <!-- 加载中 -->
    <div v-else-if="loading" class="loading-section">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <span>加载报告中...</span>
    </div>

    <!-- 报告内容 -->
    <div v-else-if="report" class="report-content">
      <div class="share-banner">
        <span v-if="report.sharedBy">
          由 <strong>{{ report.sharedBy || '匿名用户' }}</strong> 分享的分析报告
        </span>
      </div>

      <h1 class="report-title">分析报告</h1>
      <p class="report-repo">{{ report.repoUrl }}</p>

      <!-- 综合评分 -->
      <el-card class="report-card" shadow="never">
        <div class="score-section">
          <el-progress
            type="circle"
            :percentage="parsedReport.score || 0"
            :width="120"
            :stroke-width="8"
            color="#2da44e"
          >
            <template #default="{ percentage }">
              <span class="score-number">{{ percentage }}</span>
              <span class="score-label">综合评分</span>
            </template>
          </el-progress>
        </div>

        <!-- 分项评分 -->
        <div v-if="parsedReport.scoreDetails" class="score-details">
          <div v-for="(val, key) in parsedReport.scoreDetails" :key="key" class="score-detail-item">
            <el-progress
              type="dashboard"
              :percentage="val"
              :width="80"
              :stroke-width="6"
              :color="val >= 80 ? '#2da44e' : val >= 60 ? '#d29922' : '#cf222e'"
            >
              <template #default>
                <span class="detail-score">{{ val }}</span>
              </template>
            </el-progress>
            <span class="detail-label">{{ scoreLabels[key] || key }}</span>
          </div>
        </div>

        <!-- 概述 -->
        <el-descriptions :column="1" border class="report-desc">
          <el-descriptions-item label="项目概述">{{ parsedReport.summary }}</el-descriptions-item>
          <el-descriptions-item label="主要语言">{{ parsedReport.primaryLanguage }}</el-descriptions-item>
          <el-descriptions-item label="技术栈">
            <el-tag v-for="t in parsedReport.techStack" :key="t" class="tech-tag" type="success" size="small">{{ t }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 优点 -->
        <div v-if="parsedReport.strengths?.length" class="list-section">
          <h3>优点</h3>
          <ul class="check-list">
            <li v-for="(s, i) in parsedReport.strengths" :key="i">{{ s }}</li>
          </ul>
        </div>

        <!-- 改进建议 -->
        <div v-if="parsedReport.improvements?.length" class="list-section">
          <h3>改进建议</h3>
          <ul class="warn-list">
            <li v-for="(s, i) in parsedReport.improvements" :key="i">{{ s }}</li>
          </ul>
        </div>

        <!-- 总结 -->
        <el-alert v-if="parsedReport.verdict" :title="parsedReport.verdict" type="success" :closable="false" show-icon />
      </el-card>

      <div class="powered-by">
        Powered by <strong>CodeXray</strong>
      </div>
    </div>

    <!-- 错误 -->
    <div v-else-if="error" class="error-section">
      <el-result icon="error" :title="error" sub-title="该分享链接可能已过期或被撤销">
        <template #extra>
          <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Lock, Loading } from '@element-plus/icons-vue'
import { getSharedReport } from '../api/analysis'

const route = useRoute()
const loading = ref(true)
const error = ref('')
const report = ref(null)
const needPassword = ref(false)
const password = ref('')

const scoreLabels = {
  codeQuality: '代码质量',
  structure: '项目结构',
  documentation: '文档',
  testing: '测试',
  dependencies: '依赖管理',
  security: '安全性',
  performance: '性能',
  maintainability: '可维护性'
}

const parsedReport = computed(() => {
  if (!report.value?.report) return {}
  try {
    return typeof report.value.report === 'string' ? JSON.parse(report.value.report) : report.value.report
  } catch {
    return { summary: report.value.report, score: 0 }
  }
})

async function loadReport() {
  loading.value = true
  error.value = ''
  try {
    const shareToken = route.params.shareToken
    const data = await getSharedReport(shareToken, password.value || undefined)
    if (data.passwordRequired) {
      needPassword.value = true
      if (password.value) {
        error.value = '密码错误，请重试'
        password.value = ''
      }
    } else {
      report.value = data
      needPassword.value = false
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '加载失败'
    if (msg.includes('密码')) {
      needPassword.value = true
      if (password.value) {
        error.value = msg
        password.value = ''
      }
    } else {
      error.value = msg
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadReport()
})
</script>

<style scoped>
.shared-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
  min-height: 100vh;
}

.password-section,
.loading-section {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.password-card {
  max-width: 400px;
  width: 100%;
}

.password-inner {
  text-align: center;
  padding: 20px;
}

.password-inner h2 {
  margin: 16px 0 8px;
  font-size: 18px;
  color: #1f2328;
}

.password-inner p {
  margin: 0 0 16px;
  color: #656d76;
  font-size: 14px;
}

.loading-section {
  flex-direction: column;
  gap: 12px;
  color: #656d76;
}

.share-banner {
  background: linear-gradient(135deg, #f0fdf4 0%, #eff6ff 100%);
  padding: 12px 20px;
  border-radius: 10px;
  margin-bottom: 20px;
  font-size: 14px;
  color: #656d76;
}

.report-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 8px;
}

.report-repo {
  color: #656d76;
  font-size: 14px;
  margin: 0 0 20px;
}

.report-card {
  margin-bottom: 20px;
}

.score-section {
  text-align: center;
  margin-bottom: 32px;
}

.score-number {
  font-size: 28px;
  font-weight: 700;
  color: #2da44e;
  display: block;
}

.score-label {
  font-size: 12px;
  color: #656d76;
  display: block;
}

.score-details {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 32px;
  flex-wrap: wrap;
}

.score-detail-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.detail-score {
  font-size: 16px;
  font-weight: 600;
  color: #1f2328;
}

.detail-label {
  font-size: 12px;
  color: #656d76;
}

.report-desc {
  margin-bottom: 24px;
}

.tech-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.list-section {
  margin-bottom: 24px;
}

.list-section h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 12px;
}

.check-list,
.warn-list {
  margin: 0;
  padding-left: 20px;
}

.check-list li {
  color: #2da44e;
  margin-bottom: 6px;
}

.warn-list li {
  color: #d29922;
  margin-bottom: 6px;
}

.powered-by {
  text-align: center;
  color: #8b949e;
  font-size: 12px;
  padding: 20px 0;
}

.error-section {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
</style>
