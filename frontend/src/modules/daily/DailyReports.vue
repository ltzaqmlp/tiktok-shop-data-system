<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, query, save } from '../../api/client'
import type { DailyContext, DailyMetricReport, DailySummary, DailySummaryReview, Market } from '../../api/types'
import { useAuth } from '../../stores/auth'
import RequestError from '../../components/RequestError.vue'

type Metric = { plan: keyof DailyMetricReport; actual: keyof DailyMetricReport; label: string }
type ReportMetric = { metric: Metric; plan: number; actual: number; gap: number; rate: number; delivery: string }
type ReportRow = { report: DailyMetricReport; marketName: string; role: string; metrics: ReportMetric[] }
type SummaryMetric = { plan: keyof DailySummary; actual: keyof DailySummary; label: string }
const contentMetrics: Metric[]=[
  {label:'复盘视频',plan:'plannedReviewVideos',actual:'actualReviewVideos'},{label:'有效对标',plan:'plannedValidBenchmark',actual:'actualValidBenchmark'},
  {label:'完成拆解',plan:'plannedDeconstruction',actual:'actualDeconstruction'},{label:'完整脚本',plan:'plannedCompleteScript',actual:'actualCompleteScript'},
  {label:'可开剪脚本',plan:'plannedReadyScript',actual:'actualReadyScript'},{label:'新增发布',plan:'plannedNewPublish',actual:'actualNewPublish'},
  {label:'首次交审',plan:'plannedFirstReview',actual:'actualFirstReview'},{label:'返工验收',plan:'plannedReworkAcceptance',actual:'actualReworkAcceptance'},
]
const editorMetrics=contentMetrics.slice(5), leadMetrics=contentMetrics.slice(0,5)
const adFields: { key:keyof DailyMetricReport; label:string; money?:boolean }[]=[
  {key:'plannedTest',label:'计划测试（条）'},{key:'actualTest',label:'实际测试（条）'},{key:'testGap',label:'测试缺口（条）'},
  {key:'newAdjustPlan',label:'新建 / 调整计划（个）'},{key:'adSpend',label:'消耗',money:true},{key:'adGmv',label:'广告 GMV',money:true},
  {key:'roi',label:'ROI'},{key:'impressions',label:'展现量'},{key:'clicks',label:'点击量'},{key:'ctr',label:'CTR'},
  {key:'orders',label:'订单（单）'},{key:'expandedMaterial',label:'放大素材（条）'},{key:'stoppedMaterial',label:'停止素材（条）'},
]
const summaryMetrics: SummaryMetric[]=[
  {label:'复盘视频',plan:'plannedReviewVideos',actual:'actualReviewVideos'},{label:'有效对标',plan:'plannedValidBenchmark',actual:'actualValidBenchmark'},
  {label:'完成拆解',plan:'plannedDeconstruction',actual:'actualDeconstruction'},{label:'完整脚本',plan:'plannedCompleteScript',actual:'actualCompleteScript'},
  {label:'可开剪脚本',plan:'plannedReadyScript',actual:'actualReadyScript'},{label:'新增发布',plan:'plannedNewPublish',actual:'actualNewPublish'},
  {label:'首次交审',plan:'plannedFirstReview',actual:'actualFirstReview'},{label:'返工验收',plan:'plannedReworkAcceptance',actual:'actualReworkAcceptance'},
]
const today=()=>new Date().toLocaleDateString('en-CA'), selectedDate=ref(today()), tab=ref('summary'), adMarket=ref('MY')
const auth=useAuth(), context=ref<DailyContext>(), rows=ref<DailyMetricReport[]>([]), summaryReports=ref<DailySummary[]>([]), summaryReview=ref<DailySummaryReview>(summaryBlank()), loading=ref(false), saving=ref(false), error=ref<Error|null>(null)
const content=reactive<DailyMetricReport>(blank('EDITOR','MY')), ads=ref<Record<string,DailyMetricReport>>({})
const viewer=computed(()=>['BOSS','DEPT_HEAD','ADMIN'].some(role=>auth.user?.roles.some(item=>item.roleCode===role)))
const departmentReviewer=computed(()=>auth.isAdmin||auth.user?.roles.some(role=>role.roleCode==='DEPT_HEAD')||false)
const reviewColumn=computed(()=>departmentReviewer.value||context.value?.role==='DIRECTOR')
const contentRole=computed(()=>['EDITOR','DIRECTOR'].includes(context.value?.role??''))
const ownMarket=computed(()=>context.value?.marketCode||'MY'), currentMarket=computed(()=>tab.value.startsWith('market-')?tab.value.slice(7):ownMarket.value)
const marketTabs=computed<Market[]>(()=>context.value?.markets??[])
function blank(reportType:DailyMetricReport['reportType'],marketCode:string):DailyMetricReport{return {
  reportDate:selectedDate.value,reportType,marketCode,submissionStatus:'DRAFT',rejectionReason:'',notes:'',deliveryResults:{},submittedAt:null,plannedReviewVideos:0,actualReviewVideos:0,plannedValidBenchmark:0,actualValidBenchmark:0,plannedDeconstruction:0,actualDeconstruction:0,plannedCompleteScript:0,actualCompleteScript:0,plannedReadyScript:0,actualReadyScript:0,plannedNewPublish:0,actualNewPublish:0,plannedFirstReview:0,actualFirstReview:0,plannedReworkAcceptance:0,actualReworkAcceptance:0,plannedTest:0,actualTest:0,testGap:0,newAdjustPlan:0,adSpend:0,adGmv:0,roi:0,impressions:0,clicks:0,ctr:0,orders:0,expandedMaterial:0,stoppedMaterial:0,
 }}
function summaryBlank():DailySummaryReview{return {reportDate:selectedDate.value,todayImportantResult:'',needBossSupport:'',tomorrowFocus:'',submissionStatus:'DRAFT',rejectionReason:''}}
function assign(target:DailyMetricReport,value:DailyMetricReport){Object.assign(target,blank(value.reportType,value.marketCode),value)}
function status(value:string){return ({DRAFT:'草稿',PENDING_MARKET:'待编导审核',PENDING_DEPT:'待部门负责人审核',APPROVED:'已通过',REJECTED:'已退回'} as Record<string,string>)[value]??value}
function statusType(value:string){return ({APPROVED:'success',REJECTED:'danger',DRAFT:'info',PENDING_MARKET:'warning',PENDING_DEPT:'warning'} as Record<string,string>)[value]??'info'}
function reasonText(value:string){return value?(value.startsWith('原因：')?value:`原因：${value}`):''}
function number(value:number){return Number(value??0).toLocaleString('zh-CN',{maximumFractionDigits:2})}
function percent(value:number){return `${(Number(value??0)*100).toFixed(2)}%`}
function currencyForMarket(marketCode:string){return marketCode?marketTabs.value.find(item=>item.marketCode===marketCode)?.currencyCode??'账户币种':'各市场币种'}
function currencyLabel(field:{key:keyof DailyMetricReport;label:string},marketCode:string){return field.key==='adSpend'||field.key==='adGmv'?`${field.label}（${currencyForMarket(marketCode)}）`:field.label}
function adValue(row:DailyMetricReport,key:keyof DailyMetricReport){return key==='roi'?number(Number(row[key])):key==='ctr'?percent(Number(row[key])):number(Number(row[key]))}
function reportRows(reports:DailyMetricReport[]):ReportRow[]{return reports.map(report=>({report,marketName:report.marketName??report.marketCode,role:report.reportType==='EDITOR'?'剪辑':'编导',metrics:(report.reportType==='EDITOR'?editorMetrics:leadMetrics).map(metric=>{const plan=Number(report[metric.plan]??0),actual=Number(report[metric.actual]??0);return {metric,plan,actual,gap:Math.max(plan-actual,0),rate:plan?actual/plan:0,delivery:report.deliveryResults?.[String(metric.actual)]??''}})}))}
const marketReportRows=computed(()=>reportRows(rows.value))
const summaryTotal=computed(()=>summaryMetrics.reduce((total,metric)=>{total[metric.plan]=summaryReports.value.reduce((sum,row)=>sum+Number(row[metric.plan]??0),0);total[metric.actual]=summaryReports.value.reduce((sum,row)=>sum+Number(row[metric.actual]??0),0);return total},{} as Record<string,number>))
function submittedAt(value?:string|null){return value?new Date(value).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):'—'}
function deliveryKey(metric:Metric){return String(metric.actual)}
function contentFormMetrics(){return context.value?.role==='EDITOR'?editorMetrics:leadMetrics}
function canReview(row:DailyMetricReport){return (context.value?.role==='DIRECTOR'&&row.reportType==='EDITOR'&&row.submissionStatus==='PENDING_MARKET')||(departmentReviewer.value&&row.submissionStatus==='PENDING_DEPT')}
async function load(){
  if(!context.value)return
  loading.value=true;error.value=null
  try{
    if(tab.value==='summary'){summaryReports.value=await api<DailySummary[]>(`/daily-reports/summary?${query({date:selectedDate.value})}`);summaryReview.value=await api<DailySummaryReview>(`/daily-reports/summary/review?${query({date:selectedDate.value})}`)}
    else if(tab.value==='ads')rows.value=await api<DailyMetricReport[]>(`/daily-reports/ads?${query({date:selectedDate.value})}`)
    else if(tab.value==='content-submit')assign(content,await api<DailyMetricReport>(`/daily-reports/mine/content?${query({date:selectedDate.value})}`))
    else if(tab.value==='ads-submit'){
      const mine=await api<DailyMetricReport[]>(`/daily-reports/mine/ads?${query({date:selectedDate.value})}`);ads.value=Object.fromEntries(marketTabs.value.map(m=>[m.marketCode,blank('ADS_BUYER',m.marketCode)]));for(const row of mine)ads.value[row.marketCode]=row
    } else rows.value=await api<DailyMetricReport[]>(`/daily-reports/market/${currentMarket.value}?${query({date:selectedDate.value})}`)
  }catch(e){error.value=e as Error}finally{loading.value=false}
}
async function saveContent(submit:boolean){saving.value=true;try{assign(content,await save<DailyMetricReport>(`/daily-reports/mine/content/${selectedDate.value}${submit?'/submit':''}`,content,submit?'POST':'PUT'));ElMessage.success(submit?'日报已提交':'日报已暂存');await load()}catch(e){ElMessage.error((e as Error).message)}finally{saving.value=false}}
async function saveAds(submit:boolean){const row=ads.value[adMarket.value];if(!row)return;saving.value=true;try{ads.value[adMarket.value]=await save<DailyMetricReport>(`/daily-reports/mine/ads/${selectedDate.value}/${adMarket.value}${submit?'/submit':''}`,row,submit?'POST':'PUT');ElMessage.success(submit?'该地区投流日报已提交':'日报已暂存');await load()}catch(e){ElMessage.error((e as Error).message)}finally{saving.value=false}}
async function saveSummary(){saving.value=true;try{await save<DailySummaryReview>(`/daily-reports/summary/${selectedDate.value}`,summaryReview.value,'PUT');ElMessage.success('汇总已暂存');await load()}catch(e){ElMessage.error((e as Error).message)}finally{saving.value=false}}
async function approveSummary(){saving.value=true;try{await save<DailySummaryReview>(`/daily-reports/summary/${selectedDate.value}`,summaryReview.value,'PUT');await save<DailySummaryReview>(`/daily-reports/summary/${selectedDate.value}/approve`,{});ElMessage.success('汇总审核已通过');await load()}catch(e){ElMessage.error((e as Error).message)}finally{saving.value=false}}
async function rejectSummary(){try{const result=await ElMessageBox.prompt('请填写退回原因，编导和剪辑将看到该原因并重新填报。','退回汇总日报',{inputPattern:/\S+/,inputErrorMessage:'退回原因不能为空',confirmButtonText:'确认退回',cancelButtonText:'取消'});saving.value=true;await save<DailySummaryReview>(`/daily-reports/summary/${selectedDate.value}`,summaryReview.value,'PUT');await save<DailySummaryReview>(`/daily-reports/summary/${selectedDate.value}/reject`,{reason:result.value});ElMessage.success('汇总日报已退回');await load()}catch(e){if(e instanceof Error&&e.message!=='cancel')ElMessage.error(e.message)}finally{saving.value=false}}
async function review(row:DailyMetricReport,accept:boolean){
  try{let data:Record<string,string>={};if(!accept){const result=await ElMessageBox.prompt('请填写退回原因，提交人将看到该原因并重新填报。','退回日报',{inputPattern:/\S+/,inputErrorMessage:'退回原因不能为空',confirmButtonText:'确认退回',cancelButtonText:'取消'});data={reason:result.value}}
    await save(`/daily-reports/${row.id}/${accept?'approve':'reject'}`,data);ElMessage.success(accept?'审核已通过':'日报已退回');await load()
  }catch(e){if(e instanceof Error&&e.message!=='cancel')ElMessage.error(e.message)}
}
onMounted(async()=>{try{context.value=await api<DailyContext>('/daily-reports/context');if(!viewer.value)tab.value=context.value.role==='ADS_BUYER'?'ads':context.value.role==='DIRECTOR'?'content-submit':'market-'+ownMarket.value;await load()}catch(e){error.value=e as Error}})
watch([selectedDate,tab],()=>void load())
</script>

<template>
  <div class="page-head daily-head"><div><h1>日报</h1><p>按角色填报，审核通过后实时进入对应汇总。</p></div><label>日期<el-date-picker v-model="selectedDate" type="date" value-format="YYYY-MM-DD" :clearable="false"/></label></div>
  <RequestError :error="error" @retry="load"/>
  <el-tabs v-model="tab" class="daily-tabs">
     <el-tab-pane v-if="viewer" label="汇总" name="summary"><section class="surface table-panel"><div class="sheet-title">五区内容日报汇总 <span>按市场汇总各岗位已通过日报的计划与实际数据</span> <el-tag size="small" :type="statusType(summaryReview.submissionStatus)">{{ status(summaryReview.submissionStatus) }}</el-tag></div><el-alert v-if="summaryReview.rejectionReason" title="汇总日报已退回" :description="reasonText(summaryReview.rejectionReason)" type="error" :closable="false"/><div v-loading="loading" class="table-scroll"><table class="summary-table"><thead><tr><th rowspan="2">地区</th><th v-for="metric in summaryMetrics" :key="metric.label" colspan="2">{{ metric.label }}</th></tr><tr><template v-for="metric in summaryMetrics" :key="`${metric.label}-head`"><th>计划</th><th>实际</th></template></tr></thead><tbody><tr v-for="row in summaryReports" :key="row.marketCode"><td>{{ row.marketName }}</td><template v-for="metric in summaryMetrics" :key="`${row.marketCode}-${metric.label}`"><td>{{ number(Number(row[metric.plan])) }}</td><td>{{ number(Number(row[metric.actual])) }}</td></template></tr><tr v-if="!loading&&!summaryReports.length"><td :colspan="summaryMetrics.length*2+1" class="empty">该日期暂无已通过日报</td></tr><tr v-if="!loading&&summaryReports.length" class="summary-total"><td>合计</td><template v-for="metric in summaryMetrics" :key="`total-${metric.label}`"><td>{{ number(summaryTotal[metric.plan]) }}</td><td>{{ number(summaryTotal[metric.actual]) }}</td></template></tr></tbody></table></div><div class="summary-review"><label><span>今日最重要成果</span><el-input v-model="summaryReview.todayImportantResult" type="textarea" :rows="3" maxlength="4000" :readonly="!departmentReviewer" placeholder="请输入今日最重要成果"/></label><label><span>需老板支持</span><el-input v-model="summaryReview.needBossSupport" type="textarea" :rows="3" maxlength="4000" :readonly="!departmentReviewer" placeholder="请输入需要老板支持的事项"/></label><label><span>明日重点</span><el-input v-model="summaryReview.tomorrowFocus" type="textarea" :rows="3" maxlength="4000" :readonly="!departmentReviewer" placeholder="请输入明日重点"/></label></div><div v-if="departmentReviewer" class="summary-actions"><el-button :loading="saving" @click="saveSummary">暂存</el-button><el-button type="success" :loading="saving" @click="approveSummary">通过</el-button><el-button type="danger" :loading="saving" @click="rejectSummary">不通过</el-button></div></section></el-tab-pane>
    <el-tab-pane v-for="market in (context?.role==='ADS_BUYER'?[]:(viewer?marketTabs:marketTabs.filter(item=>item.marketCode===ownMarket)))" :key="market.marketCode" :label="`${market.marketName}日报`" :name="`market-${market.marketCode}`"><section class="surface table-panel"><div class="sheet-title">{{ market.marketName }}区日报 <span>{{ departmentReviewer?'部门负责人可审核待处理日报。':'按个人日报展示。' }}</span></div><div v-loading="loading" class="table-scroll"><table class="task-table"><thead><tr><th>地区</th><th>每日任务</th><th>执行岗位</th><th>具体负责人</th><th>计划</th><th>实际</th><th>还差</th><th>完成率</th><th>交付链接 / 编号清单</th><th>备注/卡点</th><th>截止时间</th><th>状态</th><th v-if="reviewColumn">操作</th></tr></thead><tbody><tr v-for="item in marketReportRows" :key="item.report.id"><td>{{ item.marketName }}</td><td class="task-stack"><div v-for="metric in item.metrics" :key="metric.metric.label">{{ metric.metric.label }}</div></td><td>{{ item.role }}</td><td>{{ item.report.reporterName }}</td><td class="task-stack"><div v-for="metric in item.metrics" :key="`${metric.metric.label}-plan`">{{ number(metric.plan) }}</div></td><td class="task-stack"><div v-for="metric in item.metrics" :key="`${metric.metric.label}-actual`">{{ number(metric.actual) }}</div></td><td class="task-stack"><div v-for="metric in item.metrics" :key="`${metric.metric.label}-gap`">{{ number(metric.gap) }}</div></td><td class="task-stack"><div v-for="metric in item.metrics" :key="`${metric.metric.label}-rate`">{{ metric.plan?percent(metric.rate):'—' }}</div></td><td class="delivery-cell task-stack"><div v-for="metric in item.metrics" :key="`${metric.metric.label}-delivery`">{{ metric.delivery||'—' }}</div></td><td class="notes-cell">{{ item.report.notes||'—' }}</td><td>{{ submittedAt(item.report.submittedAt) }}</td><td><el-tag size="small" :type="statusType(item.report.submissionStatus)">{{ status(item.report.submissionStatus) }}</el-tag></td><td v-if="reviewColumn"><template v-if="canReview(item.report)"><el-button link type="success" @click="review(item.report,true)">通过</el-button><el-button link type="danger" @click="review(item.report,false)">不通过</el-button></template><span v-else>—</span></td></tr><tr v-if="!loading&&!marketReportRows.length"><td :colspan="reviewColumn?13:12" class="empty">该日期暂无日报</td></tr></tbody></table></div></section></el-tab-pane>
     <el-tab-pane v-if="viewer||context?.role==='ADS_BUYER'" label="投流日报" name="ads"><section class="surface table-panel"><div class="sheet-title">投手数据｜素材测试、消耗、GMV、ROI、CTR、订单 <span>金额和 ROI 按地区账户币种查看。</span></div><div v-loading="loading" class="table-scroll"><table class="ads-table"><thead><tr><th>地区</th><th>填报人</th><th>状态</th><th v-for="field in adFields" :key="field.key">{{ currencyLabel(field,'') }}</th><th>备注/卡点</th><th>退回原因</th><th v-if="departmentReviewer">操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{ row.marketName }}</td><td>{{ row.reporterName }}</td><td><el-tag size="small" :type="statusType(row.submissionStatus)">{{ status(row.submissionStatus) }}</el-tag></td><td v-for="field in adFields" :key="field.key">{{ adValue(row,field.key) }}</td><td class="notes-cell">{{ row.notes||'—' }}</td><td>{{ reasonText(row.rejectionReason)||'—' }}</td><td v-if="departmentReviewer"><template v-if="canReview(row)"><el-button link type="success" @click="review(row,true)">通过</el-button><el-button link type="danger" @click="review(row,false)">不通过</el-button></template><span v-else>—</span></td></tr><tr v-if="!loading&&!rows.length"><td :colspan="20" class="empty">该日期暂无投流日报</td></tr></tbody></table></div><p class="note">同一素材重复测试不累计测试素材数量；测试缺口 = max(计划测试 − 实际测试，0)，ROI 和 CTR 自动计算。</p></section></el-tab-pane>
     <el-tab-pane v-if="contentRole" label="新增日报" name="content-submit"><section class="surface form-panel" v-loading="loading"><div class="sheet-title">{{ context?.role==='EDITOR'?'剪辑':'编导' }}日报｜{{ content.marketCode }} <el-tag size="small" :type="statusType(content.submissionStatus)">{{ status(content.submissionStatus) }}</el-tag></div><el-alert v-if="content.rejectionReason" title="日报已退回" :description="reasonText(content.rejectionReason)" type="error" :closable="false"/><div class="report-table-scroll"><table class="report-form-table"><thead><tr><th>每日任务</th><th>计划</th><th>实际</th><th>还差</th><th>完成率</th><th>交付成果</th></tr></thead><tbody><tr v-for="metric in contentFormMetrics()" :key="metric.label"><td>{{ metric.label }}</td><td><el-input-number v-model="content[metric.plan] as number" :min="0" :precision="0" controls-position="right"/></td><td><el-input-number v-model="content[metric.actual] as number" :min="0" :precision="0" controls-position="right"/></td><td>{{ number(Math.max(Number(content[metric.plan]??0)-Number(content[metric.actual]??0),0)) }}</td><td>{{ Number(content[metric.plan])?percent(Number(content[metric.actual])/Number(content[metric.plan])):'—' }}</td><td><el-input v-model="content.deliveryResults[String(metric.actual)]" maxlength="2000" placeholder="填写链接、编号或交付说明"/></td></tr></tbody></table></div><div class="report-note"><span>备注/卡点</span><el-input v-model="content.notes" type="textarea" :rows="3" maxlength="4000" placeholder="填写备注或当前卡点"/></div><div class="submit-bar"><span>交付成果由填报人填写；提交成功后自动记录提交时间：{{ submittedAt(content.submittedAt) }}</span><div><el-button :loading="saving" @click="saveContent(false)">暂存</el-button><el-button type="primary" :loading="saving" @click="saveContent(true)">提交日报</el-button></div></div></section></el-tab-pane>
     <el-tab-pane v-if="context?.role==='ADS_BUYER'" label="新增投流日报" name="ads-submit"><section class="surface form-panel" v-loading="loading"><div class="sheet-title">投流日报填报</div><el-tabs v-model="adMarket" type="card" class="market-picker"><el-tab-pane v-for="market in marketTabs" :key="market.marketCode" :label="market.marketName" :name="market.marketCode"/></el-tabs><template v-if="ads[adMarket]"><el-alert v-if="ads[adMarket].rejectionReason" title="该地区日报已退回" :description="reasonText(ads[adMarket].rejectionReason)" type="error" :closable="false"/><div class="metric-form ads-form"><label v-for="field in adFields.filter(field=>!['testGap','roi','ctr'].includes(String(field.key)))" :key="field.key"><span>{{ currencyLabel(field,adMarket) }}</span><el-input-number v-model="ads[adMarket][field.key] as number" :min="0" :precision="field.money?2:0" controls-position="right"/></label><label><span>测试缺口（自动）</span><el-input :model-value="String(Math.max(ads[adMarket].plannedTest-ads[adMarket].actualTest,0))" readonly/></label><label><span>ROI（自动）</span><el-input :model-value="number(ads[adMarket].adSpend?ads[adMarket].adGmv/ads[adMarket].adSpend:0)" readonly/></label><label><span>CTR（自动）</span><el-input :model-value="percent(ads[adMarket].impressions?ads[adMarket].clicks/ads[adMarket].impressions:0)" readonly/></label></div><div class="report-note"><span>备注/卡点</span><el-input v-model="ads[adMarket].notes" type="textarea" :rows="3" maxlength="4000" placeholder="填写备注或当前卡点"/></div><div class="submit-bar"><span>一名投手每天可按五个地区分别提交。</span><div><el-button :loading="saving" @click="saveAds(false)">暂存</el-button><el-button type="primary" :loading="saving" @click="saveAds(true)">提交{{ marketTabs.find(item=>item.marketCode===adMarket)?.marketName }}日报</el-button></div></div></template></section></el-tab-pane>
  </el-tabs>
</template>

<style scoped>
.daily-head label{display:grid;grid-template-columns:auto 150px;align-items:center;gap:8px;color:var(--hm-text-secondary);font-size:12px}
.daily-tabs{margin-top:-8px}
.sheet-title{padding:14px 18px;font-weight:650;border-bottom:1px solid var(--hm-border)}
.sheet-title span{margin-left:8px;color:var(--hm-text-secondary);font-weight:400;font-size:12px}
.table-scroll{overflow:auto}
table{width:100%;border-collapse:collapse;font-size:12px;white-space:nowrap}
th,td{border:1px solid var(--hm-border);padding:9px;text-align:center}
th{background:var(--hm-bg-soft);color:var(--hm-text);font-weight:650}
small{display:block;color:var(--hm-text-secondary);font-weight:400}
.summary-table{min-width:1770px}
.summary-table th{background:#19384e;color:#fff}
.summary-table tbody tr:nth-child(even):not(.summary-total){background:#f2f6f9}
.summary-table td:first-child,.summary-total td:first-child{font-weight:650;text-align:left}
.summary-total{background:#19384e;color:#fff}
.summary-total td{border-color:#19384e}
.task-table{min-width:1450px}
.task-table th{background:#19384e;color:#fff}
.task-table td{vertical-align:middle}
.task-stack>div{min-height:20px;line-height:20px}
.task-stack>div+div{border-top:1px solid var(--hm-border);margin-top:4px;padding-top:4px}
.task-table .delivery-cell{min-width:230px;max-width:360px;white-space:normal;text-align:left;overflow-wrap:anywhere}
.notes-cell{min-width:180px;max-width:320px;white-space:normal;text-align:left;overflow-wrap:anywhere}
.ads-table{min-width:1800px}
.empty{padding:28px;color:var(--hm-text-secondary)}
.form-panel{overflow:hidden}
.metric-form{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;padding:18px}
.metric-form label{display:grid;grid-template-columns:116px minmax(0,1fr);gap:8px;align-items:center;min-width:0;min-height:40px;color:var(--hm-text-secondary);font-size:12px}
.metric-form label>span{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.metric-form :deep(.el-input),.metric-form :deep(.el-input-number){width:100%;min-width:0}
.metric-form :deep(.el-input__wrapper){min-height:40px}
.ads-form{grid-template-columns:repeat(3,minmax(280px,1fr))}
.submit-bar{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:14px 18px;border-top:1px solid var(--hm-border);background:var(--hm-bg-soft);color:var(--hm-text-secondary);font-size:12px}
.submit-bar>div{display:flex;align-items:center;gap:12px;flex-shrink:0}
.note{padding:12px 18px;margin:0;color:var(--hm-text-secondary);font-size:12px}
.market-picker{padding:16px 18px 0}
.market-picker :deep(.el-tabs__header){margin-bottom:14px}
.market-picker :deep(.el-tabs__nav-wrap::after){display:none}
.market-picker :deep(.el-tabs__item){height:36px;margin-right:8px;border:1px solid var(--hm-border);border-radius:6px;background:#fff;color:var(--hm-text-secondary);transition:.2s}
.market-picker :deep(.el-tabs__item:hover){color:var(--hm-text);border-color:var(--hm-accent)}
.market-picker :deep(.el-tabs__item.is-active){background:#19384e;color:#fff;border-color:#19384e;box-shadow:0 2px 6px rgba(25,56,78,.18)}
.report-table-scroll{overflow:auto}
.report-form-table{min-width:850px;table-layout:fixed}
.report-form-table th:nth-child(1){width:150px}.report-form-table th:nth-child(2),.report-form-table th:nth-child(3){width:130px}.report-form-table th:nth-child(4){width:100px}.report-form-table th:nth-child(5){width:110px}.report-form-table th:nth-child(6){width:38%}
.report-form-table th{background:var(--hm-bg-soft)}
.report-form-table :deep(.el-input),.report-form-table :deep(.el-input-number){width:100%;min-width:0}
.report-form-table :deep(.el-input__wrapper){min-height:40px}
.report-note{display:grid;grid-template-columns:100px minmax(0,1fr);gap:12px;align-items:start;padding:16px 18px;border-top:1px solid var(--hm-border)}
.report-note>span{padding-top:10px;font-size:12px;color:var(--hm-text-secondary)}
.summary-review{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;padding:18px 0 4px}
.summary-review label{display:grid;gap:7px;color:var(--hm-text-secondary);font-size:12px}
.summary-review :deep(.el-textarea__inner){min-height:88px}
.summary-actions{display:flex;justify-content:flex-end;gap:12px;padding-top:14px}
@media(max-width:1100px){.metric-form,.ads-form{grid-template-columns:1fr 1fr}}
@media(max-width:600px){.daily-head,.submit-bar{align-items:stretch;flex-direction:column}.daily-head label{grid-template-columns:1fr}.metric-form,.ads-form,.summary-review{grid-template-columns:1fr}.metric-form label{grid-template-columns:116px minmax(0,1fr)}.sheet-title span{display:block;margin:5px 0 0}.report-form-table{min-width:850px}.report-note{grid-template-columns:1fr;gap:6px}}
</style>
