<script setup lang="ts">
import { ApiError } from '../api/client'
defineProps<{ error: Error | null }>(); defineEmits<{ retry: [] }>()
</script>
<template><el-alert v-if="error" type="error" :closable="false" show-icon role="alert"><template #title>{{ error.message }}</template><div class="actions"><small v-if="error instanceof ApiError && error.requestId">请求编号：{{ error.requestId }}</small><el-button text type="primary" size="small" @click="$emit('retry')">重新加载</el-button></div></el-alert></template>
