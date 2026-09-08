<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, AriaComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([BarChart,LineChart,PieChart,GridComponent,TooltipComponent,LegendComponent,AriaComponent,CanvasRenderer])
const props = withDefaults(defineProps<{ option: echarts.EChartsCoreOption; label: string; height?: number }>(),{height:220})
const element=ref<HTMLDivElement>(); let chart: echarts.EChartsType | undefined, observer:ResizeObserver
function render(){
  chart?.setOption({
    animation:!matchMedia('(prefers-reduced-motion: reduce)').matches,
    animationDuration:320,
    color:['#5F8FBE','#AAB7C5','#D9E2EB','#355D86','#7E9FBE','#C7D3DF'],
    textStyle:{fontFamily:"-apple-system,BlinkMacSystemFont,'SF Pro Text','PingFang SC','Microsoft YaHei',sans-serif",fontSize:11,color:'#667085'},
    aria:{enabled:true},
    ...props.option
  },true)
}
onMounted(()=>{chart=echarts.init(element.value!);render();observer=new ResizeObserver(()=>chart?.resize());observer.observe(element.value!)})
watch(()=>props.option,render,{deep:true});onBeforeUnmount(()=>{observer?.disconnect();chart?.dispose()})
</script>
<template><div ref="element" role="img" :aria-label="label" :style="{height:height+'px',width:'100%',minWidth:0}"/></template>
