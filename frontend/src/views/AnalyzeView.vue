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
      <div class="upload-row">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".zip,.tar.gz,.tgz"
          :on-change="handleFileChange"
          :on-exceed="() => ElMessage.warning('请先移除已选文件')"
          drag
          class="upload-area"
        >
          <div class="upload-inner">
            <el-icon :size="28" color="#8b949e"><Upload /></el-icon>
            <div class="upload-text">拖拽代码压缩包到此处，或 <em>点击上传</em></div>
            <div class="upload-hint">支持 .zip / .tar.gz 格式</div>
          </div>
        </el-upload>
        <el-button type="success" size="large" :loading="uploading" :disabled="!uploadFile" @click="handleUpload">
          上传并分析
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
    <el-card v-if="taskId && !['COMPLETED', 'FAILED'].includes(taskStatus)" class="section-card" shadow="never">
      <template #header>
        <span class="section-title">分析进度</span>
      </template>
      <el-steps :active="stepActive" finish-status="success" align-center>
        <el-step title="提交任务" />
        <el-step title="检查配置" />
        <el-step title="克隆仓库" />
        <el-step title="AI 分析" />
        <el-step title="完成" />
      </el-steps>
      <div class="progress-hint">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ progressText }}</span>
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

        <!-- 推荐问题 -->
        <div v-if="suggestedQuestions.length" class="questions-section">
          <h3>推荐问题</h3>
          <div class="question-tags">
            <el-tag
              v-for="(q, i) in suggestedQuestions"
              :key="i"
              class="question-tag"
              effect="plain"
              type="success"
              @click="askQuestion(q)"
              style="cursor: pointer"
            >
              {{ q }}
            </el-tag>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="action-bar">
          <el-button @click="copyCloneUrl">
            <el-icon style="margin-right: 4px"><CopyDocument /></el-icon>
            复制 Clone 命令
          </el-button>
          <el-button @click="openGitHub">
            <el-icon style="margin-right: 4px"><Link /></el-icon>
            打开 GitHub
          </el-button>
          <el-button @click="exportReport">
            <el-icon style="margin-right: 4px"><Download /></el-icon>
            导出报告
          </el-button>
        </div>

        <!-- 跳转问答 -->
        <div class="chat-jump">
          <el-button @click="goToExplorer" size="large">
            <el-icon style="margin-right: 4px"><FolderOpened /></el-icon>
            浏览代码
          </el-button>
          <el-button type="primary" size="large" @click="goToChat">
            <el-icon style="margin-right: 4px"><ChatDotRound /></el-icon>
            基于此分析进行代码问答
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 失败 -->
    <el-card v-if="taskStatus === 'FAILED'" class="section-card" shadow="never">
      <div class="error-section">
        <el-alert :title="errorMessage || '分析失败'" type="error" :closable="false" show-icon />
        <div class="error-actions">
          <el-button type="primary" @click="$router.push('/settings')">
            <el-icon style="margin-right: 4px"><Setting /></el-icon>
            前往系统设置
          </el-button>
          <el-button @click="handleRetry">
            <el-icon style="margin-right: 4px"><RefreshRight /></el-icon>
            重新分析
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Upload, CopyDocument, Setting, RefreshRight, ChatDotRound, Download, FolderOpened } from '@element-plus/icons-vue'
import { analyzeRepo, uploadAndAnalyze, getAnalysisResult, previewRepo, getQuestions } from '../api/analysis'

const route = useRoute()
const router = useRouter()

const repoUrl = ref('')
const previewing = ref(false)
const analyzing = ref(false)
const uploading = ref(false)
const uploadFile = ref(null)
const uploadRef = ref(null)
const preview = ref(null)
const taskId = ref('')
const taskStatus = ref('')
const errorMessage = ref('')
const report = ref(null)
const suggestedQuestions = ref([])
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
    case 'CHECKING': return 2
    case 'CLONING': return 3
    case 'SCANNING': return 3
    case 'ANALYZING': return 4
    case 'REPORTING': return 4
    case 'COMPLETED': return 5
    default: return 0
  }
})

const progressText = computed(() => {
  switch (taskStatus.value) {
    case 'CHECKING': return '正在检查 AI 模型配置...'
    case 'CLONING': return '正在克隆仓库，请稍候...'
    case 'SCANNING': return '正在扫描代码结构...'
    case 'ANALYZING': return '正在进行 AI 分析，请稍候...'
    case 'REPORTING': return '正在生成分析报告...'
    case 'PENDING': return '任务已提交，等待处理...'
    default: return '处理中...'
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
  let failCount = 0
  pollTimer = setInterval(async () => {
    try {
      const result = await getAnalysisResult(taskId.value)
      failCount = 0
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
        loadQuestions(taskId.value)
      } else if (result.status === 'FAILED') {
        stopPolling()
        errorMessage.value = result.errorMessage
      }
    } catch {
      failCount++
      if (failCount >= 5) {
        stopPolling()
        errorMessage.value = '获取分析状态失败，请刷新页面重试'
      }
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
        loadQuestions(pathTaskId)
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

function goToChat() {
  router.push({ path: '/chat', query: { repoUrl: repoUrl.value, taskId: taskId.value } })
}

function goToExplorer() {
  router.push(`/explorer/${taskId.value}`)
}

async function loadQuestions(id) {
  try {
    suggestedQuestions.value = await getQuestions(id)
  } catch {
    suggestedQuestions.value = []
  }
}

function askQuestion(question) {
  router.push({ path: '/chat', query: { repoUrl: repoUrl.value, taskId: taskId.value, question } })
}

function handleFileChange(file) {
  uploadFile.value = file.raw
}

async function handleUpload() {
  if (!uploadFile.value) return
  uploading.value = true
  report.value = null
  try {
    taskId.value = await uploadAndAnalyze(uploadFile.value)
    taskStatus.value = 'PENDING'
    router.replace({ path: `/analyze/${taskId.value}` })
    startPolling()
    uploadRef.value?.clearFiles()
    uploadFile.value = null
  } finally {
    uploading.value = false
  }
}

function copyCloneUrl() {
  if (!repoUrl.value) return
  navigator.clipboard.writeText('git clone ' + repoUrl.value)
  ElMessage.success('Clone 命令已复制到剪贴板')
}

function openGitHub() {
  if (!repoUrl.value) return
  window.open(repoUrl.value, '_blank')
}

function handleRetry() {
  taskId.value = ''
  taskStatus.value = ''
  errorMessage.value = ''
  report.value = null
  if (repoUrl.value) {
    handleAnalyze()
  }
}

function exportReport() {
  if (!report.value) return
  const r = report.value
  let md = `# CodeXray 分析报告\n\n`
  md += `**仓库**: ${repoUrl.value}\n\n`
  if (r.summary) md += `## 项目概述\n\n${r.summary}\n\n`
  if (r.primaryLanguage) md += `**主要语言**: ${r.primaryLanguage}\n\n`
  if (r.techStack?.length) md += `**技术栈**: ${r.techStack.join(', ')}\n\n`
  if (r.architecture) md += `**架构模式**: ${r.architecture}\n\n`
  if (r.score != null) md += `## 综合评分: ${r.score}/100\n\n`
  if (r.scoreDetails) {
    md += `| 维度 | 评分 |\n|------|------|\n`
    for (const [k, v] of Object.entries(r.scoreDetails)) {
      md += `| ${scoreLabels[k] || k} | ${v} |\n`
    }
    md += '\n'
  }
  if (r.modules?.length) {
    md += `## 模块结构\n\n| 模块 | 职责 |\n|------|------|\n`
    r.modules.forEach(m => { md += `| ${m.name} | ${m.description} |\n` })
    md += '\n'
  }
  if (r.strengths?.length) {
    md += `## 优点\n\n`
    r.strengths.forEach(s => { md += `- ${s}\n` })
    md += '\n'
  }
  if (r.improvements?.length) {
    md += `## 改进建议\n\n`
    r.improvements.forEach(s => { md += `- ${s}\n` })
    md += '\n'
  }
  if (r.verdict) md += `## 总结\n\n${r.verdict}\n`
  const blob = new Blob([md], { type: 'text/markdown' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  const repoName = repoUrl.value.split('/').pop() || 'report'
  a.download = `${repoName}-analysis.md`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('报告已导出')
}
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

.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.upload-area {
  flex: 1;
}

.upload-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 0;
}

.upload-text {
  font-size: 13px;
  color: #656d76;
}

.upload-text em {
  color: #2da44e;
  font-style: normal;
}

.upload-hint {
  font-size: 11px;
  color: #8b949e;
}

.action-bar {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #e8ecf0;
  margin-top: 16px;
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

.chat-jump {
  text-align: center;
  padding-top: 16px;
  border-top: 1px solid #e8ecf0;
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

.questions-section {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e8ecf0;
}

.questions-section h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 12px 0;
}

.question-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-tag {
  font-size: 13px;
  height: auto;
  padding: 6px 12px;
  white-space: normal;
  max-width: 100%;
  line-height: 1.5;
}

.error-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.error-actions {
  display: flex;
  gap: 12px;
}
</style>
