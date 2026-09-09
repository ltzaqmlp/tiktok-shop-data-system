import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth'
import { api } from '../api/client'
import type { RouteRecordRaw } from 'vue-router'
const pages: Record<string, RouteRecordRaw['component']> = {
  '/dashboard': () => import('../modules/dashboard/Dashboard.vue'), '/data-import': () => import('../modules/import/DataImport.vue'),
  '/daily-reports': () => import('../modules/daily/DailyReports.vue'),
  '/shooting-tickets': () => import('../modules/shooting/ShootingTickets.vue'),
  '/admin/users': () => import('../modules/admin/Users.vue'), '/admin/roles': () => import('../modules/admin/Roles.vue'),
  '/admin/menus': () => import('../modules/admin/Menus.vue'), '/admin/sku-config': () => import('../modules/admin/SkuConfig.vue'), '/admin/audit': () => import('../modules/admin/Logs.vue'), '/admin/login-logs': () => import('../modules/admin/Logs.vue'),
}
export const supportedPaths = Object.keys(pages)
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/login', component: () => import('../modules/Auth.vue') },
  { path: '/force-change-password', component: () => import('../modules/Auth.vue') },
  { path: '/:pathMatch(.*)*', component: () => import('../modules/Unavailable.vue') },
] })
router.beforeEach(async to => {
  const auth = useAuth()
  if (!auth.ready && to.path !== '/login') { try { if (!(await api<{authenticated:boolean}>('/auth/session')).authenticated) return { path: '/login', query: { redirect: to.fullPath } }; await auth.load() } catch { return { path: '/login', query: { redirect: to.fullPath } } } }
  if (!auth.user) return to.path === '/login' ? true : '/login'
  if (auth.user.mustChangePassword) return to.path === '/force-change-password' ? true : '/force-change-password'
  let added = false
  for (const menu of auth.allMenus) {
    const path = menu.routePath
    if (path && pages[path] && (!path.startsWith('/admin/') || auth.isAdmin) && !router.hasRoute(path)) { router.addRoute({ path, name: path, component: pages[path], meta: { title: menu.name } }); added = true }
  }
  if (['/', '/login', '/force-change-password'].includes(to.path)) return '/dashboard'
  if (pages[to.path] && !auth.allMenus.some(m => m.routePath === to.path)) return '/no-access'
  if (to.path.startsWith('/admin/') && !auth.isAdmin) return '/no-access'
  if (added && pages[to.path]) return to.fullPath
  return true
})
export function clearSession() { useAuth().clear(); for (const path of Object.keys(pages)) if (router.hasRoute(path)) router.removeRoute(path) }
window.addEventListener('session-expired', () => { clearSession(); if (router.currentRoute.value.path !== '/login') void router.replace('/login') })
window.addEventListener('password-required', () => { const auth = useAuth(); if (auth.user) auth.user.mustChangePassword = true; void router.replace('/force-change-password') })
