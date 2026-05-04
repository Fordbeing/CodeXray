<template>
  <div class="compare-page">
    <div class="page-header">
      <h2>报告对比</h2>
      <p class="subtitle">对比同一仓库的两次分析报告，查看代码改进效果</p>
    </div>

    <div class="compare-selectors">
      <div class="selector-group">
        <label>选择仓库</label>
        <el-select v-model="selectedRepo" placeholder="选择仓库" filterable clearable style="width: 100%" @change="onRepoChange">
          <el-option
            v-for="group in repoGroups"
            :key="group.repoUrl"
            :label="formatRepoLabel(group)"
            :value="group.repoUrl"
          />
        </el-select>
      </div>
    </div>

    <div v-if="selectedRepo" class="compare-selectors">
      <div class="selector-group">
        <label>基准报告 (A)</label>
        <el-select v-model="selectedA" placeholder="选择较早的分析" filterable clearable style="width: 100%">
          <el-option
            v-for="task in currentTasks"
            :key="task.taskId"
            :label="formatTaskLabel(task)"
            :value="task.taskId"
          />
        </el-select>
      </div>
      <div class="selector-divider">
        <el-icon><Right /></el-icon>
      </div>
      <div class="selector-group">
        <label>对比报告 (B)</label>
        <el-select v-model="selectedB" placeholder="选择较新的分析" filterable clearable style="width: 100%">
          <el-option
            v-for="task in currentTasks"
            :key="task.taskId"
            :label="formatTaskLabel(task)"
            :value="task.taskId"
          />
        </el-select>
      </div>
      <el-button type="primary" @click="handleCompare" :loading="loading" :disabled="!selectedA || !selectedB || selectedA === selectedB">
        对比
      </el-button>
    </div>

    <div v-if="result" class="compare-result">
      <!-- 分数变化 -->
      <div class="score-diff-card">
        <div class="score-diff-header">
          <span>综合评分变化</span>
        </div>
        <div class="score-diff-body">
          <span class="score-a">{{ parseScore(result.taskA?.report) }}</span>
          <span class="score-arrow" :class="scoreDiffClass">
            <el-icon v-if="result.scoreDiff > 0"><Top /></el-icon>
            <el-icon v-else-if="result.scoreDiff < 0"><Bottom /></el-icon>
            <el-icon v-else><Right /></el-icon>
            <span class="diff-value">{{ result.scoreDiff > 0 ? '+' : '' }}{{ result.scoreDiff }}</span>
          </span>
          <span class="score-b">{{ parseScore(result.taskB?.report) }}</span>
        </div>
      </div>

      <!-- 雷达图对比 -->
      <div v-if="radarScoresA || radarScoresB" class="radar-compare-card">
        <h3>维度对比雷达图</h3>
        <RadarChart
          :scores="radarScoresA"
          :compare-scores="radarScoresB"
          :size="360"
        />
      </div>

      <div class="comparison-summary">
        <h3>对比分析</h3>
        <div class="summary-content" v-html="renderMarkdown(result.comparison)"></div>
      </div>

      <div class="reports-grid">
        <div class="report-card">
          <div class="report-label">基准 (A)</div>
          <div class="report-info">
            <div class="repo-url">{{ result.taskA?.repoUrl }}</div>
            <div class="report-time">{{ formatDate(result.taskA?.createdAt) }}</div>
          </div>
          <div class="report-content" v-html="renderMarkdown(reportToMd(result.taskA?.report))"></div>
        </div>
        <div class="report-card">
          <div class="report-label">对比 (B)</div>
          <div class="report-info">
            <div class="repo-url">{{ result.taskB?.repoUrl }}</div>
            <div class="report-time">{{ formatDate(result.taskB?.createdAt) }}</div>
          </div>
          <div class="report-content" v-html="renderMarkdown(reportToMd(result.taskB?.report))"></div>
        </div>
      </div>
    </div>

    <div v-if="!result && !loading" class="empty-state">
      <el-icon :size="48" color="#d0d7de"><DataAnalysis /></el-icon>
      <p v-if="!selectedRepo">选择一个仓库开始对比</p>
      <p v-else>选择两次分析任务进行对比</p>
    </div>

    <!-- 对比历史 -->
    <div v-if="historyRecords.length > 0" class="history-section">
      <h3>对比历史</h3>
      <div class="history-list">
        <div
          v-for="record in historyRecords"
          :key="record.comparisonId"
          class="history-item"
          @click="loadHistoryRecord(record.comparisonId)"
        >
          <div class="history-diff">
            <span v-if="record.scoreDiff > 0" class="diff-up">+{{ record.scoreDiff }}</span>
            <span v-else-if="record.scoreDiff < 0" class="diff-down">{{ record.scoreDiff }}</span>
            <span v-else class="diff-same">0</span>
          </div>
          <div class="history-info">
            <div class="history-repo">{{ formatRepoUrl(record.repoUrl) }}</div>
            <div class="history-tasks">{{ record.taskA?.substring(0, 8) }} → {{ record.taskB?.substring(0, 8) }}</div>
            <div class="history-time">{{ formatDate(record.createdAt) }}</div>
          </div>
          <el-button type="danger" text size="small" @click.stop="deleteHistoryRecord(record.comparisonId)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Right, DataAnalysis, Top, Bottom, Delete } from '@element-plus/icons-vue'
import { compareReports, listTasksForCompare, listComparisonRecords, getComparisonRecord, deleteComparisonRecord } from '../api/compare'
import { reportToMarkdown, parseReport } from '../utils/reportMarkdown'
import { marked } from 'marked'
import RadarChart from '../components/chart/RadarChart.vue'

// 对比历史
const historyRecords = ref([])

async function loadHistoryRecords() {
  try {
    historyRecords.value = await listComparisonRecords(30) || []
  } catch { /* ignore */ }
}

async function loadHistoryRecord(comparisonId) {
  try {
    result.value = await getComparisonRecord(comparisonId)
  } catch (e) {
    ElMessage.error('加载对比记录失败')
  }
}

async function deleteHistoryRecord(comparisonId) {
  try {
    await ElMessageBox.confirm('确定删除此对比记录？', '确认删除', { type: 'warning' })
    await deleteComparisonRecord(comparisonId)
    ElMessage.success('已删除')
    loadHistoryRecords()
  } catch { /* cancelled */ }
}

function formatRepoUrl(url) {
  if (!url) return 'unknown'
  return url.split('/').slice(-2).join('/')
}

const selectedRepo = ref('')
const selectedA = ref('')
const selectedB = ref('')
const result = ref(null)
const loading = ref(false)
const repoGroups = ref([])

const currentTasks = computed(() => {
  if (!selectedRepo.value) return []
  const group = repoGroups.value.find(g => g.repoUrl === selectedRepo.value)
  return group ? group.tasks : []
})

const scoreDiffClass = computed(() => {
  if (!result.value) return ''
  if (result.value.scoreDiff > 0) return 'diff-up'
  if (result.value.scoreDiff < 0) return 'diff-down'
  return 'diff-same'
})

const radarScoresA = computed(() => {
  if (!result.value?.taskA?.report) return null
  try {
    const r = typeof result.value.taskA.report === 'string' ? JSON.parse(result.value.taskA.report) : result.value.taskA.report
    return r.scoreDetails || null
  } catch { return null }
})

const radarScoresB = computed(() => {
  if (!result.value?.taskB?.report) return null
  try {
    const r = typeof result.value.taskB.report === 'string' ? JSON.parse(result.value.taskB.report) : result.value.taskB.report
    return r.scoreDetails || null
  } catch { return null }
})

function formatRepoLabel(group) {
  const repo = group.repoUrl ? group.repoUrl.split('/').slice(-2).join('/') : 'unknown'
  return `${repo} (${group.tasks.length} 次分析)`
}

function formatTaskLabel(task) {
  const date = task.createdAt ? new Date(task.createdAt).toLocaleString('zh-CN') : ''
  return date || task.taskId
}

function formatDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleString('zh-CN')
}

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text, { breaks: true })
}

function reportToMd(reportStr) {
  const r = parseReport(reportStr)
  return r ? reportToMarkdown(r) : reportStr || ''
}

function parseScore(reportStr) {
  const r = parseReport(reportStr)
  return r?.score ?? '-'
}

function onRepoChange() {
  selectedA.value = ''
  selectedB.value = ''
  result.value = null
}

async function loadTasks() {
  try {
    const data = await listTasksForCompare(100)
    repoGroups.value = data || []
  } catch { /* ignore */ }
}

async function handleCompare() {
  if (!selectedA.value || !selectedB.value || selectedA.value === selectedB.value) return
  loading.value = true
  result.value = null
  try {
    const data = await compareReports(selectedA.value, selectedB.value)
    result.value = data
    loadHistoryRecords()
  } catch (e) {
    const msg = e?.response?.data?.message || e.message || '未知错误'
    ElMessage.error('对比失败: ' + msg)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTasks()
  loadHistoryRecords()
})
</script>

<style scoped>
.compare-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1f2328;
  margin: 0 0 6px;
}

.subtitle {
  color: #656d76;
  font-size: 14px;
  margin: 0;
}

.compare-selectors {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 16px;
  background: #fff;
  padding: 20px;
  border: 1px solid #d8dee4;
  border-radius: 12px;
}

.selector-group {
  flex: 1;
}

.selector-group label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #1f2328;
  margin-bottom: 8px;
}

.selector-divider {
  display: flex;
  align-items: center;
  padding-bottom: 4px;
  color: #8b949e;
}

.score-diff-card {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 16px;
}

.radar-compare-card {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  text-align: center;
}

.radar-compare-card h3 {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2328;
}

.score-diff-header {
  padding: 12px 20px;
  background: #f6f8fa;
  border-bottom: 1px solid #d8dee4;
  font-size: 14px;
  font-weight: 600;
  color: #1f2328;
}

.score-diff-body {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 24px;
  font-size: 20px;
  font-weight: 700;
}

.score-a, .score-b {
  font-size: 32px;
  color: #1f2328;
}

.score-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 24px;
}

.diff-up { color: #2da44e; }
.diff-down { color: #cf222e; }
.diff-same { color: #8b949e; }

.diff-value {
  font-size: 18px;
  font-weight: 700;
}

.comparison-summary {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}

.comparison-summary h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 12px;
}

.summary-content {
  font-size: 14px;
  color: #1f2328;
  line-height: 1.7;
}

.reports-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.report-card {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  overflow: hidden;
}

.report-label {
  padding: 10px 16px;
  background: #f6f8fa;
  border-bottom: 1px solid #d8dee4;
  font-size: 13px;
  font-weight: 600;
  color: #656d76;
}

.report-info {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f5;
}

.repo-url {
  font-size: 14px;
  font-weight: 600;
  color: #0969da;
}

.report-time {
  font-size: 12px;
  color: #8b949e;
  margin-top: 2px;
}

.report-content {
  padding: 16px;
  font-size: 13px;
  line-height: 1.7;
  max-height: 600px;
  overflow-y: auto;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px;
  color: #8b949e;
}

.empty-state p {
  margin-top: 12px;
  font-size: 14px;
}

.history-section {
  margin-top: 24px;
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  padding: 20px;
}

.history-section h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 12px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e8ecf0;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s;
}

.history-item:hover {
  border-color: #0969da;
}

.history-diff {
  flex-shrink: 0;
  font-size: 16px;
  font-weight: 700;
  min-width: 48px;
  text-align: center;
  padding-top: 2px;
}

.history-info {
  flex: 1;
  min-width: 0;
}

.history-repo {
  font-size: 13px;
  font-weight: 600;
  color: #0969da;
  margin-bottom: 2px;
}

.history-tasks {
  font-family: monospace;
  font-size: 12px;
  color: #656d76;
  margin-bottom: 2px;
}

.history-time {
  font-size: 12px;
  color: #8b949e;
}

@media (max-width: 768px) {
  .compare-selectors {
    flex-direction: column;
    align-items: stretch;
  }

  .selector-divider {
    display: none;
  }

  .reports-grid {
    grid-template-columns: 1fr;
  }
}
</style>
