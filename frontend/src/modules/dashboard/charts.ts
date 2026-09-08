import type { Metric } from '../../api/types'
export const HM_CHART = {
  text:'#667085', grid:'#E9EDF2', axis:'#D7DDE5', primary:'#5F8FBE', primaryDark:'#355D86', secondary:'#AAB7C5', secondaryLight:'#D9E2EB'
} as const
export const tooltip = {
  trigger:'axis', backgroundColor:'rgba(255,255,255,.96)', borderColor:'#E5EAF0', borderWidth:1, padding:[10,12],
  textStyle:{color:'#1E2632',fontSize:12}, extraCssText:'box-shadow:0 12px 34px rgba(26,39,55,.12);border-radius:10px;', confine:true
}
export function spark(metric:Metric | undefined,color:string=HM_CHART.primary) {
  return {
    grid:{left:0,right:0,top:8,bottom:0},
    tooltip:{...tooltip,trigger:'axis'},
    xAxis:{type:'category',show:false,data:metric?.trend?.map(p=>p.date)??[]},
    yAxis:{type:'value',show:false,scale:true},
    series:[{type:'line',data:metric?.trend?.map(p=>p.value)??[],showSymbol:false,symbolSize:4,lineStyle:{width:1.8,color},areaStyle:{color,opacity:0.08},connectNulls:false}]
  }
}
export function combo(rows:Record<string,any>[],bar:string,line:string,barLabel:string,lineLabel:string) {
  return {
    tooltip,
    legend:{data:[barLabel,lineLabel],bottom:0,itemWidth:10,itemHeight:6,textStyle:{color:HM_CHART.text,fontSize:10}},
    grid:{left:44,right:40,top:30,bottom:48},
    xAxis:{type:'category',data:rows.map(r=>r.date),axisLabel:{formatter:(v:string)=>v.slice(5),fontSize:10,color:HM_CHART.text},axisTick:{show:false},axisLine:{lineStyle:{color:HM_CHART.axis}}},
    yAxis:[
      {type:'value',name:barLabel,nameTextStyle:{fontSize:10,color:HM_CHART.text},splitLine:{lineStyle:{color:HM_CHART.grid}},axisLine:{show:false},axisTick:{show:false},axisLabel:{fontSize:10,color:HM_CHART.text}},
      {type:'value',name:lineLabel,nameTextStyle:{fontSize:10,color:HM_CHART.text},splitLine:{show:false},axisLine:{show:false},axisTick:{show:false},axisLabel:{fontSize:10,color:HM_CHART.text}}
    ],
    series:[
      {name:barLabel,type:'bar',data:rows.map(r=>r[bar]),barMaxWidth:14,itemStyle:{borderRadius:[3,3,0,0],color:HM_CHART.primary}},
      {name:lineLabel,type:'line',yAxisIndex:1,data:rows.map(r=>r[line]),symbolSize:4,lineStyle:{width:1.8,color:HM_CHART.secondary},itemStyle:{color:HM_CHART.secondary}}
    ]
  }
}
