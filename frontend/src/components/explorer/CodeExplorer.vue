<template>
  <div class="explorer-page">
    <div class="explorer-header">
      <el-button text @click="$router.back()">
        <el-icon style="margin-right: 4px"><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h1 class="page-title">代码浏览器</h1>
      <span class="task-hint">任务: {{ taskId.slice(0, 8) }}...</span>
    </div>

    <div class="explorer-body">
      <!-- 左侧文件树 -->
      <div class="file-tree-panel">
        <div class="tree-search">
          <el-input v-model="treeFilter" placeholder="搜索文件..." clearable size="small">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <div class="tree-content">
          <div v-if="loadingTree" class="tree-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            加载文件树...
          </div>
          <el-tree
            v-else
            :data="treeData"
            :props="treeProps"
            :filter-node-method="filterNode"
            node-key="path"
            highlight-current
            @node-click="handleNodeClick"
            ref="treeRef"
          >
            <template #default="{ node, data }">
              <span class="tree-node">
                <el-icon v-if="!data.isLeaf" :size="14"><Folder /></el-icon>
                <el-icon v-else :size="14"><Document /></el-icon>
                <span class="node-label">{{ node.label }}</span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>

      <!-- 右侧代码查看器 -->
      <div class="code-viewer-panel">
        <div v-if="!selectedFile" class="viewer-placeholder">
          <el-icon :size="48" color="#d0d7de"><Document /></el-icon>
          <p>点击左侧文件查看源码</p>
        </div>
        <div v-else class="viewer-content">
          <div class="viewer-header">
            <span class="file-path">{{ selectedFile }}</span>
            <el-button size="small" @click="copyFileContent" text>
              <el-icon style="margin-right: 4px"><CopyDocument /></el-icon>
              复制
            </el-button>
            <el-button size="small" @click="referenceToChat" text>
              <el-icon style="margin-right: 4px"><ChatDotRound /></el-icon>
              引用到问答
            </el-button>
          </div>
          <div v-if="loadingFile" class="viewer-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            加载中...
          </div>
          <pre v-else class="code-block"><code ref="codeRef" :class="'language-' + fileLang">{{ fileContent }}</code></pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Search, Loading, Folder, Document, CopyDocument, ChatDotRound } from '@element-plus/icons-vue'
import { getFileTree, getFileContent } from '../../api/analysis'
import { useRouter } from 'vue-router'
import hljs from 'highlight.js/lib/core'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => route.params.taskId)

const treeData = ref([])
const treeFilter = ref('')
const treeRef = ref(null)
const loadingTree = ref(true)
const selectedFile = ref(null)
const fileContent = ref('')
const loadingFile = ref(false)
const codeRef = ref(null)

const treeProps = { label: 'label', children: 'children' }

const fileLang = computed(() => {
  if (!selectedFile.value) return 'plaintext'
  const ext = selectedFile.value.split('.').pop().toLowerCase()
  const langMap = {
    js: 'javascript', jsx: 'javascript', ts: 'typescript', tsx: 'typescript',
    py: 'python', java: 'java', html: 'xml', xml: 'xml',
    css: 'css', scss: 'css', sql: 'sql', sh: 'bash', bash: 'bash',
    json: 'json', yml: 'yaml', yaml: 'yaml', md: 'markdown',
    go: 'go', rs: 'rust', c: 'cpp', cpp: 'cpp', h: 'cpp',
    vue: 'xml', rb: 'ruby', php: 'php', swift: 'swift',
  }
  return langMap[ext] || 'plaintext'
})

watch(treeFilter, (val) => {
  treeRef.value?.filter(val)
})

watch(treeData, () => {
  nextTick(() => treeRef.value?.filter(treeFilter.value))
})

onMounted(async () => {
  try {
    treeData.value = await getFileTree(taskId.value)
  } catch (e) {
    ElMessage.error('加载文件树失败')
  } finally {
    loadingTree.value = false
  }
})

function filterNode(value, data) {
  if (!value) return true
  return data.label.toLowerCase().includes(value.toLowerCase())
}

async function handleNodeClick(data) {
  if (!data.isLeaf) return
  selectedFile.value = data.path
  loadingFile.value = true
  try {
    fileContent.value = await getFileContent(taskId.value, data.path)
    nextTick(() => {
      if (codeRef.value) {
        hljs.highlightElement(codeRef.value)
      }
    })
  } catch (e) {
    fileContent.value = '// 加载失败: ' + (e.message || '未知错误')
  } finally {
    loadingFile.value = false
  }
}

async function copyFileContent() {
  try {
    await navigator.clipboard.writeText(fileContent.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

function referenceToChat() {
  if (!selectedFile.value || !fileContent.value) return
  // 保存代码引用到 localStorage，ChatView 启动时读取
  const ref = { file: selectedFile.value, content: fileContent.value.slice(0, 2000) }
  localStorage.setItem('codexray_code_ref', JSON.stringify(ref))
  router.push({ path: '/chat', query: { taskId: taskId.value } })
  ElMessage.success('已引用，跳转到问答')
}
</script>

<style scoped>
.explorer-page {
  height: calc(100vh - 56px);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #d8dee4;
  overflow: hidden;
}

.explorer-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid #e8ecf0;
  flex-shrink: 0;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2328;
  margin: 0;
  flex: 1;
}

.task-hint {
  font-size: 12px;
  color: #8b949e;
}

.explorer-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 左侧文件树 */
.file-tree-panel {
  width: 280px;
  min-width: 280px;
  border-right: 1px solid #e8ecf0;
  display: flex;
  flex-direction: column;
  background: #f8faf9;
}

.tree-search {
  padding: 10px 12px;
  border-bottom: 1px solid #e8ecf0;
}

.tree-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.tree-loading {
  text-align: center;
  padding: 40px 16px;
  color: #8b949e;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右侧代码查看器 */
.code-viewer-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.viewer-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8b949e;
}

.viewer-placeholder p {
  margin-top: 12px;
  font-size: 14px;
}

.viewer-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.viewer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #f6f8fa;
  border-bottom: 1px solid #e8ecf0;
  flex-shrink: 0;
}

.file-path {
  font-size: 13px;
  color: #1f2328;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.viewer-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #8b949e;
  font-size: 13px;
}

.code-block {
  flex: 1;
  margin: 0;
  padding: 16px;
  overflow: auto;
  background: #1f2328;
  color: #e6edf3;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.code-block code {
  background: none;
  padding: 0;
  color: inherit;
  font-size: inherit;
}

@media (max-width: 767px) {
  .file-tree-panel {
    width: 200px;
    min-width: 200px;
  }
}
</style>
