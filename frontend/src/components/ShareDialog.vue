<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="分享分析报告"
    width="480px"
    :close-on-click-modal="false"
  >
    <div v-if="!shareResult">
      <el-form label-position="top">
        <el-form-item label="密码保护（可选）">
          <el-input
            v-model="password"
            placeholder="留空则不设密码"
            type="password"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item label="有效期">
          <el-radio-group v-model="expiresInDays">
            <el-radio :value="7">7 天</el-radio>
            <el-radio :value="30">30 天</el-radio>
            <el-radio :value="0">永久</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </div>

    <div v-else class="share-result">
      <el-alert
        title="分享链接已生成"
        type="success"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <div class="share-link-row">
        <el-input
          :model-value="shareUrl"
          readonly
          ref="linkInputRef"
        />
        <el-button type="primary" @click="copyLink">
          <el-icon style="margin-right: 4px"><CopyDocument /></el-icon>
          复制
        </el-button>
      </div>
      <div v-if="shareResult.passwordProtected" class="share-hint">
        <el-icon><Lock /></el-icon>
        <span>此链接需要密码访问</span>
      </div>
      <div v-if="shareResult.expiresAt" class="share-hint">
        <el-icon><Clock /></el-icon>
        <span>过期时间: {{ formatDate(shareResult.expiresAt) }}</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ shareResult ? '关闭' : '取消' }}
      </el-button>
      <el-button v-if="!shareResult" type="primary" :loading="creating" @click="handleCreate">
        生成分享链接
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Lock, Clock } from '@element-plus/icons-vue'
import { createShareLink } from '../api/analysis'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  taskId: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const password = ref('')
const expiresInDays = ref(7)
const creating = ref(false)
const shareResult = ref(null)
const linkInputRef = ref(null)

const shareUrl = ref('')

// 对话框打开时重置状态
watch(() => props.modelValue, (val) => {
  if (val) {
    password.value = ''
    expiresInDays.value = 7
    shareResult.value = null
    shareUrl.value = ''
  }
})

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

async function handleCreate() {
  if (!props.taskId) {
    ElMessage.error('缺少任务ID')
    return
  }
  creating.value = true
  try {
    const result = await createShareLink(props.taskId, {
      password: password.value || undefined,
      expiresInDays: expiresInDays.value ?? undefined
    })
    shareResult.value = result
    shareUrl.value = `${window.location.origin}/share/${result.shareToken}`
  } catch (e) {
    ElMessage.error('创建分享失败: ' + (e.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

function copyLink() {
  navigator.clipboard.writeText(shareUrl.value)
  ElMessage.success('链接已复制到剪贴板')
}
</script>

<style scoped>
.share-result {
  text-align: center;
}

.share-link-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.share-link-row .el-input {
  flex: 1;
}

.share-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  color: #8b949e;
  margin-top: 8px;
}
</style>
