<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, save, query } from '../../api/client'
import type { FormInstance } from 'element-plus'
import type { ProductSkuMapping, Shop, SkuConfig } from '../../api/types'
import RequestError from '../../components/RequestError.vue'

const shops = ref<Shop[]>([]), configs = ref<SkuConfig[]>([]), mappings = ref<ProductSkuMapping[]>([])
const loading = ref(false), saving = ref(false), error = ref<Error | null>(null)
const dialog = ref(false), form = ref<FormInstance>()
const selected = ref<SkuConfig>()
const values = reactive({ shopId: '', productIds: '', displayName: '', sellerSku: '', sortOrder: 0, enabled: true })
const unifiedRows = computed(() => configs.value.map(row => ({
  ...row,
  productIds: mappings.value.filter(item => item.sellerSku === row.sellerSku).map(item => item.productId),
})))

async function load() {
  loading.value = true; error.value = null
  try {
    shops.value = await api<Shop[]>('/system/shops?marketCode=MY')
    if (!values.shopId && shops.value.length) values.shopId = shops.value[0].id
    if (values.shopId) {
      ;[configs.value, mappings.value] = await Promise.all([
        api<SkuConfig[]>(`/admin/sku-config?${query({ shopId: values.shopId })}`),
        api<ProductSkuMapping[]>(`/admin/product-sku-mapping?${query({ shopId: values.shopId })}`),
      ])
    }
  } catch (e) { error.value = e as Error } finally { loading.value = false }
}

async function changeShop() {
  ;[configs.value, mappings.value] = await Promise.all([
    api<SkuConfig[]>(`/admin/sku-config?${query({ shopId: values.shopId })}`),
    api<ProductSkuMapping[]>(`/admin/product-sku-mapping?${query({ shopId: values.shopId })}`),
  ])
}

function edit(row?: SkuConfig) {
  selected.value = row
  Object.assign(values, { productIds: row ? mappings.value.filter(item => item.sellerSku === row.sellerSku).map(item => item.productId).join(' / ') : '', displayName: row?.displayName ?? '', sellerSku: row?.sellerSku ?? '', sortOrder: row?.sortOrder ?? 0, enabled: row?.enabled ?? true })
  dialog.value = true
}

function productIds(value: string) { return [...new Set(value.split(/[\s,，/]+/).map(item => item.trim()).filter(Boolean))] }

async function syncProductMappings(oldSellerSku?: string) {
  const existing = mappings.value.filter(item => item.sellerSku === (oldSellerSku ?? values.sellerSku))
  const desired = productIds(values.productIds)
  const requests: Promise<unknown>[] = []
  for (const productId of desired) {
    const row = existing.find(item => item.productId === productId)
    const data = { shopId: values.shopId, productId, displayName: values.displayName, sellerSku: values.sellerSku, sortOrder: values.sortOrder, enabled: values.enabled }
    requests.push(row ? save(`/admin/product-sku-mapping/${row.id}`, data, 'PUT') : save('/admin/product-sku-mapping', data))
  }
  for (const row of existing.filter(item => !desired.includes(item.productId))) requests.push(api(`/admin/product-sku-mapping/${row.id}`, { method: 'DELETE' }))
  await Promise.all(requests)
}

async function submit() {
  if (!await form.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    await save(selected.value ? `/admin/sku-config/${selected.value.id}` : '/admin/sku-config', { ...values }, selected.value ? 'PUT' : 'POST')
    await syncProductMappings(selected.value?.sellerSku)
    dialog.value = false; ElMessage.success('SKU 映射已保存'); await load()
  } catch (e) { ElMessage.error((e as Error).message) } finally { saving.value = false }
}

async function remove(row: SkuConfig) {
  try {
    await ElMessageBox.confirm(`删除 ${row.sellerSku} 的映射？`, '删除 SKU 配置', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await api(`/admin/sku-config/${row.id}`, { method: 'DELETE' }); ElMessage.success('SKU 配置已删除'); await load()
  } catch (e) { if (e instanceof Error) ElMessage.error(e.message) }
}

onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>SKU 配置</h1><p>订单数据按 Seller SKU 映射；投流数据按 Product ID 映射</p></div><el-button type="primary" :disabled="!values.shopId" @click="edit()">新增 SKU 映射</el-button></div>
  <RequestError :error="error" @retry="load" />
  <section class="surface table-panel">
    <div class="panel-head"><div><h2>SKU 映射</h2><small class="muted">按 Seller SKU 展示；Product ID 与 Seller SKU 同时匹配投流数据</small></div><el-select v-model="values.shopId" style="width:240px" @change="changeShop"><el-option v-for="shop in shops" :key="shop.id" :value="shop.id" :label="shop.shopName" /></el-select></div>
    <el-table v-loading="loading" :data="unifiedRows" row-key="id" empty-text="暂无 SKU 映射"><el-table-column label="Product ID" min-width="190"><template #default="{ row }">{{ row.productIds.join(' / ') }}</template></el-table-column><el-table-column prop="displayName" label="看板展示名称" min-width="220" /><el-table-column prop="sellerSku" label="Seller SKU" min-width="220" /><el-table-column prop="sortOrder" label="排序" width="90" align="right" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="140" fixed="right"><template #default="{ row }"><el-button text type="primary" size="small" @click="edit(row)">编辑</el-button><el-button text type="danger" size="small" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
  </section>
  <el-dialog v-model="dialog" :title="selected ? '编辑 SKU 映射' : '新增 SKU 映射'" width="520px" destroy-on-close><el-form ref="form" :model="values" label-position="top" @submit.prevent="submit"><el-form-item label="Product ID（可多个，用 / 分隔）" prop="productIds"><el-input v-model="values.productIds" placeholder="例如：1736479479514957476 / 1736690508665489060" maxlength="1000" /></el-form-item><el-form-item label="Seller SKU" prop="sellerSku" :rules="[{ required: true, message: '请输入 Seller SKU', trigger: 'blur' }]"><el-input v-model="values.sellerSku" maxlength="100" /></el-form-item><el-form-item label="看板展示名称" prop="displayName" :rules="[{ required: true, message: '请输入展示名称', trigger: 'blur' }]"><el-input v-model="values.displayName" maxlength="100" /></el-form-item><el-form-item label="排序"><el-input-number v-model="values.sortOrder" :min="0" :max="2147483647" :precision="0" /></el-form-item><el-form-item label="状态"><el-switch v-model="values.enabled" active-text="启用" inactive-text="停用" /></el-form-item><div class="actions" style="justify-content:flex-end"><el-button @click="dialog = false">取消</el-button><el-button type="primary" native-type="submit" :loading="saving">保存</el-button></div></el-form></el-dialog>
</template>
