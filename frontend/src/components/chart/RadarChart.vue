<template>
  <div class="radar-chart">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
      <!-- 背景网格 -->
      <g v-for="level in [40, 60, 80, 100]" :key="level">
        <polygon
          :points="polygonPoints(level)"
          fill="none"
          :stroke="level === 100 ? '#d0d7de' : '#e8ecf0'"
          :stroke-width="level === 100 ? 1.5 : 1"
        />
        <text
          :x="center + 4"
          :y="center - (level / 100 * radius) + 4"
          font-size="10"
          fill="#8b949e"
        >{{ level }}</text>
      </g>

      <!-- 轴线 -->
      <line
        v-for="(dim, i) in dimensions"
        :key="'axis-' + i"
        :x1="center"
        :y1="center"
        :x2="vertexX(i)"
        :y2="vertexY(i)"
        stroke="#e8ecf0"
        stroke-width="1"
      />

      <!-- 数据多边形 -->
      <polygon
        v-for="(ds, di) in allDatasets"
        :key="'data-' + di"
        :points="dataPolygon(ds.values)"
        :fill="ds.color"
        :fill-opacity="hoveredDim !== null ? 0.15 : 0.2"
        :stroke="ds.color"
        stroke-width="2"
        class="data-polygon"
      />

      <!-- 数据点 -->
      <g v-for="(ds, di) in allDatasets" :key="'dots-' + di">
        <circle
          v-for="(val, i) in ds.values"
          :key="'dot-' + di + '-' + i"
          :cx="vertexPos(i, val).x"
          :cy="vertexPos(i, val).y"
          r="4"
          :fill="ds.color"
          stroke="#fff"
          stroke-width="2"
          class="data-dot"
          @mouseenter="hoveredDim = i"
          @mouseleave="hoveredDim = null"
        />
      </g>

      <!-- 维度标签 -->
      <text
        v-for="(dim, i) in dimensions"
        :key="'label-' + i"
        :x="labelPos(i).x"
        :y="labelPos(i).y"
        :text-anchor="labelAnchor(i)"
        :dominant-baseline="labelBaseline(i)"
        font-size="12"
        font-weight="500"
        fill="#1f2328"
      >{{ dimLabels[dim] || dim }}</text>

      <!-- 悬浮详情 -->
      <g v-if="hoveredDim !== null">
        <rect
          :x="tooltipX"
          :y="tooltipY"
          :width="tooltipWidth"
          :height="tooltipHeight"
          rx="6"
          fill="#1f2328"
          fill-opacity="0.9"
        />
        <text
          :x="tooltipX + 10"
          :y="tooltipY + 18"
          fill="#fff"
          font-size="12"
          font-weight="600"
        >{{ dimLabels[dimensions[hoveredDim]] || dimensions[hoveredDim] }}</text>
        <text
          v-for="(ds, i) in allDatasets"
          :key="'tt-' + i"
          :x="tooltipX + 10"
          :y="tooltipY + 34 + i * 16"
          :fill="ds.color"
          font-size="11"
        >{{ ds.label }}: {{ ds.values[hoveredDim] }}</text>
      </g>
    </svg>

    <!-- 图例 -->
    <div v-if="allDatasets.length > 1" class="chart-legend">
      <span v-for="ds in allDatasets" :key="ds.label" class="legend-item">
        <span class="legend-color" :style="{ background: ds.color }"></span>
        {{ ds.label }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  scores: { type: Object, default: null },
  compareScores: { type: Object, default: null },
  size: { type: Number, default: 320 }
})

const hoveredDim = ref(null)

const dimensions = ['codeQuality', 'structure', 'documentation', 'testing', 'dependencies', 'security', 'performance', 'maintainability']

const dimLabels = {
  codeQuality: '代码质量',
  structure: '项目结构',
  documentation: '文档',
  testing: '测试',
  dependencies: '依赖管理',
  security: '安全性',
  performance: '性能',
  maintainability: '可维护性'
}

const colors = ['#2da44e', '#3b82f6', '#d29922']

const center = computed(() => props.size / 2)
const radius = computed(() => props.size / 2 - 50)

const allDatasets = computed(() => {
  const datasets = []
  if (props.scores) {
    datasets.push({
      label: '当前报告',
      color: colors[0],
      values: dimensions.map(d => props.scores[d] || 0)
    })
  }
  if (props.compareScores) {
    datasets.push({
      label: '对比报告',
      color: colors[1],
      values: dimensions.map(d => props.compareScores[d] || 0)
    })
  }
  return datasets
})

function vertexX(i) {
  const angle = (Math.PI * 2 * i) / dimensions.length - Math.PI / 2
  return center.value + Math.cos(angle) * radius.value
}

function vertexY(i) {
  const angle = (Math.PI * 2 * i) / dimensions.length - Math.PI / 2
  return center.value + Math.sin(angle) * radius.value
}

function vertexPos(i, val) {
  const angle = (Math.PI * 2 * i) / dimensions.length - Math.PI / 2
  const clamped = Math.max(0, Math.min(100, val))
  const r = (clamped / 100) * radius.value
  return {
    x: center.value + Math.cos(angle) * r,
    y: center.value + Math.sin(angle) * r
  }
}

function polygonPoints(level) {
  return dimensions.map((_, i) => {
    const angle = (Math.PI * 2 * i) / dimensions.length - Math.PI / 2
    const r = (level / 100) * radius.value
    return `${center.value + Math.cos(angle) * r},${center.value + Math.sin(angle) * r}`
  }).join(' ')
}

function dataPolygon(values) {
  return values.map((val, i) => {
    const pos = vertexPos(i, val)
    return `${pos.x},${pos.y}`
  }).join(' ')
}

function labelPos(i) {
  const angle = (Math.PI * 2 * i) / dimensions.length - Math.PI / 2
  const r = radius.value + 22
  return {
    x: center.value + Math.cos(angle) * r,
    y: center.value + Math.sin(angle) * r
  }
}

function labelAnchor(i) {
  const angle = (Math.PI * 2 * i) / dimensions.length - Math.PI / 2
  const cos = Math.cos(angle)
  if (Math.abs(cos) < 0.1) return 'middle'
  return cos > 0 ? 'start' : 'end'
}

function labelBaseline(i) {
  const angle = (Math.PI * 2 * i) / dimensions.length - Math.PI / 2
  const sin = Math.sin(angle)
  if (sin < -0.5) return 'auto'
  if (sin > 0.5) return 'hanging'
  return 'middle'
}

const tooltipX = computed(() => {
  if (hoveredDim.value === null) return 0
  return vertexX(hoveredDim.value) + 10
})

const tooltipY = computed(() => {
  if (hoveredDim.value === null) return 0
  return vertexY(hoveredDim.value) - 20
})

const tooltipWidth = computed(() => 140)
const tooltipHeight = computed(() => 24 + allDatasets.value.length * 16)
</script>

<style scoped>
.radar-chart {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.data-polygon {
  transition: fill-opacity 0.2s;
}

.data-dot {
  transition: r 0.15s;
  cursor: pointer;
}

.data-dot:hover {
  r: 6;
}

.chart-legend {
  display: flex;
  gap: 16px;
  margin-top: 8px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #656d76;
}

.legend-color {
  width: 12px;
  height: 4px;
  border-radius: 2px;
}

:global(html.dark) text {
  fill: #e6edf3;
}

:global(html.dark) line,
:global(html.dark) polygon {
  stroke: #444c56;
}
</style>
