<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { api, save } from '../../api/client'
import type { Market, Shop } from '../../api/types'
import RequestError from '../../components/RequestError.vue'

interface ManagedShop extends Shop { marketName: string; enabled: boolean }
const markets = ref<Market[]>([]), shops = ref<ManagedShop[]>([])
const loading = ref(false), saving = ref(false), error = ref<Error | null>(null), dialog = ref(false), form = ref<FormInstance>()
const values = reactive({ marketCode: '', shopName: '', enabled: true })
const selected = ref<ManagedShop>()

async function load() {
  loading.value = true; error.value = null
  try { [markets.value, shops.value] = await Promise.all([api<Market[]>('/system/markets'), api<ManagedShop[]>('/admin/shops')]) }
  catch (e) { error.value = e as Error } finally { loading.value = false }
}

function add() { selected.value = undefined; Object.assign(values, { marketCode: '', shopName: '', enabled: true }); dialog.value = true }
function edit(shop: ManagedShop) { selected.value = shop; Object.assign(values, { marketCode: shop.marketCode, shopName: shop.shopName, enabled: shop.enabled }); dialog.value = true }

async function submit() {
  if (!await form.value?.validate().catch(() => false)) return
  saving.value = true
  try { await save(selected.value ? `/admin/shops/${selected.value.id}` : '/admin/shops', values, selected.value ? 'PUT' : 'POST'); dialog.value = false; ElMessage.success(selected.value ? '店铺已更新' : '店铺已添加'); await load() }
  catch (e) { ElMessage.error((e as Error).message) } finally { saving.value = false }
}

async function remove(shop: ManagedShop) {
  try {
    await ElMessageBox.confirm(`停用“${shop.shopName}”？停用后将从导入选项中隐藏，历史数据会保留。`, '删除店铺', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await api(`/admin/shops/${shop.id}`, { method: 'DELETE' }); ElMessage.success('店铺已删除'); await load()
  } catch (e) { if (e instanceof Error) ElMessage.error(e.message) }
}

onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>店铺管理</h1><p>按市场添加 TikTok Shop 店铺；添加后即可在数据导入页选择</p></div><el-button type="primary" @click="add">添加店铺</el-button></div>
  <RequestError :error="error" @retry="load" />
  <section class="surface table-panel">
    <div class="panel-head"><h2>已配置店铺</h2></div>
    <el-table v-loading="loading" :data="shops" row-key="id" empty-text="暂无店铺">
      <el-table-column prop="marketName" label="市场" width="160" />
      <el-table-column prop="marketCode" label="市场代码" width="120" />
      <el-table-column prop="shopName" label="店铺名称" min-width="220" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="edit(row)">编辑</el-button><el-button v-if="row.enabled" text type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
  </section>
  <el-dialog v-model="dialog" :title="selected ? '编辑店铺' : '添加店铺'" width="480px" destroy-on-close :close-on-click-modal="false">
    <el-form ref="form" :model="values" label-position="top" @submit.prevent="submit">
      <el-form-item label="市场" prop="marketCode" :rules="[{ required: true, message: '请选择市场', trigger: 'change' }]">
        <el-select v-model="values.marketCode" placeholder="请选择市场" style="width:100%" :disabled="!!selected"><el-option v-for="market in markets" :key="market.marketCode" :label="`${market.marketName} (${market.marketCode})`" :value="market.marketCode" /></el-select>
      </el-form-item>
      <el-form-item label="店铺名称" prop="shopName" :rules="[{ required: true, message: '请输入店铺名称', trigger: 'blur' }]">
        <el-input v-model="values.shopName" maxlength="200" placeholder="例如：英国主店" />
      </el-form-item>
      <el-form-item v-if="selected" label="状态"><el-switch v-model="values.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      <div class="actions" style="justify-content:flex-end"><el-button @click="dialog = false">取消</el-button><el-button type="primary" native-type="submit" :loading="saving">保存</el-button></div>
    </el-form>
  </el-dialog>
</template>
