<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { api, save } from '../../api/client'
import type { ShootingContext, ShootingScheduleRow, ShootingTicket } from '../../api/types'
import RequestError from '../../components/RequestError.vue'

const context = ref<ShootingContext>()
const tickets = ref<ShootingTicket[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref<Error | null>(null)
const createVisible = ref(false)
const completeVisible = ref(false)
const shotRequirementVisible = ref(false)
const selectedShotRequirement = ref('')
const scheduleVisible = ref(false)
const scheduleDate = ref(todayDate())
const scheduleShooterId = ref('')
const scheduleRows = ref<ShootingScheduleRow[]>([])
const todayRows = ref<ShootingScheduleRow[]>([])
const scheduleLoading = ref(false)
const scheduleError = ref('')
const todayError = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | undefined
const createFormRef = ref<FormInstance>()
const completeFormRef = ref<FormInstance>()

const createForm = reactive({ marketCode: 'MY', countryCode: '', taskType: 'SCRIPT_SHOOT', shotRequirement: '', plannedValidShotCount: 1, shooterId: '', plannedStart: '', plannedEnd: '' })
const completeForm = reactive({ id: '', regionName: '', taskType: '', shotRequirement: '', plannedValidShotCount: 0, plannedStart: '', plannedEnd: '', sku: '', actualValidShotCount: 0, materialNotes: '' })
const createRules: FormRules = {
  marketCode: [{ required: true, message: '请选择地区', trigger: 'change' }],
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  shotRequirement: [{ required: true, message: '请填写具体镜头要求', trigger: 'blur' }],
  plannedValidShotCount: [{ required: true, message: '请填写计划有效镜头数', trigger: 'change' }],
  shooterId: [{ required: true, message: '请选择拍摄人', trigger: 'change' }],
  plannedStart: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  plannedEnd: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}
const completeRules: FormRules = {
  sku: [{ required: true, message: '请填写 SKU', trigger: 'blur' }],
  actualValidShotCount: [{ required: true, message: '请填写实际有效镜头数', trigger: 'change' }],
}
const role = computed(() => context.value?.role ?? 'VIEWER')
const euCountries = computed(() => context.value?.markets.filter(item => ['FR', 'DE'].includes(item.marketCode)) ?? [])
const regions = computed(() => {
  const markets = context.value?.markets ?? []
  return [...markets.filter(item => !['FR', 'DE'].includes(item.marketCode)), ...(euCountries.value.length ? [{ marketCode: 'EU', marketName: '欧盟', currencyCode: 'EUR' }] : [])]
})
const canCreate = computed(() => role.value === 'DIRECTOR' || role.value === 'ADMIN')
const canComplete = computed(() => role.value === 'SHOOTER' || ['ADMIN', 'BOSS'].includes(role.value))
const canReviewEditor = computed(() => role.value === 'DIRECTOR' || ['ADMIN', 'BOSS'].includes(role.value))
const canReviewDept = computed(() => role.value === 'DEPT_HEAD' || ['ADMIN', 'BOSS'].includes(role.value))
const visibleScheduleRows = computed(() => scheduleRows.value.filter(row => !scheduleShooterId.value || row.shooterId === scheduleShooterId.value))

function taskType(code: string) { return context.value?.taskTypes.find(item => item.code === code)?.name ?? code }
function status(row: ShootingTicket) {
  if (row.status === 'PENDING_SHOOT' && row.rejectionReason) return '退回拍摄'
  return ({ PENDING_SHOOT: '待拍摄', PENDING_EDITOR_REVIEW: '待编导审核', PENDING_DEPT_REVIEW: '待部门负责人审核', APPROVED: '已完成' } as Record<string, string>)[row.status] ?? row.status
}
function reasonText(value: string) { return value ? (value.startsWith('原因：') ? value : `原因：${value}`) : '' }
function statusType(row: ShootingTicket) { return row.status === 'APPROVED' ? 'success' : row.status === 'PENDING_SHOOT' && row.rejectionReason ? 'danger' : row.status === 'PENDING_SHOOT' ? 'info' : 'warning' }
function dateTime(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' }) : '—' }
function todayDate() { return new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10) }
function plannedIso(value: string) { return new Date(`${value.replace(' ', 'T')}+08:00`).toISOString() }

async function loadToday() {
  try { todayRows.value = await api<ShootingScheduleRow[]>(`/shooting-tickets/schedule?date=${todayDate()}`); todayError.value = false }
  catch { todayError.value = true }
}
async function loadSchedule() {
  scheduleLoading.value = true; scheduleError.value = ''
  try { scheduleRows.value = await api<ShootingScheduleRow[]>(`/shooting-tickets/schedule?date=${scheduleDate.value}`) }
  catch (e) { scheduleError.value = (e as Error).message; scheduleRows.value = [] }
  finally { scheduleLoading.value = false }
}
async function refresh() { await Promise.all([load(), loadToday(), ...(scheduleVisible.value ? [loadSchedule()] : [])]) }
function openSchedule() { scheduleDate.value = todayDate(); scheduleShooterId.value = ''; loadSchedule() }

async function load() {
  loading.value = true; error.value = null
  try { tickets.value = await api<ShootingTicket[]>('/shooting-tickets') } catch (e) { error.value = e as Error } finally { loading.value = false }
}
function openCreate() {
  Object.assign(createForm, { marketCode: regions.value[0]?.marketCode ?? '', countryCode: '', taskType: 'SCRIPT_SHOOT', shotRequirement: '', plannedValidShotCount: 1, shooterId: context.value?.shooters[0]?.id ?? '', plannedStart: '', plannedEnd: '' })
  createVisible.value = true
}
async function createTicket() {
  if (!(await createFormRef.value?.validate().catch(() => false))) return
  if (plannedIso(createForm.plannedStart) >= plannedIso(createForm.plannedEnd)) { ElMessage.error('结束时间必须晚于开始时间'); return }
  saving.value = true
  try { await save('/shooting-tickets', { ...createForm, marketCode: createForm.marketCode === 'EU' ? createForm.countryCode || 'EU' : createForm.marketCode, plannedStart: plannedIso(createForm.plannedStart), plannedEnd: plannedIso(createForm.plannedEnd) }); ElMessage.success('拍摄工单已提交'); createVisible.value = false; await refresh() } catch (e) { ElMessage.error((e as Error).message) } finally { saving.value = false }
}
function openComplete(row: ShootingTicket) {
  Object.assign(completeForm, { id: row.id, regionName: row.regionName, taskType: taskType(row.taskType), shotRequirement: row.shotRequirement, plannedValidShotCount: row.plannedValidShotCount, plannedStart: dateTime(row.plannedStart), plannedEnd: dateTime(row.plannedEnd), sku: row.sku ?? '', actualValidShotCount: row.actualValidShotCount ?? 0, materialNotes: row.materialNotes ?? '' })
  completeVisible.value = true
}
function openShotRequirement(value: string) { selectedShotRequirement.value = value; shotRequirementVisible.value = true }
async function completeTicket() {
  if (!(await completeFormRef.value?.validate().catch(() => false))) return
  saving.value = true
  try { await save(`/shooting-tickets/${completeForm.id}/complete`, { sku: completeForm.sku, actualValidShotCount: completeForm.actualValidShotCount, materialNotes: completeForm.materialNotes }); ElMessage.success('拍摄结果已提交，等待编导审核'); completeVisible.value = false; await refresh() } catch (e) { ElMessage.error((e as Error).message) } finally { saving.value = false }
}
async function review(row: ShootingTicket, stage: 'EDITOR' | 'DEPT', approved: boolean) {
  let reason = ''
  try {
    if (!approved) { const result = await ElMessageBox.prompt('请填写退回原因，拍摄人员将根据原因重新提交。', '退回拍摄工单', { inputPattern: /\S+/, inputErrorMessage: '退回原因不能为空', confirmButtonText: '确认退回', cancelButtonText: '取消' }); reason = result.value }
    await save(`/shooting-tickets/${row.id}/${stage === 'EDITOR' ? 'editor-review' : 'dept-review'}`, { approved, reason }); ElMessage.success(approved ? '审核已通过' : '工单已退回拍摄'); await refresh()
  } catch (e) { if (e instanceof Error && e.message !== 'cancel') ElMessage.error(e.message) }
}
onMounted(async () => { try { context.value = await api<ShootingContext>('/shooting-tickets/context'); await refresh(); refreshTimer = setInterval(() => { loadToday(); if (scheduleVisible.value) loadSchedule() }, 30000) } catch (e) { error.value = e as Error } })
onUnmounted(() => { if (refreshTimer) clearInterval(refreshTimer) })
</script>

<template>
  <div class="page-head shooting-head">
    <div><h1>拍摄工单</h1><p>编导派单，拍摄交付，编导与部门负责人分级审核。</p></div>
    <div class="head-actions">
      <el-button :loading="!todayRows.length && !todayError && loading" @click="scheduleVisible = true; loadToday()"><span class="schedule-dot" :class="todayError ? 'unknown' : todayRows.length ? 'busy' : 'free'" />{{ todayError ? '拍摄安排暂不可用' : todayRows.length ? '当前正在拍摄' : '当前拍摄空闲' }}</el-button>
      <el-button v-if="canCreate" type="primary" @click="openCreate">新增拍摄工单</el-button>
    </div>
  </div>
  <RequestError :error="error" @retry="load" />
  <section class="surface shooting-panel">
    <div class="sheet-caption">拍摄任务登记 <span>每行一个任务编号，数量单位为有效镜头</span></div>
    <div v-loading="loading" class="table-scroll">
      <table class="shooting-table">
        <thead><tr><th>地区</th><th>编导</th><th>拍摄人</th><th>任务编号</th><th>SKU</th><th>任务类型</th><th>具体镜头要求</th><th>计划有效<br>镜头数</th><th>开始时间</th><th>结束时间</th><th>实际有效<br>镜头数</th><th>实际交付时间</th><th>验收状态</th><th>素材链接／补拍与调整说明</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="row in tickets" :key="row.id">
            <td>{{ row.regionName }}</td><td>{{ row.creatorName || '—' }}</td><td>{{ row.shooterName || '—' }}</td><td>{{ row.ticketNo }}</td><td>{{ row.sku || '—' }}</td><td>{{ taskType(row.taskType) }}</td>
            <td class="requirement"><button v-if="row.shotRequirement" class="requirement-preview" type="button" :aria-label="`查看${row.ticketNo}的具体镜头要求`" @click="openShotRequirement(row.shotRequirement)">{{ row.shotRequirement }}</button><span v-else>—</span></td><td>{{ row.plannedValidShotCount }}</td><td>{{ dateTime(row.plannedStart) }}</td><td>{{ dateTime(row.plannedEnd) }}</td><td>{{ row.actualValidShotCount ?? '—' }}</td><td>{{ dateTime(row.actualDeliveredAt) }}</td>
            <td><el-tooltip v-if="row.rejectionReason" :content="reasonText(row.rejectionReason)" popper-class="format-preserving-tooltip" placement="top"><el-tag size="small" :type="statusType(row)">{{ status(row) }}</el-tag></el-tooltip><el-tag v-else size="small" :type="statusType(row)">{{ status(row) }}</el-tag></td>
            <td class="notes"><span>{{ row.materialNotes || '—' }}</span></td>
            <td class="actions">
              <el-button v-if="canComplete && row.status === 'PENDING_SHOOT'" link type="primary" @click="openComplete(row)">{{ row.rejectionReason ? '重新提交' : '完成拍摄' }}</el-button>
              <template v-if="canReviewEditor && row.status === 'PENDING_EDITOR_REVIEW'"><el-button link type="success" @click="review(row, 'EDITOR', true)">通过</el-button><el-button link type="danger" @click="review(row, 'EDITOR', false)">退回</el-button></template>
              <template v-if="canReviewDept && row.status === 'PENDING_DEPT_REVIEW'"><el-button link type="success" @click="review(row, 'DEPT', true)">通过</el-button><el-button link type="danger" @click="review(row, 'DEPT', false)">退回</el-button></template>
              <span v-if="(row.status === 'APPROVED') || (!canComplete && !canReviewEditor && !canReviewDept) || (row.status !== 'PENDING_SHOOT' && row.status !== 'PENDING_EDITOR_REVIEW' && row.status !== 'PENDING_DEPT_REVIEW')">—</span>
            </td>
          </tr>
          <tr v-if="!loading && !tickets.length"><td colspan="15" class="empty">暂无拍摄工单</td></tr>
        </tbody>
      </table>
    </div>
  </section>

  <el-dialog v-model="scheduleVisible" title="拍摄工单计划" width="620px" destroy-on-close @open="openSchedule">
    <div class="schedule-filters">
      <el-date-picker v-model="scheduleDate" type="date" value-format="YYYY-MM-DD" :clearable="false" placeholder="选择日期" @change="loadSchedule" />
      <el-select v-model="scheduleShooterId" clearable placeholder="全部拍摄人"><el-option v-for="shooter in context?.shooters" :key="shooter.id" :label="shooter.displayName" :value="shooter.id" /></el-select>
    </div>
    <div v-if="scheduleError" class="schedule-error">{{ scheduleError }}</div>
    <div v-loading="scheduleLoading" class="schedule-list"><table><thead><tr><th>拍摄人</th><th>开始时间</th><th>结束时间</th><th>计划镜头数</th></tr></thead><tbody><tr v-for="(row, index) in visibleScheduleRows" :key="index"><td>{{ row.shooterName }}</td><td>{{ dateTime(row.plannedStart) }}</td><td>{{ dateTime(row.plannedEnd) }}</td><td>{{ row.plannedValidShotCount }}</td></tr><tr v-if="!scheduleLoading && !scheduleError && !visibleScheduleRows.length"><td colspan="4">当天暂无待拍工单</td></tr></tbody></table></div>
    <template #footer><el-button @click="scheduleVisible = false">关闭</el-button></template>
  </el-dialog>

  <el-dialog v-model="createVisible" title="新增拍摄工单" width="620px" destroy-on-close>
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
      <div class="form-grid"><el-form-item label="地区" prop="marketCode"><el-select v-model="createForm.marketCode" placeholder="请选择地区" @change="createForm.countryCode = ''"><el-option v-for="item in regions" :key="item.marketCode" :label="item.marketName" :value="item.marketCode" /></el-select></el-form-item><el-form-item v-if="createForm.marketCode === 'EU'" label="国家（可选）"><el-select v-model="createForm.countryCode" clearable placeholder="不选则为欧盟"><el-option v-for="item in euCountries" :key="item.marketCode" :label="item.marketName" :value="item.marketCode" /></el-select></el-form-item><el-form-item label="任务类型" prop="taskType"><el-select v-model="createForm.taskType" placeholder="请选择任务类型"><el-option v-for="item in context?.taskTypes" :key="item.code" :label="item.name" :value="item.code" /></el-select></el-form-item><el-form-item label="计划有效镜头数" prop="plannedValidShotCount"><el-input-number v-model="createForm.plannedValidShotCount" :min="0" :precision="0" controls-position="right" /></el-form-item><el-form-item label="拍摄人" prop="shooterId"><el-select v-model="createForm.shooterId" placeholder="请选择拍摄人"><el-option v-for="shooter in context?.shooters" :key="shooter.id" :label="shooter.displayName" :value="shooter.id" /></el-select></el-form-item><el-form-item label="开始时间" prop="plannedStart"><el-date-picker v-model="createForm.plannedStart" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择开始时间" /></el-form-item><el-form-item label="结束时间" prop="plannedEnd"><el-date-picker v-model="createForm.plannedEnd" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择结束时间" /></el-form-item></div>
      <el-form-item label="具体镜头要求" prop="shotRequirement"><el-input v-model="createForm.shotRequirement" type="textarea" :autosize="{ minRows: 5, maxRows: 10 }" maxlength="4000" show-word-limit placeholder="填写具体镜头、动作、构图或脚本要求" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="createTicket">提交工单</el-button></template>
  </el-dialog>

  <el-dialog v-model="completeVisible" title="提交拍摄结果" width="720px" destroy-on-close>
    <el-form ref="completeFormRef" :model="completeForm" :rules="completeRules" label-position="top">
      <div class="readonly-grid"><el-form-item label="地区"><el-input :model-value="completeForm.regionName" readonly /></el-form-item><el-form-item label="任务类型"><el-input :model-value="completeForm.taskType" readonly /></el-form-item><el-form-item label="计划有效镜头数"><el-input :model-value="String(completeForm.plannedValidShotCount)" readonly /></el-form-item><el-form-item label="开始时间"><el-input :model-value="completeForm.plannedStart" readonly /></el-form-item><el-form-item label="结束时间"><el-input :model-value="completeForm.plannedEnd" readonly /></el-form-item></div>
      <el-form-item label="具体镜头要求"><el-input :model-value="completeForm.shotRequirement" type="textarea" :autosize="{ minRows: 4, maxRows: 10 }" readonly /></el-form-item>
      <div class="form-grid"><el-form-item label="SKU" prop="sku"><el-input v-model="completeForm.sku" maxlength="500" placeholder="填写实际拍摄 SKU" /></el-form-item><el-form-item label="实际有效镜头数" prop="actualValidShotCount"><el-input-number v-model="completeForm.actualValidShotCount" :min="0" :precision="0" controls-position="right" /></el-form-item></div>
      <el-form-item label="素材链接／补拍与调整说明"><el-input v-model="completeForm.materialNotes" type="textarea" :autosize="{ minRows: 4, maxRows: 10 }" maxlength="4000" show-word-limit placeholder="填写素材链接，或补拍与调整说明" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="completeVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="completeTicket">提交拍摄结果</el-button></template>
  </el-dialog>

  <el-dialog v-model="shotRequirementVisible" title="具体镜头要求" width="620px" destroy-on-close>
    <div class="requirement-full">{{ selectedShotRequirement }}</div>
    <template #footer><el-button @click="shotRequirementVisible = false">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
.shooting-head{align-items:center}.head-actions{display:flex;gap:10px;flex-wrap:wrap}.schedule-dot{display:inline-block;width:8px;height:8px;margin-right:7px;border-radius:50%}.schedule-dot.free{background:#28a745}.schedule-dot.busy{background:#e6a23c}.schedule-dot.unknown{background:#909399}.schedule-filters{display:flex;gap:10px;margin-bottom:12px}.schedule-filters>*{flex:1;min-width:0}.schedule-list{max-height:360px;overflow:auto}.schedule-list table{width:100%;border-collapse:collapse;text-align:center;font-size:12px}.schedule-list th,.schedule-list td{padding:9px 6px;border:1px solid var(--hm-border)}.schedule-list th{background:var(--hm-bg-soft)}.schedule-error{margin-bottom:10px;color:#c45656}.shooting-panel{overflow:hidden}.sheet-caption{padding:14px 18px;font-weight:650;border-bottom:1px solid var(--hm-border)}.sheet-caption span{margin-left:8px;color:var(--hm-text-secondary);font-size:12px;font-weight:400}.table-scroll{overflow:auto}.shooting-table{width:100%;min-width:2050px;border-collapse:collapse;font-size:12px}.shooting-table th,.shooting-table td{border:1px solid #B9DDE1;padding:10px 9px;text-align:center;vertical-align:middle}.shooting-table th{background:#19384E;color:#fff;font-weight:650;white-space:nowrap}.shooting-table td{height:150px;max-height:150px;background:#FFF6DB;overflow:hidden}.shooting-table .requirement,.shooting-table .notes{min-width:230px;max-width:320px;text-align:left;overflow-wrap:anywhere}.requirement-preview,.notes>span{display:-webkit-box;width:100%;height:128px;max-height:128px;padding:0;border:0;background:transparent;color:inherit;text-align:left;white-space:break-spaces;overflow:hidden;overflow-wrap:anywhere;line-height:20px;-webkit-box-orient:vertical;-webkit-line-clamp:6}.requirement-preview{cursor:pointer}.requirement-preview:hover{color:#356FAE}.shooting-table .actions{min-width:130px;white-space:nowrap}.requirement-full{max-height:min(60vh,520px);overflow:auto;padding:12px;background:var(--hm-bg-soft);border:1px solid var(--hm-border);border-radius:9px;white-space:break-spaces;overflow-wrap:anywhere;line-height:1.7}.empty{padding:32px!important;color:var(--hm-text-secondary)}.form-grid,.readonly-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}.form-grid :deep(.el-select),.form-grid :deep(.el-date-editor),.form-grid :deep(.el-input-number){width:100%}.readonly-grid :deep(.el-input__wrapper){background:#F5F7FA}.shooting-table :deep(.el-tag){border:0}:deep(.el-textarea__inner){white-space:break-spaces}:global(.format-preserving-tooltip .el-popper__inner){white-space:break-spaces;overflow-wrap:anywhere}@media(max-width:700px){.form-grid,.readonly-grid{grid-template-columns:1fr}.shooting-head{align-items:flex-start;gap:12px;flex-direction:column}.schedule-filters{flex-direction:column}}
</style>
