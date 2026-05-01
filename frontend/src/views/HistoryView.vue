<template>
  <div class="history-page">
    <h1 class="page-title">分析历史</h1>

    <el-card shadow="never">
      <el-table :data="tasks" stripe v-loading="loading" empty-text="暂无分析记录">
        <el-table-column label="仓库" min-width="250">
          <template #default="{ row }">
            <span class="repo-url" :title="row.repoUrl">{{ shortenUrl(row.repoUrl) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusInfo(row.status).type" size="small">
              {{ getStatusInfo(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
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
              查看
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
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listTasks, deleteTask } from '../api/analysis'
import { getStatusInfo, formatTime, shortenUrl } from '../utils/status'
import { ElMessage } from 'element-plus'

const router = useRouter()
const tasks = ref([])
const loading = ref(false)

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await listTasks(50)
  } finally {
    loading.value = false
  }
}

function viewResult(taskId) {
  router.push(`/analyze/${taskId}`)
}

async function handleDelete(taskId) {
  await deleteTask(taskId)
  ElMessage.success('已删除')
  await loadTasks()
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2328;
  margin: 0 0 20px 0;
}

.repo-url {
  font-size: 13px;
  color: #2da44e;
  cursor: default;
}
</style>
