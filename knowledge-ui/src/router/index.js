import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'Register', component: () => import('../views/Register.vue') },
  { path: '/', name: 'Docs', component: () => import('../views/DocList.vue') },
  { path: '/search', name: 'Search', component: () => import('../views/SearchView.vue') },
  { path: '/agent', name: 'Agent', component: () => import('../views/AgentChat.vue') },
  { path: '/audit', name: 'Audit', component: () => import('../views/AuditView.vue') }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  if (to.path !== '/login' && to.path !== '/register' && !localStorage.getItem('token')) {
    return '/login'
  }
})

export default router
