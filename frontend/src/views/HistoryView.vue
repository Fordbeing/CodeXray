<template>
  <div class="history-page">
    <div class="page-header">
      <h1 class="page-title">分析历史</h1>
      <div class="header-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索仓库地址..."
          prefix-icon="Search"
          clearable
          style="width: 260px"
          @input="handleSearch"
        />
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 130px" @change="handleSearch">
          <el-option label="全部" value="" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="分析中" value="ANALYZING" />
          <el-option label="克隆中" value="CLONING" />
          <el-option label="等待中" value="PENDING" />
          <el-option label="已失败" value="FAILED" />
        </el-select>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <span class="stat-num">{{ allTasks.length }}</span>
        <span class="stat-label">总任务</span>
      </div>
      <div class="stat-card">
        <span class="stat-num completed">{{ countByStatus('COMPLETED') }}</span>
        <span class="stat-label">已完成</span>
      </div>
      <div class="stat-card">
        <span class="stat-num running">{{ countByStatus('ANALYZING') + countByStatus('CLONING') + countByStatus('PENDING') }}</span>
        <span class="stat-label">进行中</span>
      </div>
      <div class="stat-card">
        <span class="stat-num failed">{{ countByStatus('FAILED') }}</span>
        <span class="stat-label">已失败</span>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table
        :data="pagedTasks"
        stripe
        v-loading="loading"
        empty-text="暂无分析记录"
        @sort-change="handleSortChange"
      >
        <el-table-column label="仓库" min-width="280" prop="repoUrl" sortable="custom">
          <template #default="{ row }">
            <div class="repo-cell">
              <div class="repo-icon">
                <svg viewBox="0 0 16 16" width="16" height="16"><path fill="#656d76" d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8ZM5 12.25a.25.25 0 0 1 .25-.25h3.5a.25.25 0 0 1 .25.25v3.25a.25.25 0 0 1-.4.2l-1.45-1.087a.249.249 0 0 0-.3 0L5.4 15.7a.25.25 0 0 1-.4-.2Z"/></svg>
              </div>
              <div class="repo-info">
                <span class="repo-url" :title="row.repoUrl">{{ shortenUrl(row.repoUrl) }}</span>
                <span class="repo-time">{{ formatTime(row.createdAt) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusInfo(row.status).type" size="small" effect="light" round>
              {{ getStatusInfo(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" sortable="custom" prop="createdAt">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'COMPLETED'"
              type="primary"
              link
              size="small"
              @click="viewResult(row.taskId)"
            >
              查看报告
            </el-button>
            <el-button
              v-if="row.status === 'COMPLETED'"
              type="success"
              link
              size="small"
              @click="goChat(row)"
            >
              问答
            </el-button>
            <el-popconfirm
              title="确定删除该分析记录？"
              @confirm="handleDelete(row.taskId)"
            >
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap" v-if="filteredTasks.length > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="filteredTasks.length"
          layout="prev, pager, next"
          background
          small
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { listTasks, deleteTask } from '../api/analysis'
import { getStatusInfo, formatTime, shortenUrl } from '../utils/status'
import { ElMessage } from 'element-plus'

const router = useRouter()
const allTasks = ref([])
const loading = ref(false)
const searchQuery = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = 15
const sortField = ref('')
const sortOrder = ref('')

function countByStatus(status) {
  return allTasks.value.filter(t => t.status === status).length
}

const filteredTasks = computed(() => {
  let list = allTasks.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(t => (t.repoUrl || '').toLowerCase().includes(q))
  }
  if (statusFilter.value) {
    list = list.filter(t => t.status === statusFilter.value)
  }
  if (sortField.value === 'createdAt') {
    list = [...list].sort((a, b) => {
      const da = new Date(a.createdAt).getTime()
      const db = new Date(b.createdAt).getTime()
      return sortOrder.value === 'ascending' ? da - db : db - da
    })
  }
  return list
})

const pagedTasks = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredTasks.value.slice(start, start + pageSize)
})

function handleSearch() {
  currentPage.value = 1
}

function handleSortChange({ prop, order }) {
  sortField.value = prop
  sortOrder.value = order
}

async function loadTasks() {
  loading.value = true
  try {
    allTasks.value = await listTasks(200)
  } finally {
    loading.value = false
  }
}

function viewResult(taskId) {
  router.push(`/analyze/${taskId}`)
}

function goChat(row) {
  const repoName = shortenUrl(row.repoUrl)
  router.push({ path: '/chat', query: { repoUrl: row.repoUrl, taskId: row.taskId } })
}

async function handleDelete(taskId) {
  try {
    await deleteTask(taskId)
    ElMessage.success('已删除')
    await loadTasks()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || '未知错误'))
  }
}

function onAuthChange(e) {
  if (!e.detail) {
    allTasks.value = []
  } else {
    loadTasks()
  }
}

onMounted(() => {
  loadTasks()
  window.addEventListener('auth-change', onAuthChange)
})

onUnmounted(() => {
  window.removeEventListener('auth-change', onAuthChange)
})
</script>

<style scoped>
.history-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2328;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* 统计卡片 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e8ecf0;
  border-radius: 10px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-num {
  font-size: 24px;
  font-weight: 800;
  color: #1f2328;
}

.stat-num.completed { color: #2da44e; }
.stat-num.running { color: #d29922; }
.stat-num.failed { color: #cf222e; }

.stat-label {
  font-size: 12px;
  color: #8b949e;
  font-weight: 500;
}

/* 表格卡片 */
.table-card {
  border-radius: 12px;
}

.repo-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.repo-icon {
  flex-shrink: 0;
  opacity: 0.5;
}

.repo-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.repo-url {
  font-size: 13px;
  color: #2da44e;
  font-weight: 500;
  cursor: default;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.repo-time {
  font-size: 11px;
  color: #8b949e;
  margin-top: 2px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

@media (max-width: 767px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    flex-wrap: wrap;
  }

  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
