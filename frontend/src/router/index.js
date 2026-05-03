import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '仪表盘' } },
      { path: 'analyze', name: 'analyze', component: () => import('../views/AnalyzeView.vue'), meta: { title: '仓库分析' } },
      { path: 'analyze/:taskId', name: 'analyze-detail', component: () => import('../views/AnalyzeView.vue'), meta: { title: '分析结果' } },
      { path: 'chat', name: 'chat', component: () => import('../views/ChatView.vue'), meta: { title: '代码问答' } },
      { path: 'review', name: 'review', component: () => import('../views/CodeReviewView.vue'), meta: { title: 'AI 代码审查' } },
      { path: 'compare', name: 'compare', component: () => import('../views/CompareView.vue'), meta: { title: '报告对比' } },
      { path: 'trending', name: 'trending', component: () => import('../views/TrendingView.vue'), meta: { title: '热点推送' } },
      { path: 'history', name: 'history', component: () => import('../views/HistoryView.vue'), meta: { title: '分析历史' } },
      { path: 'github', name: 'github', component: () => import('../views/GitHubView.vue'), meta: { title: '我的 GitHub' } },
      { path: 'settings', name: 'settings', component: () => import('../views/SettingsView.vue'), meta: { title: '系统设置' } },
      { path: 'explorer/:taskId', name: 'explorer', component: () => import('../components/explorer/CodeExplorer.vue'), meta: { title: '代码浏览器' } }
    ]
  }
]

routes.push({ path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/DashboardView.vue'), meta: { title: '404' } })

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.afterEach((to) => {
  document.title = to.meta.title ? to.meta.title + ' - CodeXray' : 'CodeXray'
})

export default router
