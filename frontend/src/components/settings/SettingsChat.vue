<template>
  <div class="setting-card">
    <div class="card-header">
      <div class="card-icon" style="background: linear-gradient(135deg, #fefce8, #fef9c3)">
        <el-icon :size="20" color="#d29922"><ChatDotRound /></el-icon>
      </div>
      <div>
        <div class="card-title">对话设置</div>
        <div class="card-desc">代码问答的行为参数</div>
      </div>
    </div>
    <div class="card-body">
      <div class="form-row two-col">
        <div class="form-item">
          <label class="form-label">最大历史消息数</label>
          <el-input-number v-model="form.chat_max_history" :min="5" :max="50" size="large" style="width: 160px" />
          <div class="form-hint">每次对话保留的上下文消息数量，越大越准确但消耗更多 Token</div>
        </div>
        <div class="form-item">
          <label class="form-label">默认语言</label>
          <el-radio-group v-model="form.default_language" size="large">
            <el-radio-button value="zh">中文</el-radio-button>
            <el-radio-button value="en">English</el-radio-button>
          </el-radio-group>
          <div class="form-hint">AI 分析和对话的默认输出语言</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useSettingsForm } from '../../composables/useSettingsForm'
import { ChatDotRound } from '@element-plus/icons-vue'

const { form } = useSettingsForm()
</script>

<style scoped>
.setting-card {
  background: #fff;
  border: 1px solid #d8dee4;
  border-radius: 14px;
  overflow: hidden;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.setting-card:hover {
  border-color: #2da44e40;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
}
.card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f2f5;
  background: #fafbfc;
}
.card-icon {
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.card-title { font-size: 16px; font-weight: 700; color: #1f2328; }
.card-desc { font-size: 12px; color: #8b949e; margin-top: 1px; }
.card-body { padding: 20px 24px; }
.form-row { margin-bottom: 16px; }
.form-row:last-child { margin-bottom: 0; }
.form-row.two-col { display: flex; gap: 16px; }
.form-row.two-col .form-item { flex: 1; min-width: 0; }
.form-item { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 13px; font-weight: 600; color: #1f2328; }
.form-hint { font-size: 12px; color: #8b949e; margin-top: 2px; }

@media (max-width: 767px) {
  .form-row.two-col { flex-direction: column; gap: 16px; }
  .card-header { padding: 16px 18px; }
  .card-body { padding: 16px 18px; }
}
</style>
