<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api, query, save } from '../../api/client'
import type { DailyReport, DailyReportRow, DailyTask } from '../../api/types'
import { useAuth } from '../../stores/auth'
import RequestError from '../../components/RequestError.vue'

const auth=useAuth(), leader=computed(()=>auth.user?.roles.some(role=>['ADMIN','BOSS'].includes(role.roleCode))??false)
const localDate=()=>{const now=new Date();return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`}
const selectedDate=ref(localDate()),reportDate=ref(localDate()),tab=ref(leader.value?'overview':'submit'),rows=ref<DailyReportRow[]>([]),loading=ref(false),saving=ref(false),error=ref<Error|null>(null)
const report=reactive<DailyReport>({reportDate:reportDate.value,groupName:'',roleMarket:'',todayFocus:'',keyResult:'',needBossSupport:'',tomorrowFocus:'',submissionStatus:'DRAFT',reporterName:'',tasks:[]})
const blankTask=():DailyTask=>({workModule:'',workDetail:'',planDelivery:'',actualResult:'',completionStatus:'进行中',issueNextStep:'',resultLink:''})
const grouped=computed(()=>{
  const byReporter=new Map<string,DailyReportRow[]>();for(const row of rows.value){const key=`${row.reporterName}｜${row.groupName}｜${row.roleMarket}`;byReporter.set(key,[...(byReporter.get(key)??[]),row])}return [...byReporter.entries()]
})
function applyMine(value:DailyReport){Object.assign(report,{...value,reportDate:reportDate.value,tasks:value.tasks.length?value.tasks.map(task=>({...task})): [blankTask()]})}
async function loadRows(){loading.value=true;error.value=null;try{rows.value=await api<DailyReportRow[]>(`/daily-reports?${query({date:selectedDate.value})}`)}catch(e){error.value=e as Error}finally{loading.value=false}}
async function loadMine(){try{applyMine(await api<DailyReport>(`/daily-reports/mine?${query({date:reportDate.value})}`))}catch(e){error.value=e as Error}}
function addTask(){report.tasks.push(blankTask())}
function removeTask(index:number){if(report.tasks.length===1)return ElMessage.warning('日报至少保留一项工作');report.tasks.splice(index,1)}
function payload(){return {groupName:report.groupName,roleMarket:report.roleMarket,todayFocus:report.todayFocus,keyResult:report.keyResult,needBossSupport:report.needBossSupport,tomorrowFocus:report.tomorrowFocus,tasks:report.tasks}}
async function store(submit:boolean){
  saving.value=true
  try{applyMine(await save<DailyReport>(submit?`/daily-reports/mine/${reportDate.value}/submit`:`/daily-reports/mine/${reportDate.value}`,payload(),submit?'POST':'PUT'));ElMessage.success(submit?'日报已提交':'日报已暂存');await loadRows()}catch(e){ElMessage.error((e as Error).message)}finally{saving.value=false}
}
async function submit(){
  if(!report.groupName.trim()||!report.roleMarket.trim())return ElMessage.error('请填写所属小组和岗位 / 负责市场')
  if(report.tasks.some(task=>!task.workModule.trim()||!task.workDetail.trim()||!task.planDelivery.trim()))return ElMessage.error('请填写每项任务的工作模块、今日具体工作和计划交付')
  await store(true)
}
watch(reportDate,()=>void loadMine())
watch(selectedDate,()=>void loadRows())
onMounted(()=>{void Promise.all([loadRows(),loadMine()])})
</script>

<template>
  <div class="page-head"><div><h1>日报</h1><p>{{ leader?'老板可按日报标签查看全员记录，也可提交自己的日报。':'提交并查看仅属于自己账号的日报记录。' }}</p></div></div>
  <el-tabs v-model="tab" class="sheet-tabs">
    <el-tab-pane v-if="leader" label="老板日报" name="overview">
      <section class="surface excel-sheet"><div class="excel-title">TikTok 团队｜每日工作汇报</div><div class="excel-note">老板先看本页；具体到个人的工作请查看“团队每日明细”。</div>
        <div class="date-bar"><label>日期<el-date-picker v-model="selectedDate" value-format="YYYY-MM-DD" type="date" :clearable="false"/></label><span>{{ rows.length }} 项已上报工作</span></div>
        <RequestError :error="error" @retry="loadRows"/>
        <div v-loading="loading" class="report-cards"><article v-for="[name,tasks] in grouped" :key="name" class="report-card"><header><strong>汇报人：{{ tasks[0].reporterName }}</strong><span>今日重点：{{ tasks[0].todayFocus||'—' }}</span></header><div class="table-scroll"><table><thead><tr><th>工作归属</th><th>负责人 / 成员</th><th>工作模块</th><th>今日具体工作</th><th>计划交付</th><th>实际完成 / 结果</th><th>完成状态</th><th>问题及下一步</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.taskId"><td>{{ task.groupName }}</td><td>{{ task.reporterName }}</td><td>{{ task.workModule }}</td><td>{{ task.workDetail }}</td><td>{{ task.planDelivery }}</td><td>{{ task.actualResult||'—' }}</td><td><el-tag size="small" :type="task.completionStatus==='已完成'?'success':task.completionStatus==='已阻塞'?'danger':'warning'">{{ task.completionStatus }}</el-tag></td><td>{{ task.issueNextStep||'—' }}</td></tr></tbody></table></div><div class="boss-summary"><span><b>今日最重要成果</b>{{ tasks[0].keyResult||'—' }}</span><span><b>需老板支持</b>{{ tasks[0].needBossSupport||'—' }}</span><span><b>明日重点</b>{{ tasks[0].tomorrowFocus||'—' }}</span></div></article><el-empty v-if="!loading&&!grouped.length" description="该日期暂无日报"/></div>
      </section>
    </el-tab-pane>
    <el-tab-pane :label="leader?'团队每日明细':'我的日报记录'" name="details">
      <section class="surface excel-sheet"><div class="excel-title">TikTok 团队｜个人每日工作明细</div><div class="excel-note">每人每项任务填一行；成员只能读取自己的记录。</div>
        <div class="date-bar"><label>日期<el-date-picker v-model="selectedDate" value-format="YYYY-MM-DD" type="date" :clearable="false"/></label><span>{{ leader?'全员记录':'仅我的记录' }}</span></div>
        <RequestError :error="error" @retry="loadRows"/>
        <div class="table-scroll"><el-table v-loading="loading" :data="rows" border empty-text="该日期暂无日报"><el-table-column prop="reportDate" label="日期" width="110"/><el-table-column prop="groupName" label="所属小组" min-width="115"/><el-table-column prop="reporterName" label="负责人" min-width="105"/><el-table-column prop="roleMarket" label="岗位 / 负责市场" min-width="150"/><el-table-column prop="workDetail" label="今日具体工作" min-width="210" show-overflow-tooltip/><el-table-column prop="planDelivery" label="计划交付" min-width="165" show-overflow-tooltip/><el-table-column prop="actualResult" label="实际完成 / 结果" min-width="180" show-overflow-tooltip/><el-table-column label="完成状态" width="105"><template #default="{row}"><el-tag size="small" :type="row.completionStatus==='已完成'?'success':row.completionStatus==='已阻塞'?'danger':'warning'">{{ row.completionStatus }}</el-tag></template></el-table-column><el-table-column prop="issueNextStep" label="问题及下一步" min-width="190" show-overflow-tooltip/><el-table-column label="成果链接 / 位置" min-width="145"><template #default="{row}"><a v-if="row.resultLink" :href="row.resultLink" target="_blank" rel="noopener">查看成果</a><span v-else>—</span></template></el-table-column></el-table></div>
      </section>
    </el-tab-pane>
    <el-tab-pane label="提交日报" name="submit">
      <section class="surface submit-sheet"><div class="excel-title">TikTok 团队｜个人每日工作明细</div><p class="excel-note">早上写计划，收工前补结果。暂存仅本人可见，提交后领导看到完全相同的字段。</p>
        <div class="report-meta"><label>日期<el-date-picker v-model="reportDate" value-format="YYYY-MM-DD" type="date" :clearable="false"/></label><label>所属小组<el-input v-model="report.groupName" maxlength="100" placeholder="如：视频组"/></label><label>负责人<el-input :model-value="report.reporterName" readonly/></label><label>岗位 / 负责市场<el-input v-model="report.roleMarket" maxlength="100" placeholder="如：马来西亚编导"/></label><label class="wide">今日重点<el-input v-model="report.todayFocus" type="textarea" :rows="2" maxlength="2000" show-word-limit placeholder="写清当天最重要的工作重点"/></label></div>
        <div class="task-head"><h2>工作明细</h2><el-button type="primary" plain @click="addTask"><el-icon><Plus/></el-icon>&nbsp;新增一项工作</el-button></div>
        <article v-for="(task,index) in report.tasks" :key="index" class="task-form"><div class="task-form-title"><strong>第 {{ index+1 }} 项</strong><el-button text type="danger" :disabled="report.tasks.length===1" aria-label="删除工作项" @click="removeTask(index)"><el-icon><Delete/></el-icon> 删除</el-button></div><div class="task-grid"><label>工作模块<el-input v-model="task.workModule" maxlength="100" placeholder="如：内容审核"/></label><label>完成状态<el-select v-model="task.completionStatus"><el-option label="未开始" value="未开始"/><el-option label="进行中" value="进行中"/><el-option label="已完成" value="已完成"/><el-option label="已阻塞" value="已阻塞"/></el-select></label><label class="wide">今日具体工作<el-input v-model="task.workDetail" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="写清动作 + 数量或结论"/></label><label>计划交付<el-input v-model="task.planDelivery" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="如：脚本 5 条"/></label><label>实际完成 / 结果<el-input v-model="task.actualResult" type="textarea" :rows="3" maxlength="2000" show-word-limit/></label><label>成果链接 / 位置<el-input v-model="task.resultLink" maxlength="2000" placeholder="链接或文件夹位置（可选）"/></label><label class="wide">问题及下一步<el-input v-model="task.issueNextStep" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="写原因 + 处理人 + 时间"/></label></div></article>
        <div class="summary-form"><label>今日最重要成果<el-input v-model="report.keyResult" type="textarea" :rows="2" maxlength="2000" show-word-limit/></label><label>需老板支持<el-input v-model="report.needBossSupport" type="textarea" :rows="2" maxlength="2000" show-word-limit/></label><label>明日重点<el-input v-model="report.tomorrowFocus" type="textarea" :rows="2" maxlength="2000" show-word-limit/></label></div>
        <div class="submit-bar"><span>{{ report.submissionStatus==='SUBMITTED'?'该日报已提交，继续编辑后可再次提交。':'成员暂存的日报仅本人可见。' }}</span><div class="actions"><el-button :loading="saving" @click="store(false)">暂存日报</el-button><el-button type="primary" :loading="saving" @click="submit">提交日报</el-button></div></div>
      </section>
    </el-tab-pane>
  </el-tabs>
</template>

<style scoped>
.sheet-tabs{margin-top:-8px}.excel-sheet,.submit-sheet{overflow:hidden}.excel-title{padding:16px 20px;background:#F2F4F3;color:#2B302D;font-size:16px;font-weight:650;text-align:center;border-bottom:1px solid #E2E6E3}.excel-note{padding:10px 16px;color:#707872;background:#F7F8F7;border-bottom:1px solid #E2E6E3;font-size:12px}.date-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 16px;color:var(--text-secondary);font-size:12px}.date-bar label,.report-meta label,.task-grid label,.summary-form label{display:grid;gap:6px;color:#4D554F;font-size:12px}.date-bar label{grid-template-columns:auto 148px;align-items:center}.report-cards{display:grid;gap:14px;padding:0 16px 16px}.report-card{border:1px solid #E2E6E3;border-radius:6px;overflow:hidden}.report-card header{padding:10px 12px;background:#F3F5F4;display:flex;gap:10px;align-items:center}.report-card header span{color:var(--text-secondary);font-size:12px}table{border-collapse:collapse;width:100%;min-width:1100px;font-size:12px}th{background:#F3F5F4;color:#2B302D;font-weight:650;text-align:left}th,td{padding:10px;border:1px solid #E2E6E3;vertical-align:top;line-height:1.55;white-space:pre-wrap}.boss-summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));border-top:1px solid #E2E6E3}.boss-summary span{padding:10px;border-right:1px solid #E2E6E3;font-size:12px;white-space:pre-wrap}.boss-summary span:last-child{border-right:0}.boss-summary b{display:block;color:#2B302D;margin-bottom:4px}.submit-sheet{padding-bottom:0}.submit-sheet .excel-note{margin:0}.report-meta{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;padding:18px;border-bottom:1px solid var(--border)}.task-head{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:18px}.task-form{margin:0 18px 16px;border:1px solid #E2E6E3;border-radius:6px;overflow:hidden}.task-form-title{padding:10px 12px;background:#F3F5F4;display:flex;justify-content:space-between;align-items:center}.task-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;padding:14px}.summary-form{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px;padding:2px 18px 18px}.wide{grid-column:1/-1}.submit-bar{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:16px 18px;background:#F7F8F7;border-top:1px solid #E2E6E3;color:var(--text-secondary);font-size:12px}
@media(max-width:850px){.report-meta{grid-template-columns:repeat(2,minmax(0,1fr))}.summary-form,.boss-summary{grid-template-columns:1fr}.boss-summary span{border-right:0;border-bottom:1px solid #E2E6E3}.boss-summary span:last-child{border-bottom:0}}
@media(max-width:600px){.date-bar,.submit-bar{align-items:stretch;flex-direction:column}.date-bar label{grid-template-columns:1fr}.report-meta,.task-grid,.summary-form{grid-template-columns:1fr}.wide{grid-column:auto}.task-form{margin:0 12px 12px}}
</style>
