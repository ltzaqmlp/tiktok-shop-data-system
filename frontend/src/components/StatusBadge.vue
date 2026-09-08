<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ status: string | boolean }>()
const map: Record<string,[string,'success'|'danger'|'warning'|'info'|'primary']> = { true:['成功','success'],false:['失败','danger'],ACTIVE:['正常','success'],DISABLED:['停用','info'],LOCKED:['已锁定','warning'],CREATED:['已创建','info'],VALIDATING:['校验中','info'],IMPORTING:['导入中','info'],AGGREGATING:['聚合中','info'],SUCCESS:['成功','success'],FAILED:['失败','danger'],AGGREGATE_FAILED:['聚合失败','warning'] }
const badge = computed(() => map[String(props.status)] ?? [String(props.status),'info'])
</script>
<template><el-tag :type="badge[1] as any" size="small" effect="light">{{ badge[0] }}</el-tag></template>
