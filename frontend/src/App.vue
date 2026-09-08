<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, Upload, User, Key, Menu as MenuIcon, Document, Connection, Fold, Expand, SwitchButton } from '@element-plus/icons-vue'
import { useAuth } from './stores/auth'
import { clearSession, supportedPaths } from './router'
import { api } from './api/client'
import { ElMessage } from 'element-plus'
import { navigationMenus } from './api/format.mjs'
import logo from './assets/brand/herbmoda-logo-light.webp'
const auth = useAuth(), route = useRoute(), router = useRouter(), collapsed = ref(false), loggingOut = ref(false)
const publicPage = computed(() => ['/login','/force-change-password'].includes(route.path))
const icons: Record<string, unknown> = { DataBoard, Upload, User, Key, Menu: MenuIcon, Document, Connection }
const fallback: Record<string,string> = { '/dashboard':'DataBoard','/data-import':'Upload','/admin/users':'User','/admin/roles':'Key','/admin/menus':'Menu','/admin/audit':'Document','/admin/login-logs':'Connection' }
const entries = computed(() => navigationMenus(auth.allMenus, supportedPaths, auth.isAdmin))
async function logout() { loggingOut.value = true; try { await api('/auth/logout',{method:'POST'}); clearSession(); await router.replace('/login') } catch(e) { ElMessage.error((e as Error).message) } finally { loggingOut.value = false } }
</script>
<template>
  <el-config-provider><router-view v-if="publicPage" />
  <div v-else class="app-shell" :class="{collapsed}">
    <a class="skip" href="#main">跳转到主要内容</a>
    <aside class="sidebar" aria-label="主导航">
      <div class="brand"><span class="brand-icon"><img :src="logo" alt="HERBMODA"/></span><div class="brand-copy"><strong>HERBMODA</strong><small>Commerce Intelligence</small></div></div>
      <div class="nav-section">工作空间</div>
      <nav><router-link v-for="item in entries" :key="item.id" :to="item.routePath!" :title="item.name" :aria-label="item.name"><el-icon :size="18"><component :is="icons[item.icon ?? ''] ?? icons[fallback[item.routePath!] ?? 'Document']"/></el-icon><span>{{ item.name }}</span></router-link></nav>
      <div class="sidebar-bottom"><span class="connection-dot"></span><span>内部经营系统 · V1.0</span></div>
    </aside>
    <div class="workspace">
      <header class="topbar"><div class="actions"><el-button text :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" @click="collapsed=!collapsed"><el-icon :size="18"><Expand v-if="collapsed"/><Fold v-else/></el-icon></el-button><el-breadcrumb separator="/"><el-breadcrumb-item>工作空间</el-breadcrumb-item><el-breadcrumb-item>{{ route.meta.title ?? '访问提示' }}</el-breadcrumb-item></el-breadcrumb></div><div class="user-area"><span class="avatar">{{ auth.user?.displayName?.slice(0,1) ?? '用' }}</span><div><strong>{{ auth.user?.displayName }}</strong><small>{{ auth.user?.roles.map(r=>r.roleName).join(' · ') }}</small></div><el-button text :loading="loggingOut" aria-label="退出登录" @click="logout"><el-icon><SwitchButton/></el-icon></el-button></div></header>
      <main id="main" tabindex="-1"><router-view/></main>
      <footer>跨境电商经营数据平台 <span>按业务日期统计 · 金额按市场币种展示</span></footer>
    </div>
  </div></el-config-provider>
</template>
<style scoped>
.app-shell{display:grid;grid-template-columns:208px minmax(0,1fr);min-height:100vh;background:var(--bg)}
.sidebar{position:sticky;top:0;height:100vh;background:rgba(255,255,255,.98);border-right:1px solid var(--border);display:flex;flex-direction:column;padding:24px 12px 16px}
.brand{display:flex;gap:10px;align-items:center;padding:0 12px 28px}.brand-icon{width:36px;height:36px;display:grid;place-items:center;flex-shrink:0}.brand-icon img{display:block;width:36px;height:36px;object-fit:contain;filter:brightness(0)}
.brand-copy strong{font-family:Georgia,'Times New Roman',serif;font-size:16px;letter-spacing:.12em;font-weight:600;color:#171A18}.brand-copy small{display:block;color:var(--text-secondary);margin-top:3px;font-size:9px;letter-spacing:.04em}
.nav-section{color:#929893;font-size:11px;padding:0 14px;margin-bottom:10px}nav{display:grid;gap:3px}nav a{display:flex;align-items:center;gap:12px;padding:11px 14px;color:#626A65;text-decoration:none;border-radius:6px;font-size:13px;transition:background 150ms,color 150ms;position:relative}nav a:hover{background:#F5F6F5;color:#171A18}nav a.router-link-active{background:#EEF1EF;color:#171A18;font-weight:600;box-shadow:inset 2px 0 0 #181B19}
.sidebar-bottom{margin-top:auto;padding:16px 8px 0;display:flex;gap:8px;align-items:center;color:#929893;font-size:11px}.connection-dot{width:6px;height:6px;background:var(--positive);border-radius:50%}
.workspace{min-width:0}.topbar{height:64px;background:rgba(255,255,255,.98);border-bottom:1px solid var(--border);padding:0 24px;display:flex;justify-content:space-between;align-items:center}.topbar :deep(.el-button.is-text){color:#626A65}.topbar :deep(.el-button.is-text:hover){background:#F5F6F5;color:#171A18}
.user-area{display:flex;align-items:center;gap:10px;font-size:12px}.user-area small{display:block;font-size:10px;margin-top:3px;color:var(--text-secondary)}.avatar{display:grid;place-items:center;width:32px;height:32px;background:#181B19;color:#fff;border:1px solid #181B19;border-radius:50%;font-size:13px;font-weight:600}
main{padding:24px;min-height:calc(100vh - 112px)}footer{padding:0 24px 16px;display:flex;justify-content:space-between;font-size:11px;color:#929893}
.collapsed{grid-template-columns:72px minmax(0,1fr)}.collapsed .brand{padding:0 5px 28px}.collapsed .brand-copy,.collapsed nav span,.collapsed .nav-section,.collapsed .sidebar-bottom{display:none}.collapsed nav a{justify-content:center;padding:14px 0}.collapsed nav a.router-link-active{box-shadow:inset 2px 0 0 #181B19}
@media(max-width:1100px){.app-shell{grid-template-columns:72px minmax(0,1fr)}.brand-copy,.nav-section,.sidebar-bottom,nav span{display:none}.brand{padding:0 5px 28px}nav a{justify-content:center;padding:14px 0}main{padding:16px}.topbar{padding:0 16px}}
@media(max-width:600px){.app-shell{grid-template-columns:52px minmax(0,1fr)}.sidebar{padding:16px 4px}.brand{padding:0 3px 24px}.brand-icon,.brand-icon img{width:32px;height:32px}.topbar{padding:0 8px}.user-area div,.user-area .avatar,.topbar .el-button:first-child{display:none}.el-breadcrumb{font-size:11px}main{padding:12px}footer{padding:0 12px 12px}footer span{display:none}}
</style>
