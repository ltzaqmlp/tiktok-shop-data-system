import type { Metric } from '../../api/types'
export const tooltip = {
  trigger:'axis',
  backgroundColor:'#FFFFFF',
  borderColor:'#E5E9E6',
  borderWidth:1,
  padding:[9,11],
  textStyle:{color:'#171A18',fontSize:12},
  extraCssText:'box-shadow:0 8px 24px rgba(20,24,21,.06);border-radius:8px;',
  confine:true
}
export function spark(metric:Metric | undefined,_color='#344B40') {
  const color='#344B40'
  return {
    grid:{left:0,right:0,top:8,bottom:0},
    tooltip:{...tooltip,trigger:'axis'},
    xAxis:{type:'category',show:false,data:metric?.trend?.map(p=>p.date)??[]},
    yAxis:{type:'value',show:false,scale:true},
    series:[{type:'line',data:metric?.trend?.map(p=>p.value)??[],showSymbol:false,symbolSize:4,lineStyle:{width:1.6,color},areaStyle:{color,opacity:0.08},connectNulls:false}]
  }
}
export function combo(rows:Record<string,any>[],bar:string,line:string,barLabel:string,lineLabel:string) {
  return {
    tooltip,
    legend:{data:[barLabel,lineLabel],bottom:0,itemWidth:10,itemHeight:8,textStyle:{color:'#7A827D',fontSize:10}},
    grid:{left:44,right:40,top:30,bottom:48},
    xAxis:{type:'category',data:rows.map(r=>r.date),axisLabel:{formatter:(v:string)=>v.slice(5),fontSize:10,color:'#7A827D'},axisTick:{show:false},axisLine:{lineStyle:{color:'#E5E9E6'}}},
    yAxis:[
      {type:'value',name:barLabel,nameTextStyle:{fontSize:10,color:'#7A827D'},splitLine:{lineStyle:{color:'#EDF0EE'}},axisLine:{show:false},axisTick:{show:false},axisLabel:{fontSize:10,color:'#7A827D'}},
      {type:'value',name:lineLabel,nameTextStyle:{fontSize:10,color:'#7A827D'},splitLine:{show:false},axisLine:{show:false},axisTick:{show:false},axisLabel:{fontSize:10,color:'#7A827D'}}
    ],
    series:[
      {name:barLabel,type:'bar',data:rows.map(r=>r[bar]),barMaxWidth:14,itemStyle:{borderRadius:[2,2,0,0],color:'#344B40'}},
      {name:lineLabel,type:'line',yAxisIndex:1,data:rows.map(r=>r[line]),symbolSize:4,lineStyle:{width:1.8,color:'#AEB6B1'},itemStyle:{color:'#AEB6B1'}}
    ]
  }
}
