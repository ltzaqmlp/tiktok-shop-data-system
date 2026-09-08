<script setup lang="ts">
import { useAuth } from '../stores/auth'
const auth = useAuth()
</script>
<template><el-result icon="warning" title="页面不可访问" sub-title="此页面不存在，或您尚未获得菜单权限。请联系管理员。"><template #extra><router-link v-if="auth.allMenus.find(m=>m.routePath)" :to="auth.allMenus.find(m=>m.routePath)!.routePath!">返回已授权页面</router-link></template></el-result></template>
