<script setup lang="ts">
import { computed } from 'vue'
import { changeTone, number } from '../api/format.mjs'
import type { CompareStatus } from '../api/types'
const props=withDefaults(defineProps<{ value?: number|null; status?: CompareStatus; direction?: string; label?: string }>(),{direction:'higher',label:''})
const reasons:Record<string,string>={NO_HISTORY:'无历史数据',ZERO_BASELINE:'基线为 0',CURRENT_MISSING:'当前无数据',CURRENT_PARTIAL:'当前数据不完整',HISTORY_PARTIAL:'历史数据不完整',VALUE_UNAVAILABLE:'无法计算',NOT_APPLICABLE:'—',PERIOD_TOTAL:'区间总计，无法环比'}
const display=computed(()=>props.value==null?(reasons[props.status??'']??'—'):`${props.value>0?'↑':props.value<0?'↓':'—'} ${number(Math.abs(props.value)*100,1)}%`)
</script>
<template><div class="metric-change number"><span :class="changeTone(value,direction)">{{ display }}</span><span class="muted">{{ label }}</span></div></template>
<style scoped>.metric-change{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:11px;line-height:22px}.metric-change>span:first-child{font-weight:550;white-space:nowrap}.metric-change .muted{font-size:10px}</style>
