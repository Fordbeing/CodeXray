<template>
  <div class="review-page">
    <div class="page-header">
      <h2>AI 代码审查</h2>
      <p class="subtitle">粘贴 git diff 或从已有分析中选择文件进行审查</p>
    </div>

    <el-tabs v-model="activeTab" class="review-tabs">
      <!-- Tab 1: Diff 审查 -->
      <el-tab-pane label="Diff 审查" name="diff">
        <div class="review-input">
          <el-input
            v-model="diffInput"
            type="textarea"
            :rows="10"
            placeholder="粘贴 git diff 内容..."
            class="diff-textarea"
          />
          <div class="input-actions">
            <el-button type="primary" @click="handleReviewDiff" :loading="loadingDiff" :disabled="!diffInput.trim()">
              <el-icon><Search /></el-icon>
              开始审查
            </el-button>
            <el-button @click="diffInput = ''">清空</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 文件审查 -->
      <el-tab-pane label="文件审查" name="file">
        <div class="file-review-layout">
          <div class="file-selector">
            <div class="selector-group">
              <label>选择分析任务</label>
              <el-select v-model="selectedTask" placeholder="选择已有分析" filterable clearable style="width: 100%" @change="loadFileTree">
                <el-option
                  v-for="task in availableTasks"
                  :key="task.taskId"
                  :label="formatTaskLabel(task)"
                  :value="task.taskId"
                />
              </el-select>
            </div>

            <div v-if="selectedTask" class="selector-group">
              <label>选择文件</label>
              <el-tree
                :data="fileTree"
                :props="{ label: 'label', children: 'children', isLeaf: 'isLeaf' }"
                node-key="path"
                highlight-current
                @node-click="onFileSelect"
                class="file-tree"
                empty-text="加载中..."
              >
                <template #default="{ node, data }">
                  <span class="tree-node">
                    <el-icon v-if="!data.isLeaf" style="margin-right: 4px"><FolderOpened /></el-icon>
                    <el-icon v-else style="margin-right: 4px"><Document /></el-icon>
                    {{ node.label }}
                  </span>
                </template>
              </el-tree>
            </div>

            <div v-if="selectedFile" class="selected-file-info">
              <el-tag type="success" size="small">{{ selectedFile }}</el-tag>
              <el-button type="primary" size="small" @click="handleReviewFile" :loading="loadingFile">
                审查此文件
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 3: 审查历史 -->
      <el-tab-pane label="审查历史" name="history">
        <div v-if="historyRecords.length === 0" class="empty-history">
          <el-icon :size="32" color="#d0d7de"><Clock /></el-icon>
          <span>暂无审查记录</span>
        </div>
        <div v-else class="history-list">
          <div
            v-for="record in historyRecords"
            :key="record.reviewId"
            class="history-item"
            @click="loadHistoryRecord(record.reviewId)"
          >
            <div class="history-score">
              <el-tag :type="hunkScoreType(record.score || 0)" size="small">{{ record.score || '-' }}/10</el-tag>
            </div>
            <div class="history-info">
              <div class="history-type">
                <el-tag v-if="record.inputType === 'file'" type="success" size="small">文件审查</el-tag>
                <el-tag v-else type="info" size="small">Diff 审查</el-tag>
                <span v-if="record.filePath" class="history-filepath">{{ record.filePath }}</span>
              </div>
              <div v-if="record.diffPreview" class="history-preview">{{ record.diffPreview }}...</div>
              <div class="history-time">{{ formatDate(record.createdAt) }}</div>
            </div>
            <el-button type="danger" text size="small" @click.stop="deleteHistoryRecord(record.reviewId)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 审查结果 -->
    <div v-if="result" class="review-result">
      <div class="result-header">
        <div class="score-badge" :class="scoreClass">
          <span class="score-value">{{ result.score }}</span>
          <span class="score-label">/ 10</span>
        </div>
        <div class="summary">{{ result.summary }}</div>
      </div>

      <!-- Hunk 分组结果 -->
      <div v-if="result.hunkResults?.length > 1" class="hunk-results">
        <h3>分块审查结果</h3>
        <el-collapse v-model="expandedHunks">
          <el-collapse-item v-for="(hunk, idx) in result.hunkResults" :key="idx" :name="idx">
            <template #title>
              <div class="hunk-title">
                <span class="hunk-file">{{ hunk.file }}</span>
                <span class="hunk-header">{{ hunk.header }}</span>
                <el-tag :type="hunkScoreType(hunk.score)" size="small">{{ hunk.score }}/10</el-tag>
              </div>
            </template>
            <div class="hunk-summary">{{ hunk.summary }}</div>
            <div v-if="hunk.comments?.length" class="hunk-comments">
              <div
                v-for="(comment, ci) in hunk.comments"
                :key="ci"
                class="comment-item"
                :class="'severity-' + comment.severity"
              >
                <div class="comment-header">
                  <el-tag :type="severityType(comment.severity)" size="small">
                    {{ severityLabel(comment.severity) }}
                  </el-tag>
                  <span v-if="comment.line" class="comment-line">L{{ comment.line }}</span>
                </div>
                <div class="comment-message">{{ comment.message }}</div>
              </div>
            </div>
            <div v-else class="no-comments-hunk">
              <el-icon color="#2da44e"><CircleCheck /></el-icon>
              <span>此代码块无问题</span>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- 所有评论列表 -->
      <div v-if="result.comments?.length" class="comments-section">
        <h3>审查意见 ({{ result.comments.length }})</h3>
        <div
          v-for="(comment, idx) in result.comments"
          :key="idx"
          class="comment-item"
          :class="'severity-' + comment.severity"
        >
          <div class="comment-header">
            <el-tag :type="severityType(comment.severity)" size="small">
              {{ severityLabel(comment.severity) }}
            </el-tag>
            <span class="comment-file">{{ comment.file }}</span>
            <span v-if="comment.line" class="comment-line">L{{ comment.line }}</span>
          </div>
          <div class="comment-message">{{ comment.message }}</div>
        </div>
      </div>

      <div v-if="!result.comments?.length" class="no-comments">
        <el-icon :size="24" color="#2da44e"><CircleCheck /></el-icon>
        <span>未发现明显问题</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, CircleCheck, FolderOpened, Document, Clock, Delete } from '@element-plus/icons-vue'
import { reviewCode, reviewFile, getFileTree, listTasksForReview, listReviewRecords, getReviewRecord, deleteReviewRecord } from '../api/review'

const activeTab = ref('diff')
const diffInput = ref('')
const result = ref(null)
const loadingDiff = ref(false)
const loadingFile = ref(false)

// 文件审查状态
const selectedTask = ref('')
const selectedFile = ref('')
const availableTasks = ref([])
const fileTree = ref([])
const expandedHunks = ref([])

const scoreClass = ref('')

function updateScoreClass() {
  if (!result.value) { scoreClass.value = ''; return }
  if (result.value.score >= 8) scoreClass.value = 'score-good'
  else if (result.value.score >= 5) scoreClass.value = 'score-warn'
  else scoreClass.value = 'score-bad'
}

function severityType(severity) {
  if (severity === 'error') return 'danger'
  if (severity === 'warning') return 'warning'
  return 'info'
}

function severityLabel(severity) {
  if (severity === 'error') return '错误'
  if (severity === 'warning') return '警告'
  return '建议'
}

function hunkScoreType(score) {
  if (score >= 8) return 'success'
  if (score >= 5) return 'warning'
  return 'danger'
}

function formatTaskLabel(task) {
  const repo = task.repoUrl ? task.repoUrl.split('/').slice(-2).join('/') : task.taskId
  const date = task.createdAt ? new Date(task.createdAt).toLocaleDateString('zh-CN') : ''
  return `${repo} (${date})`
}

async function loadTasks() {
  try {
    const data = await listTasksForReview(30)
    availableTasks.value = (data || []).filter(t => t.status === 'COMPLETED')
  } catch { /* ignore */ }
}

async function loadFileTree() {
  selectedFile.value = ''
  fileTree.value = []
  if (!selectedTask.value) return
  try {
    fileTree.value = await getFileTree(selectedTask.value)
  } catch {
    ElMessage.error('获取文件树失败')
  }
}

function onFileSelect(data) {
  if (data.isLeaf) {
    selectedFile.value = data.path
  }
}

// 审查历史
const historyRecords = ref([])

async function loadHistoryRecords() {
  try {
    historyRecords.value = await listReviewRecords(30) || []
  } catch { /* ignore */ }
}

async function loadHistoryRecord(reviewId) {
  try {
    result.value = await getReviewRecord(reviewId)
    updateScoreClass()
    activeTab.value = 'diff' // 切到审查 tab 展示结果
  } catch (e) {
    ElMessage.error('加载审查记录失败')
  }
}

async function deleteHistoryRecord(reviewId) {
  try {
    await ElMessageBox.confirm('确定删除此审查记录？', '确认删除', { type: 'warning' })
    await deleteReviewRecord(reviewId)
    ElMessage.success('已删除')
    loadHistoryRecords()
  } catch { /* cancelled */ }
}

function formatDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleString('zh-CN')
}

// 审查完成后自动刷新历史
async function handleReviewDiff() {
  if (!diffInput.value.trim()) return
  loadingDiff.value = true
  result.value = null
  try {
    const data = await reviewCode(diffInput.value)
    result.value = data
    updateScoreClass()
    loadHistoryRecords()
  } catch (e) {
    ElMessage.error('审查失败: ' + (e.message || '未知错误'))
  } finally {
    loadingDiff.value = false
  }
}

async function handleReviewFile() {
  if (!selectedTask.value || !selectedFile.value) return
  loadingFile.value = true
  result.value = null
  try {
    const data = await reviewFile(selectedTask.value, selectedFile.value)
    result.value = data
    updateScoreClass()
    loadHistoryRecords()
  } catch (e) {
    ElMessage.error('审查失败: ' + (e.message || '未知错误'))
  } finally {
    loadingFile.value = false
  }
}

watch(activeTab, (val) => {
  if (val === 'history') loadHistoryRecords()
})

onMounted(() => {
  loadTasks()
  loadHistoryRecords()
})
</script>

<style scoped>
.review-page {
  max-width: 900px;
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

.review-tabs {
  margin-bottom: 24px;
}

.review-input {
  margin-bottom: 24px;
}

.diff-textarea :deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.input-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.file-review-layout {
  padding: 8px 0;
}

.file-selector {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.selector-group label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #1f2328;
  margin-bottom: 8px;
}

.file-tree {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #d8dee4;
  border-radius: 8px;
  padding: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.selected-file-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-result {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 12px;
  overflow: hidden;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #f6f8fa;
  border-bottom: 1px solid #d8dee4;
}

.score-badge {
  display: flex;
  align-items: baseline;
  gap: 2px;
  padding: 8px 16px;
  border-radius: 8px;
  font-weight: 700;
}

.score-good { background: #dafbe1; color: #1a7f37; }
.score-warn { background: #fff8c5; color: #9a6700; }
.score-bad { background: #ffebe9; color: #cf222e; }

.score-value { font-size: 24px; }
.score-label { font-size: 13px; opacity: 0.7; }

.summary {
  font-size: 14px;
  color: #1f2328;
  line-height: 1.5;
}

.hunk-results {
  padding: 20px;
  border-bottom: 1px solid #e8ecf0;
}

.hunk-results h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 12px;
}

.hunk-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.hunk-file {
  font-family: monospace;
  font-size: 13px;
  font-weight: 600;
  color: #1f2328;
}

.hunk-header {
  font-family: monospace;
  font-size: 12px;
  color: #8b949e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 400px;
}

.hunk-summary {
  font-size: 13px;
  color: #656d76;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
}

.hunk-comments {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.no-comments-hunk {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #2da44e;
  font-size: 13px;
}

.comments-section {
  padding: 20px;
}

.comments-section h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 12px;
}

.comment-item {
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 8px;
  border-left: 3px solid transparent;
}

.severity-error { background: #fff5f5; border-left-color: #cf222e; }
.severity-warning { background: #fffbf0; border-left-color: #d4a72c; }
.severity-info { background: #f6f8fa; border-left-color: #0969da; }

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.comment-file {
  font-family: monospace;
  font-size: 12px;
  color: #656d76;
}

.comment-line {
  font-family: monospace;
  font-size: 12px;
  color: #0969da;
  font-weight: 600;
}

.comment-message {
  font-size: 14px;
  color: #1f2328;
  line-height: 1.5;
}

.no-comments {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  color: #2da44e;
  font-size: 14px;
  font-weight: 500;
}

.empty-history {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px;
  color: #8b949e;
  font-size: 14px;
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
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s;
}

.history-item:hover {
  border-color: #0969da;
}

.history-score {
  flex-shrink: 0;
  padding-top: 2px;
}

.history-info {
  flex: 1;
  min-width: 0;
}

.history-type {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.history-filepath {
  font-family: monospace;
  font-size: 12px;
  color: #1f2328;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-preview {
  font-family: monospace;
  font-size: 12px;
  color: #656d76;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.history-time {
  font-size: 12px;
  color: #8b949e;
}
</style>
