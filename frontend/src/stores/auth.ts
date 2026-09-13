import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '../api/client'
import type { User, Menu } from '../api/types'
import { flattenMenus } from '../api/format.mjs'
export const useAuth = defineStore('auth', () => {
  const user = ref<User | null>(null), menus = ref<Menu[]>([]), ready = ref(false)
  const isAdmin = computed(() => user.value?.roles.some(r => ['ADMIN', 'BOSS'].includes(r.roleCode)) ?? false)
  const canManage = computed(() => isAdmin.value)
  const allMenus = computed<Menu[]>(() => flattenMenus(menus.value).filter((m: Menu) => m.enabled))
  async function load() { user.value = await api<User>('/auth/me'); menus.value = user.value.mustChangePassword ? [] : await api<Menu[]>('/me/menus'); ready.value = true }
  function clear() { user.value = null; menus.value = []; ready.value = false }
  function can(permission: string) { return isAdmin.value || !!user.value?.permissions?.includes(permission) }
  return { user, menus, ready, isAdmin, canManage, allMenus, load, clear, can }
})
