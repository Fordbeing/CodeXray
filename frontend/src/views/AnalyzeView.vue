<template>
  <div class="analyze-page">
    <h1 class="page-title">仓库分析</h1>

    <!-- URL 输入区 -->
    <el-card class="input-card" shadow="never">
      <div class="input-row">
        <el-input
          v-model="repoUrl"
          placeholder="输入 GitHub 仓库地址，如 https://github.com/vuejs/vue"
          size="large"
          clearable
          @keyup.enter="handlePreview"
        >
          <template #prefix>
            <el-icon><Link /></el-icon>
          </template>
        </el-input>
        <el-button size="large" @click="handlePreview" :loading="previewing">
          预览
        </el-button>
        <el-button type="primary" size="large" @click="handleAnalyze" :loading="analyzing" :disabled="!repoUrl">
          开始分析
        </el-button>
      </div>
    </el-card>

    <!-- 预览结果 -->
    <el-card v-if="preview" class="section-card" shadow="never">
      <template #header>
        <span class="section-title">仓库预览</span>
      </template>
      <div class="preview-stats">
        <div class="stat-item">
          <span class="stat-label">主要语言</span>
          <el-tag type="success">{{ preview.primaryLanguage }}</el-tag>
        </div>
        <div class="stat-item">
          <span class="stat-label">总文件数</span>
          <span class="stat-value">{{ preview.totalFiles }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">源代码文件</span>
          <span class="stat-value">{{ preview.totalSourceFiles }}</span>
        </div>
      </div>
      <div v-if="preview.languageStats" class="lang-stats">
        <div v-for="(count, lang) in preview.languageStats" :key="lang" class="lang-bar">
          <span class="lang-name">{{ lang }}</span>
          <el-progress
            :percentage="Math.round(count / preview.totalSourceFiles * 100)"
            :stroke-width="8"
            :show-text="true"
            :format="() => `${count} files`"
          />
        </div>
      </div>
    </el-card>

    <!-- 分析进度 -->
    <el-card v-if="taskId && (taskStatus === 'CLONING' || taskStatus === 'ANALYZING' || taskStatus === 'PENDING')" class="section-card" shadow="never">
      <template #header>
        <span class="section-title">分析进度</span>
      </template>
      <el-steps :active="stepActive" finish-status="success" align-center>
        <el-step title="提交任务" />
        <el-step title="克隆仓库" />
        <el-step title="AI 分析" />
        <el-step title="完成" />
      </el-steps>
      <div class="progress-hint">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在{{ taskStatus === 'CLONING' ? '克隆仓库' : '进行 AI 分析' }}，请稍候...</span>
      </div>
    </el-card>

    <!-- 分析报告 -->
    <div v-if="report" class="report-section">
      <el-card class="section-card" shadow="never">
        <template #header>
          <span class="section-title">分析报告</span>
        </template>

        <!-- 综合评分 -->
        <div class="score-section">
          <el-progress
            type="circle"
            :percentage="report.score || 0"
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
        <div v-if="report.scoreDetails" class="score-details">
          <div v-for="(val, key) in report.scoreDetails" :key="key" class="score-detail-item">
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
          <el-descriptions-item label="项目概述">{{ report.summary }}</el-descriptions-item>
          <el-descriptions-item label="主要语言">{{ report.primaryLanguage }}</el-descriptions-item>
          <el-descriptions-item label="技术栈">
            <el-tag v-for="t in report.techStack" :key="t" class="tech-tag" type="success" size="small">{{ t }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="report.architecture" label="架构模式">{{ report.architecture }}</el-descriptions-item>
        </el-descriptions>

        <!-- 模块 -->
        <div v-if="report.modules?.length" class="modules-section">
          <h3>模块结构</h3>
          <el-table :data="report.modules" stripe>
            <el-table-column prop="name" label="模块" width="200" />
            <el-table-column prop="description" label="职责" />
          </el-table>
        </div>

        <!-- 优点 -->
        <div v-if="report.strengths?.length" class="list-section">
          <h3>优点</h3>
          <ul class="check-list">
            <li v-for="(s, i) in report.strengths" :key="i">{{ s }}</li>
          </ul>
        </div>

        <!-- 改进建议 -->
        <div v-if="report.improvements?.length" class="list-section">
          <h3>改进建议</h3>
          <ul class="warn-list">
            <li v-for="(s, i) in report.improvements" :key="i">{{ s }}</li>
          </ul>
        </div>

        <!-- 总结 -->
        <el-alert v-if="report.verdict" :title="report.verdict" type="success" :closable="false" show-icon />
      </el-card>
    </div>

    <!-- 失败 -->
    <el-card v-if="taskStatus === 'FAILED'" class="section-card" shadow="never">
      <el-alert :title="errorMessage || '分析失败'" type="error" :closable="false" show-icon />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { analyzeRepo, getAnalysisResult, previewRepo } from '../api/analysis'

const route = useRoute()
const router = useRouter()

const repoUrl = ref('')
const previewing = ref(false)
const analyzing = ref(false)
const preview = ref(null)
const taskId = ref('')
const taskStatus = ref('')
const errorMessage = ref('')
const report = ref(null)
let pollTimer = null

const scoreLabels = {
  codeQuality: '代码质量',
  structure: '项目结构',
  documentation: '文档',
  testing: '测试',
  dependencies: '依赖管理'
}

const stepActive = computed(() => {
  switch (taskStatus.value) {
    case 'PENDING': return 1
    case 'CLONING': return 2
    case 'ANALYZING': return 3
    case 'COMPLETED': return 4
    default: return 0
  }
})

async function handlePreview() {
  if (!repoUrl.value) return
  previewing.value = true
  try {
    preview.value = await previewRepo(repoUrl.value)
  } finally {
    previewing.value = false
  }
}

async function handleAnalyze() {
  if (!repoUrl.value) return
  analyzing.value = true
  report.value = null
  try {
    taskId.value = await analyzeRepo(repoUrl.value)
    taskStatus.value = 'PENDING'
    router.replace({ path: `/analyze/${taskId.value}` })
    startPolling()
  } finally {
    analyzing.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const result = await getAnalysisResult(taskId.value)
      taskStatus.value = result.status
      if (result.status === 'COMPLETED') {
        stopPolling()
        if (result.report) {
          try {
            report.value = JSON.parse(result.report)
          } catch {
            report.value = { summary: result.report, score: 0 }
          }
        }
      } else if (result.status === 'FAILED') {
        stopPolling()
        errorMessage.value = result.errorMessage
      }
    } catch {
      stopPolling()
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(async () => {
  // 从仪表盘快速分析跳转过来
  if (route.query.url) {
    repoUrl.value = route.query.url
    router.replace({ path: route.path })
  }

  const pathTaskId = route.params.taskId
  if (pathTaskId) {
    taskId.value = pathTaskId
    try {
      const result = await getAnalysisResult(pathTaskId)
      taskStatus.value = result.status
      repoUrl.value = result.repoUrl
      if (result.status === 'COMPLETED' && result.report) {
        try {
          report.value = JSON.parse(result.report)
        } catch {
          report.value = { summary: result.report, score: 0 }
        }
      } else if (result.status === 'FAILED') {
        errorMessage.value = result.errorMessage
      } else {
        startPolling()
      }
    } catch {
      // task not found
    }
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 20px 0;
}

.input-card {
  margin-bottom: 20px;
}

.input-row {
  display: flex;
  gap: 12px;
}

.input-row .el-input {
  flex: 1;
}

.section-card {
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2328;
}

.preview-stats {
  display: flex;
  gap: 32px;
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 13px;
  color: #656d76;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #1f2328;
}

.lang-stats {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.lang-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.lang-name {
  width: 100px;
  font-size: 13px;
  color: #656d76;
  text-align: right;
}

.lang-bar .el-progress {
  flex: 1;
}

.progress-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
  color: #656d76;
  font-size: 14px;
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

.modules-section,
.list-section {
  margin-bottom: 24px;
}

.modules-section h3,
.list-section h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 12px 0;
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
</style>
