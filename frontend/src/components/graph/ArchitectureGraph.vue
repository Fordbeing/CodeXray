<template>
  <div class="arch-graph" ref="containerRef">
    <div class="graph-toolbar">
      <span class="graph-title">架构依赖图</span>
      <div class="graph-controls">
        <el-button size="small" text @click="resetZoom">
          <el-icon><RefreshRight /></el-icon>
        </el-button>
      </div>
    </div>
    <div class="graph-legend">
      <span v-for="(color, cat) in categoryColors" :key="cat" class="legend-item">
        <span class="legend-dot" :style="{ background: color }"></span>
        {{ cat }}
      </span>
    </div>
    <div v-if="!graphData || !graphData.nodes || graphData.nodes.length === 0" class="graph-empty">
      <el-icon :size="32" color="#d0d7de"><DataAnalysis /></el-icon>
      <p>暂无架构数据</p>
    </div>
    <svg
      v-if="graphData && graphData.nodes && graphData.nodes.length > 0"
      ref="svgRef"
      :width="svgWidth"
      :height="svgHeight"
      @mousedown="onSvgMouseDown"
      @mousemove="onSvgMouseMove"
      @mouseup="onSvgMouseUp"
      @mouseleave="onSvgMouseUp"
      @wheel.prevent="onWheel"
      @touchstart.prevent="onTouchStart"
    >
      <g :transform="`translate(${pan.x}, ${pan.y}) scale(${zoom})`">
        <!-- 边 -->
        <g v-for="edge in layoutEdges" :key="edge.id">
          <line
            :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
            :stroke="highlightEdge(edge) ? '#2da44e' : '#d0d7de'"
            :stroke-width="Math.max(1, Math.min(edge.weight, 4))"
            :opacity="hoveredNode && !highlightEdge(edge) ? 0.15 : 0.6"
          />
          <polygon
            :points="arrowPoints(edge)"
            :fill="highlightEdge(edge) ? '#2da44e' : '#d0d7de'"
            :opacity="hoveredNode && !highlightEdge(edge) ? 0.15 : 0.6"
          />
          <!-- 边的 tooltip -->
          <title v-if="edge.matchedSymbols?.length">{{ edge.source }} -> {{ edge.target }}: {{ edge.matchedSymbols.slice(0, 5).join(', ') }}</title>
        </g>
        <!-- 节点 -->
        <g
          v-for="node in layoutNodes"
          :key="node.id"
          :transform="`translate(${node.x}, ${node.y})`"
          class="graph-node"
          @mouseenter="hoveredNode = node.id"
          @mouseleave="hoveredNode = null"
          @click="selectNode(node)"
        >
          <rect
            :x="-node.w / 2"
            :y="-node.h / 2"
            :width="node.w"
            :height="node.h"
            rx="8"
            ry="8"
            :fill="node.color"
            :opacity="hoveredNode && hoveredNode !== node.id ? 0.3 : 1"
            :stroke="hoveredNode === node.id || selectedNode?.id === node.id ? '#fff' : 'none'"
            :stroke-width="2"
          />
          <text
            text-anchor="middle"
            :y="-6"
            fill="#fff"
            font-size="13"
            font-weight="700"
          >{{ node.label }}</text>
          <text
            text-anchor="middle"
            :y="10"
            fill="rgba(255,255,255,0.85)"
            font-size="10"
          >{{ node.fileCount }} 文件 &middot; {{ node.chunkCount }} 片段</text>
        </g>
      </g>
    </svg>

    <!-- 节点详情面板 -->
    <transition name="slide">
      <div v-if="selectedNode" class="node-detail">
        <div class="detail-header">
          <span class="detail-title" :style="{ color: selectedNode.color }">{{ selectedNode.label }}</span>
          <el-button text size="small" @click="selectedNode = null">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>

        <!-- 统计概览 -->
        <div class="detail-stats">
          <div class="stat-card">
            <span class="stat-num">{{ selectedNode.fileCount }}</span>
            <span class="stat-label">文件</span>
          </div>
          <div class="stat-card">
            <span class="stat-num">{{ selectedNode.chunkCount }}</span>
            <span class="stat-label">代码片段</span>
          </div>
          <div class="stat-card">
            <span class="stat-num">{{ nodeIncoming(selectedNode.id) }}</span>
            <span class="stat-label">被依赖</span>
          </div>
          <div class="stat-card">
            <span class="stat-num">{{ nodeOutgoing(selectedNode.id) }}</span>
            <span class="stat-label">依赖于</span>
          </div>
        </div>

        <!-- 依赖关系 -->
        <div v-if="nodeEdges(selectedNode.id).length > 0" class="detail-section">
          <div class="section-title">依赖关系</div>
          <div v-for="edge in nodeEdges(selectedNode.id)" :key="edge.id" class="edge-item">
            <div class="edge-header">
              <span class="edge-dir" v-if="edge.source === selectedNode.id">
                <span class="arrow-out">→</span>
                <span class="edge-target" :style="{ color: getEdgeNodeColor(edge.target) }">{{ edge.target }}</span>
              </span>
              <span class="edge-dir" v-else>
                <span class="arrow-in">←</span>
                <span class="edge-target" :style="{ color: getEdgeNodeColor(edge.source) }">{{ edge.source }}</span>
              </span>
              <span class="edge-weight">{{ edge.weight }} 次引用</span>
            </div>
            <div v-if="edge.matchedSymbols?.length" class="edge-symbols">
              <span v-for="sym in edge.matchedSymbols.slice(0, 6)" :key="sym" class="symbol-tag">{{ sym }}</span>
              <span v-if="edge.matchedSymbols.length > 6" class="more-syms">+{{ edge.matchedSymbols.length - 6 }}</span>
            </div>
          </div>
        </div>

        <!-- 核心符号 -->
        <div v-if="selectedNode.symbols?.length" class="detail-section">
          <div class="section-title">核心符号 ({{ selectedNode.symbols.length }})</div>
          <div class="symbol-cloud">
            <span v-for="sym in selectedNode.symbols" :key="sym" class="symbol-tag">{{ sym }}</span>
          </div>
        </div>

        <!-- 文件列表 -->
        <div class="detail-section">
          <div class="section-title">文件列表 ({{ selectedNode.filePaths?.length || 0 }})</div>
          <div class="detail-files">
            <div
              v-for="fp in selectedNode.filePaths"
              :key="fp"
              class="detail-file"
              @click="$emit('fileClick', fp)"
            >
              <el-icon :size="14"><Document /></el-icon>
              <div class="file-info">
                <span class="file-name">{{ fp.split('/').pop() }}</span>
                <span class="file-path">{{ fp }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { RefreshRight, Close, Document, DataAnalysis } from '@element-plus/icons-vue'

const props = defineProps({
  graphData: { type: Object, default: null }
})

defineEmits(['nodeClick', 'fileClick'])

const categoryColors = {
  controller: '#3b82f6',
  service: '#2da44e',
  model: '#d29922',
  config: '#8b5cf6',
  data: '#ef4444',
  util: '#6b7280',
  test: '#f97316',
  source: '#656d76'
}

const containerRef = ref(null)
const svgRef = ref(null)
const svgWidth = ref(800)
const svgHeight = ref(500)
const hoveredNode = ref(null)
const selectedNode = ref(null)

const zoom = ref(1)
const pan = ref({ x: 0, y: 0 })
let isPanning = false
let panStart = { x: 0, y: 0 }

const layoutNodes = ref([])
const layoutEdges = ref([])

function getColor(category) {
  return categoryColors[category] || categoryColors.source
}

function selectNode(node) {
  selectedNode.value = selectedNode.value?.id === node.id ? null : node
}

// 计算某节点的入边/出边数量
function nodeIncoming(nodeId) {
  return layoutEdges.value.filter(e => e.target === nodeId).length
}
function nodeOutgoing(nodeId) {
  return layoutEdges.value.filter(e => e.source === nodeId).length
}

// 获取某节点相关的所有边
function nodeEdges(nodeId) {
  return layoutEdges.value.filter(e => e.source === nodeId || e.target === nodeId)
}

function getEdgeNodeColor(nodeId) {
  const node = layoutNodes.value.find(n => n.id === nodeId)
  return node?.color || '#656d76'
}

function initLayout() {
  if (!props.graphData?.nodes) return

  const nodes = props.graphData.nodes.map((n, i) => {
    const angle = (2 * Math.PI * i) / props.graphData.nodes.length
    const radius = 150
    return {
      id: n.id,
      label: n.label,
      category: n.category,
      fileCount: n.fileCount,
      filePaths: n.filePaths || [],
      symbols: n.symbols || [],
      chunkCount: n.chunkCount || 0,
      color: getColor(n.category),
      w: Math.max(100, n.label.length * 12 + 50),
      h: 48,
      x: svgWidth.value / 2 + Math.cos(angle) * radius,
      y: svgHeight.value / 2 + Math.sin(angle) * radius,
      vx: 0,
      vy: 0
    }
  })

  const edges = (props.graphData.edges || []).map(e => ({
    id: `${e.source}-${e.target}`,
    source: e.source,
    target: e.target,
    weight: e.weight || 1,
    matchedSymbols: e.matchedSymbols || []
  }))

  layoutNodes.value = nodes
  layoutEdges.value = edges

  runSimulation()
}

function runSimulation() {
  const nodes = layoutNodes.value
  const edges = layoutEdges.value
  const iterations = 80

  for (let iter = 0; iter < iterations; iter++) {
    const alpha = 1 - iter / iterations

    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const dx = nodes[j].x - nodes[i].x
        const dy = nodes[j].y - nodes[i].y
        const dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1)
        const force = (3000 / (dist * dist)) * alpha
        const fx = (dx / dist) * force
        const fy = (dy / dist) * force
        nodes[i].x -= fx
        nodes[i].y -= fy
        nodes[j].x += fx
        nodes[j].y += fy
      }
    }

    for (const edge of edges) {
      const source = nodes.find(n => n.id === edge.source)
      const target = nodes.find(n => n.id === edge.target)
      if (!source || !target) continue
      const dx = target.x - source.x
      const dy = target.y - source.y
      const dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1)
      const force = (dist - 180) * 0.03 * alpha
      const fx = (dx / dist) * force
      const fy = (dy / dist) * force
      source.x += fx
      source.y += fy
      target.x -= fx
      target.y -= fy
    }

    const cx = nodes.reduce((s, n) => s + n.x, 0) / nodes.length
    const cy = nodes.reduce((s, n) => s + n.y, 0) / nodes.length
    const offsetX = svgWidth.value / 2 - cx
    const offsetY = svgHeight.value / 2 - cy
    for (const n of nodes) {
      n.x += offsetX * 0.1
      n.y += offsetY * 0.1
    }
  }
}

function edgeNode(edge) {
  const source = layoutNodes.value.find(n => n.id === edge.source)
  const target = layoutNodes.value.find(n => n.id === edge.target)
  return { source, target }
}

function updateEdgePositions() {
  layoutEdges.value = layoutEdges.value.map(edge => {
    const { source, target } = edgeNode(edge)
    if (!source || !target) return edge
    return { ...edge, x1: source.x, y1: source.y, x2: target.x, y2: target.y }
  })
}

function arrowPoints(edge) {
  const { source, target } = edgeNode(edge)
  if (!source || !target) return ''
  const dx = target.x - source.x
  const dy = target.y - source.y
  const dist = Math.sqrt(dx * dx + dy * dy)
  if (dist === 0) return ''
  const nx = dx / dist
  const ny = dy / dist
  const ax = target.x - nx * (target.w / 2 + 4)
  const ay = target.y - ny * (target.h / 2 + 4)
  const size = 6
  const px = -ny
  const py = nx
  return `${ax},${ay} ${ax - nx * size + px * size / 2},${ay - ny * size + py * size / 2} ${ax - nx * size - px * size / 2},${ay - ny * size - py * size / 2}`
}

function highlightEdge(edge) {
  if (!hoveredNode.value && !selectedNode.value) return false
  const id = hoveredNode.value || selectedNode.value?.id
  return edge.source === id || edge.target === id
}

function onWheel(e) {
  const delta = e.deltaY > 0 ? 0.9 : 1.1
  zoom.value = Math.max(0.3, Math.min(3, zoom.value * delta))
}

function onSvgMouseDown(e) {
  if (e.target.closest('.graph-node')) return
  isPanning = true
  panStart = { x: e.clientX - pan.value.x, y: e.clientY - pan.value.y }
}

function onSvgMouseMove(e) {
  if (!isPanning) return
  pan.value = { x: e.clientX - panStart.x, y: e.clientY - panStart.y }
}

function onSvgMouseUp() {
  isPanning = false
}

function onTouchStart(e) {
  if (e.touches.length === 1) {
    const t = e.touches[0]
    isPanning = true
    panStart = { x: t.clientX - pan.value.x, y: t.clientY - pan.value.y }
  }
}

function resetZoom() {
  zoom.value = 1
  pan.value = { x: 0, y: 0 }
}

function onResize() {
  if (containerRef.value) {
    svgWidth.value = containerRef.value.clientWidth
    svgHeight.value = Math.max(400, Math.min(600, window.innerHeight * 0.5))
  }
}

watch(() => props.graphData, () => {
  nextTick(() => {
    onResize()
    initLayout()
    updateEdgePositions()
    selectedNode.value = null
  })
}, { immediate: true })

onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.arch-graph {
  position: relative;
  border: 1px solid #e8ecf0;
  border-radius: 10px;
  overflow: hidden;
  background: #fafbfc;
}

.graph-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #e8ecf0;
  background: #fff;
}

.graph-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2328;
}

.graph-legend {
  display: flex;
  gap: 14px;
  padding: 8px 16px;
  border-bottom: 1px solid #f0f2f5;
  flex-wrap: wrap;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #656d76;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

svg {
  display: block;
  cursor: grab;
}

svg:active {
  cursor: grabbing;
}

.graph-empty {
  text-align: center;
  padding: 60px 20px;
  color: #8b949e;
}

.graph-empty p {
  margin: 12px 0 0;
  font-size: 14px;
}

.graph-node {
  cursor: pointer;
}

.graph-node rect {
  transition: opacity 0.15s;
}

/* ===== 节点详情面板 ===== */
.node-detail {
  position: absolute;
  top: 0;
  right: 0;
  width: 320px;
  height: 100%;
  background: #fff;
  border-left: 1px solid #e8ecf0;
  overflow-y: auto;
  z-index: 10;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f2f5;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 1;
}

.detail-title {
  font-size: 16px;
  font-weight: 700;
}

/* 统计卡片 */
.detail-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f5;
}

.stat-card {
  text-align: center;
  padding: 8px 0;
  background: #f8faf9;
  border-radius: 6px;
}

.stat-num {
  display: block;
  font-size: 18px;
  font-weight: 800;
  color: #1f2328;
}

.stat-label {
  font-size: 11px;
  color: #8b949e;
}

/* 通用 section */
.detail-section {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f5;
}

.section-title {
  font-size: 12px;
  font-weight: 700;
  color: #656d76;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 10px;
}

/* 依赖关系 */
.edge-item {
  margin-bottom: 10px;
  padding: 8px 10px;
  background: #f8faf9;
  border-radius: 6px;
}

.edge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.edge-dir {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}

.arrow-out { color: #3b82f6; }
.arrow-in { color: #2da44e; }

.edge-target {
  font-weight: 600;
}

.edge-weight {
  font-size: 11px;
  color: #8b949e;
}

.edge-symbols {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

/* 符号标签 */
.symbol-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.symbol-tag {
  display: inline-block;
  font-size: 11px;
  font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
  color: #3b82f6;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  padding: 1px 6px;
}

.more-syms {
  font-size: 11px;
  color: #8b949e;
}

/* 文件列表 */
.detail-files {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-file {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.1s;
}

.detail-file:hover {
  background: #f6f8fa;
}

.file-info {
  min-width: 0;
  flex: 1;
}

.file-name {
  font-size: 13px;
  color: #1f2328;
  font-weight: 500;
  display: block;
}

.file-path {
  font-size: 11px;
  color: #8b949e;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.2s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}

:global(html.dark) .arch-graph {
  background: #2d333b;
  border-color: #373e47;
}

:global(html.dark) .graph-toolbar {
  background: #22272e;
  border-bottom-color: #373e47;
}

:global(html.dark) .graph-title {
  color: #e6edf3;
}

:global(html.dark) .node-detail {
  background: #22272e;
  border-left-color: #373e47;
}

:global(html.dark) .detail-header {
  background: #22272e;
}

:global(html.dark) .detail-file .file-name {
  color: #e6edf3;
}

:global(html.dark) .stat-num {
  color: #e6edf3;
}

:global(html.dark) .edge-item {
  background: #2d333b;
}

:global(html.dark) .stat-card {
  background: #2d333b;
}
</style>
