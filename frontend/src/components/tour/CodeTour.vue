<template>
  <div class="code-tour">
    <div v-if="loading" class="tour-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>AI 正在生成代码导览...</span>
    </div>
    <div v-else-if="error" class="tour-error">
      <el-alert :title="error" type="warning" :closable="false" show-icon />
    </div>
    <template v-else-if="tour && tour.stops && tour.stops.length > 0">
      <!-- 顶部 -->
      <div class="tour-header">
        <div class="tour-meta">
          <h3 class="tour-title">{{ tour.title }}</h3>
          <p class="tour-summary">{{ tour.summary }}</p>
        </div>
        <div class="tour-progress">
          <el-progress
            :percentage="tour.stops?.length ? Math.round((currentStep + 1) / tour.stops.length * 100) : 0"
            :stroke-width="6"
            :show-text="false"
            color="#2da44e"
          />
          <span class="progress-text">{{ currentStep + 1 }} / {{ tour.stops?.length || 0 }}</span>
        </div>
      </div>

      <div class="tour-body">
        <!-- 左侧步骤列表 -->
        <div class="tour-steps">
          <div
            v-for="(stop, i) in tour.stops"
            :key="i"
            class="step-item"
            :class="{ active: i === currentStep, done: i < currentStep }"
            @click="currentStep = i"
          >
            <div class="step-marker">
              <span v-if="i < currentStep" class="step-check">
                <el-icon><Check /></el-icon>
              </span>
              <span v-else>{{ i + 1 }}</span>
            </div>
            <div class="step-info">
              <div class="step-title">{{ stop.title }}</div>
              <div v-if="i === currentStep" class="step-hint">{{ stop.category }}</div>
            </div>
          </div>
        </div>

        <!-- 右侧内容 -->
        <div class="tour-content" v-if="currentStop">
          <div class="content-header">
            <span class="step-badge">步骤 {{ currentStep + 1 }}</span>
            <span class="content-category" v-if="currentStop.category">{{ currentStop.category }}</span>
          </div>
          <h4 class="content-title">{{ currentStop.title }}</h4>
          <div class="content-explanation" v-html="renderMarkdown(currentStop.explanation)"></div>

          <!-- 相关文件 -->
          <div v-if="currentStop.filePaths?.length" class="content-files">
            <div class="files-title">相关文件</div>
            <div
              v-for="fp in currentStop.filePaths"
              :key="fp"
              class="file-item"
              @click="$emit('fileClick', fp)"
            >
              <el-icon :size="14"><Document /></el-icon>
              <span class="file-name">{{ fp.split('/').pop() }}</span>
              <span class="file-path">{{ fp }}</span>
            </div>
          </div>

          <!-- 下一步提示 -->
          <div v-if="currentStop.nextHint" class="next-hint">
            <el-icon><ArrowRight /></el-icon>
            <span>{{ currentStop.nextHint }}</span>
          </div>

          <!-- 导航按钮 -->
          <div class="tour-nav">
            <el-button :disabled="currentStep === 0" @click="currentStep--">
              上一步
            </el-button>
            <el-button
              v-if="currentStep < (tour.stops?.length || 0) - 1"
              type="primary"
              @click="currentStep++"
            >
              下一步
            </el-button>
            <el-button v-else type="success" @click="$emit('complete')">
              导览完成
            </el-button>
          </div>
        </div>
      </div>
    </template>
    <div v-else-if="tour" class="tour-loading">
      <span>导览内容为空，请重试</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Loading, Check, Document, ArrowRight } from '@element-plus/icons-vue'
import { marked } from 'marked'

const props = defineProps({
  tour: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

defineEmits(['fileClick', 'complete'])

const currentStep = ref(0)

const currentStop = computed(() => {
  return props.tour?.stops?.[currentStep.value] || null
})

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text, { breaks: true })
}

watch(() => props.tour, () => {
  currentStep.value = 0
})
</script>

<style scoped>
.code-tour {
  border: 1px solid #e8ecf0;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.tour-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 20px;
  color: #656d76;
  font-size: 14px;
}

.tour-error {
  padding: 16px;
}

.tour-header {
  padding: 20px 24px;
  border-bottom: 1px solid #e8ecf0;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}

.tour-meta {
  flex: 1;
}

.tour-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 700;
  color: #1f2328;
}

.tour-summary {
  margin: 0;
  font-size: 13px;
  color: #656d76;
  line-height: 1.5;
}

.tour-progress {
  flex-shrink: 0;
  width: 120px;
  text-align: right;
}

.progress-text {
  font-size: 12px;
  color: #8b949e;
  margin-top: 4px;
  display: block;
}

.tour-body {
  display: flex;
  min-height: 400px;
}

.tour-steps {
  width: 240px;
  border-right: 1px solid #e8ecf0;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
}

.step-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.step-item:hover {
  background: #f6f8fa;
}

.step-item.active {
  background: #f0fdf4;
}

.step-item.done {
  opacity: 0.6;
}

.step-marker {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  background: #f0f2f5;
  color: #656d76;
}

.step-item.active .step-marker {
  background: #2da44e;
  color: #fff;
}

.step-item.done .step-marker {
  background: #dcfce7;
  color: #2da44e;
}

.step-check {
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-info {
  flex: 1;
  min-width: 0;
}

.step-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2328;
  line-height: 1.4;
}

.step-hint {
  font-size: 11px;
  color: #2da44e;
  margin-top: 2px;
}

.tour-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.content-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.step-badge {
  background: #2da44e;
  color: #fff;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.content-category {
  font-size: 12px;
  color: #8b949e;
}

.content-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 700;
  color: #1f2328;
}

.content-explanation {
  font-size: 14px;
  color: #1f2328;
  line-height: 1.8;
}

.content-explanation :deep(code) {
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.content-explanation :deep(pre) {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}

.content-explanation :deep(pre code) {
  background: none;
  padding: 0;
}

.content-files {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f2f5;
}

.files-title {
  font-size: 13px;
  font-weight: 600;
  color: #656d76;
  margin-bottom: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.1s;
}

.file-item:hover {
  background: #f6f8fa;
}

.file-name {
  font-size: 13px;
  color: #1f2328;
  font-weight: 500;
}

.file-path {
  font-size: 11px;
  color: #8b949e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.next-hint {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 20px;
  padding: 12px;
  background: #eff6ff;
  border-radius: 8px;
  font-size: 13px;
  color: #3b82f6;
  line-height: 1.5;
}

.tour-nav {
  display: flex;
  justify-content: space-between;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e8ecf0;
}

:global(html.dark) .code-tour {
  background: #22272e;
  border-color: #373e47;
}

:global(html.dark) .tour-header {
  border-bottom-color: #373e47;
}

:global(html.dark) .tour-title,
:global(html.dark) .content-title,
:global(html.dark) .step-title,
:global(html.dark) .file-name,
:global(html.dark) .content-explanation {
  color: #e6edf3;
}

:global(html.dark) .tour-steps {
  border-right-color: #373e47;
}
</style>
