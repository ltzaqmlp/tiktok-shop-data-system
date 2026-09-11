<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Coin, Goods, InfoFilled, Refresh, ShoppingBag, Tickets, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api, query, download } from '../../api/client'
import type { Overview, Trend, Funnel, Ads, SkuSales, AfterSales, Metric, Market } from '../../api/types'
import { comparisonLabel, dateRangeError, presetRange, number } from '../../api/format.mjs'
import { useAuth } from '../../stores/auth'
import Chart from '../../components/Chart.vue'
import MetricChange from '../../components/MetricChange.vue'
import RequestError from '../../components/RequestError.vue'
import { spark, combo, dynamicCombo } from './charts'
const auth = useAuth(), busy = ref(false), exporting = ref(false), error = ref<Error | null>(null), markets = ref<Market[]>([]), marketCode = ref('MY')
const initialRange = presetRange('yesterday') as [string, string], dates = ref<[string, string]>(initialRange), period = ref('yesterday')
const overview = ref<Overview>(), sales = ref<Trend[]>([]), funnel = ref<Funnel>(), ads = ref<Ads>(), skuSales = ref<SkuSales[]>([]), after = ref<AfterSales>(), loaded = ref(false)
const applied = ref({ marketCode: 'MY', dateFrom: initialRange[0], dateTo: initialRange[1], comparisonPeriod: 'yesterday' })
const single = computed(() => applied.value.dateFrom === applied.value.dateTo), compare = computed(() => overview.value?.comparisonLabel ?? comparisonLabel(applied.value.comparisonPeriod))
const currency = computed(() => overview.value?.currencyCode ?? markets.value.find(m => m.marketCode === marketCode.value)?.currencyCode ?? 'MYR')
const currencyDisplay = computed(() => currency.value === 'MYR' ? 'RM' : currency.value)
const metricIcons: Record<string, unknown> = { gmv: ShoppingBag, orderCount: Tickets, soldQty: Goods, aov: Coin, refundAmount: Refresh }
const kpis = computed(() => [{ key: 'gmv', label: 'GMV', hint: 'Shop Analytics 每日 GMV', money: true }, { key: 'orderCount', label: '订单数', hint: 'Shop Analytics 每日订单数' }, { key: 'soldQty', label: '商品成交件数', hint: 'Shop Analytics 每日商品成交件数' }, { key: 'aov', label: '平均订单金额', hint: 'AOV = 总 GMV ÷ 总订单数', money: true }, { key: 'refundAmount', label: '退款金额', hint: 'Shop Analytics 每日退款金额', money: true, direction: 'lower' }])
const getMetric = (key: string) => overview.value?.[key as keyof Overview] as Metric | undefined
const pct = (v: number | null | undefined) => v == null ? '—' : `${number(v * 100, 1)}%`
const productNotice = computed(() => { if (!loaded.value || !funnel.value) return ''; if (funnel.value.source === 'SHOP_ANALYTICS_PARTIAL') return '曝光、点击及去重指标使用 Shop Analytics 店铺口径；加购指标继续使用商品报表，商品报表缺失时显示为横线。'; if (funnel.value.source === 'NONE') return '当前范围没有 Shop Analytics 数据，商品漏斗无法计算。'; return '' })
const funnelSourceLabel = computed(() => ({ PRODUCT_PERIOD: '商品区间报表', PRODUCT_DAILY: '商品单日数据', SHOP_ANALYTICS_PARTIAL: 'Shop Analytics（流量口径）', NONE: '无可用数据' }[funnel.value?.source ?? 'NONE']))
const funnelStatus = computed(() => ({ PRODUCT_PERIOD: '完整商品数据', PRODUCT_DAILY: '完整商品数据', SHOP_ANALYTICS_PARTIAL: '部分数据', NONE: '无可用数据' }[funnel.value?.source ?? 'NONE']))
const funnelStatusType = computed(() => funnel.value?.source === 'NONE' ? 'warning' : funnel.value?.source === 'SHOP_ANALYTICS_PARTIAL' ? 'info' : 'success')
const stages = computed(() => [{ label: '商品曝光次数', value: funnel.value?.impressions, rateLabel: '', rate: null, aux: '去重曝光', extra: funnel.value?.uniqueImpressions }, { label: '商品点击量', value: funnel.value?.clicks, rateLabel: 'CTR', rate: funnel.value?.ctr, aux: '去重点击', extra: funnel.value?.uniqueClicks }, { label: '加购次数', value: funnel.value?.addToCartCount, rateLabel: '加购率', rate: funnel.value?.addToCartRate, aux: '加购用户', extra: funnel.value?.addedUserCount }, { label: 'SKU 订单数', value: funnel.value?.skuOrderCount, rateLabel: 'CTOR', rate: funnel.value?.ctor, aux: '下单客户', extra: funnel.value?.estimatedCustomerCount }])
const adMetricConfig = [{ key: 'spend', trendKey: 'spend', metricKey: 'spend', label: '广告花费', chartLabel: '广告花费', direction: 'neutral', money: true }, { key: 'totalRevenue', trendKey: 'totalRevenue', metricKey: 'attributedRevenue', label: '总收入', chartLabel: '总收入', direction: 'higher', money: true }, { key: 'roi', trendKey: 'roi', metricKey: 'roi', label: 'ROI', chartLabel: '投入产出比', direction: 'higher', money: false }, { key: 'cpo', trendKey: 'cpo', metricKey: 'cpo', label: 'CPO', chartLabel: '每单成本', direction: 'lower', money: true }] as const
type AdMetricKey = typeof adMetricConfig[number]['key']
const selectedAdMetricKeys = ref<AdMetricKey[]>(['spend', 'totalRevenue'])
const adMetrics = computed(() => adMetricConfig.map(metric => ({ ...metric, metric: ads.value?.[metric.metricKey] })))
const adCurrencyDisplay = 'USD'
const selectedAdMetrics = computed(() => adMetricConfig.filter(metric => selectedAdMetricKeys.value.includes(metric.key)).map(metric => ({ key: metric.trendKey, label: `${metric.chartLabel}${metric.money ? `（${adCurrencyDisplay}）` : ''}` })))
const adChartRows = computed(() => ads.value?.trend?.map(row => ({ ...row, totalRevenue: row.attributedRevenue })) ?? [])
const adChartTitle = computed(() => selectedAdMetrics.value.map(metric => metric.label).join('与'))
function toggleAdMetric(key: AdMetricKey) { const index = selectedAdMetricKeys.value.indexOf(key); if (index >= 0) { selectedAdMetricKeys.value = selectedAdMetricKeys.value.filter(item => item !== key); return } if (selectedAdMetricKeys.value.length < 2) { selectedAdMetricKeys.value = [...selectedAdMetricKeys.value, key]; return } ElMessage.info('图表最多同时展示两个指标，请先取消一个已选指标') }
const currentAfterMetrics = computed(() => [{ label: '退款金额', metric: after.value?.refundAmount, money: true }, { label: '取消订单数', metric: after.value?.cancelOrderCount }])
const cumulativeAfterMetrics = computed(() => [{ label: '已退款商品件数', metric: after.value?.refundedQty }, { label: '退款客户数', metric: after.value?.refundCustomerCount }])
const afterSource = computed(() => after.value?.productDataRange ? `商品售后累计数据：${after.value.productDataRange.dateFrom} 至今` : '商品售后累计数据暂无可用报表')
const afterUpdatedAt = computed(() => after.value?.productDataRange?.dateTo)
const skuSalesTotal = computed(() => skuSales.value.reduce((sum, s) => sum + s.sales, 0))
const skuSalesOption = computed(() => ({ tooltip: { trigger: 'item', confine: true }, color: ['#5F8FBE', '#7EA6C9', '#AAB7C5', '#C7D3DF', '#D9E2EB', '#E14B50'], series: [{ type: 'pie', radius: ['66%', '84%'], center: ['50%', '50%'], label: { show: false }, itemStyle: { borderWidth: 2, borderColor: '#fff' }, data: skuSales.value.map(s => ({ name: s.displayName, value: s.sales })) }] }))
const sourceSales = computed(() => [{ label: '自营销量', value: overview.value?.selfSales?.value ?? null }, { label: '达人销量', value: overview.value?.affiliateSales?.value ?? null }])
const sourceSalesTotal = computed(() => sourceSales.value.reduce((sum, item) => sum + Math.max(Number(item.value ?? 0), 0), 0))
const sourceSalesOption = computed(() => ({ tooltip: { trigger: 'item', confine: true }, color: ['#5F8FBE', '#E14B50'], series: [{ type: 'pie', radius: ['64%', '82%'], center: ['50%', '50%'], label: { show: false }, itemStyle: { borderWidth: 2, borderColor: '#fff' }, data: sourceSales.value.map(item => ({ name: item.label, value: Math.max(Number(item.value ?? 0), 0) })) }] }))
let request = 0
async function load() { const validation = dateRangeError(dates.value?.[0], dates.value?.[1]); if (validation) { ElMessage.warning(validation); return } const current = ++request; busy.value = true; error.value = null; loaded.value = false; overview.value = undefined; sales.value = []; funnel.value = undefined; ads.value = undefined; skuSales.value = []; after.value = undefined; const filters = { marketCode: marketCode.value, dateFrom: dates.value[0], dateTo: dates.value[1], comparisonPeriod: period.value }, suffix = `?${query(filters)}`; try { const values = await Promise.all([api<Overview>('/dashboard/overview' + suffix), api<Trend[]>('/dashboard/sales-trend' + suffix), api<Funnel>('/dashboard/product-funnel' + suffix), api<Ads>('/dashboard/ads' + suffix), api<SkuSales[]>('/dashboard/sku-sales' + suffix), api<AfterSales>('/dashboard/after-sales' + suffix)]); if (current !== request) return;[overview.value, sales.value, funnel.value, ads.value, skuSales.value, after.value] = values; applied.value = filters; loaded.value = true } catch (e) { if (current === request) error.value = e as Error } finally { if (current === request) busy.value = false } }
function choosePeriod(value: string) { period.value = value; if (value === 'custom') return; dates.value = presetRange(value) as [string, string]; void load() }
async function exportData(type: string) { exporting.value = true; try { await download(`/exports/${type}`, applied.value); ElMessage.success('导出文件已下载') } catch (e) { ElMessage.error((e as Error).message) } finally { exporting.value = false } }
onMounted(async () => { void load(); try { markets.value = await api<Market[]>('/system/markets') } catch {/* MY is the documented initial market; no invented additional markets. */ } })
</script>
<template>
  <div class="dashboard-shell">
    <h1 class="sr-only">数据看板</h1>
    <div class="surface dashboard-filters">
      <div class="actions"><label class="inline-filter"><span>业务日期</span><el-radio-group v-model="period" size="small"
            aria-label="业务日期快捷选择" @change="choosePeriod"><el-radio-button
              value="today">今日</el-radio-button><el-radio-button value="yesterday">昨日</el-radio-button><el-radio-button
              value="week">本周</el-radio-button><el-radio-button value="month">本月</el-radio-button><el-radio-button
              value="custom">自定义</el-radio-button></el-radio-group><el-date-picker v-if="period === 'custom'"
            v-model="dates" type="daterange" value-format="YYYY-MM-DD" format="YYYY/MM/DD" start-placeholder="开始日期"
            end-placeholder="结束日期" :clearable="false" /></label><label class="inline-filter"><span>市场</span><el-select
            v-model="marketCode" aria-label="市场" style="width:170px"><el-option
              v-for="m in markets.length ? markets : [{ marketCode: 'MY', marketName: '马来西亚', currencyCode: 'MYR' }]"
              :key="m.marketCode" :value="m.marketCode"
              :label="`${m.marketName} (${m.marketCode})`" /></el-select></label><el-button type="primary"
          :loading="busy" @click="load">查询</el-button></div>
      <el-button text aria-label="刷新数据" :disabled="busy" @click="load"><el-icon>
          <Refresh />
        </el-icon></el-button>
    </div>
    <RequestError :error="error" @retry="load" />
    <el-alert v-if="productNotice" class="data-alert" :type="funnel?.source === 'NONE' ? 'warning' : 'info'"
      :closable="false" :title="productNotice" />
    <div v-if="busy" class="surface panel" aria-label="正在加载经营数据" aria-busy="true"><el-skeleton :rows="12" animated />
    </div>
    <template v-else>
      <section class="kpi-grid" aria-label="核心经营指标">
        <article v-for="k in kpis" :key="k.key" class="surface kpi">
          <div class="kpi-top"><span class="metric-icon"><el-icon :size="25">
                <component :is="metricIcons[k.key]" />
              </el-icon></span>
            <div class="kpi-label"><span>{{ k.label }}</span><el-tooltip :content="k.hint" placement="top"><button
                  class="info-button" :aria-label="k.hint"><el-icon>
                    <InfoFilled />
                  </el-icon></button></el-tooltip></div>
          </div>
          <div class="kpi-value number"><small v-if="k.money">{{ currencyDisplay }}</small>{{
            number(getMetric(k.key)?.value, k.money ? 2 : 0) }}</div>
          <MetricChange :value="getMetric(k.key)?.comparePrevious" :status="getMetric(k.key)?.comparePreviousStatus"
            :direction="k.direction" :label="compare" />
          <MetricChange v-if="single" :value="getMetric(k.key)?.compare7dAvg"
            :status="getMetric(k.key)?.compare7dAvgStatus" :direction="k.direction" label="较前 7 日均值" />
          <Chart v-if="getMetric(k.key)?.trend?.length"
            :option="spark(getMetric(k.key), k.key === 'refundAmount' ? '#E14B50' : k.key === 'aov' ? '#7EA6C9' : k.key === 'soldQty' ? '#8AA9C5' : '#355D86')"
            :height="43" :label="`${k.label}小趋势`" />
          <div v-else class="no-spark">{{ loaded ? '暂无趋势数据' : '等待数据加载' }}</div>
        </article>
        <article class="surface kpi source-kpi">
          <div class="kpi-top"><span class="metric-icon"><el-icon :size="25">
                <Goods />
              </el-icon></span>
            <div class="kpi-label"><span>销量分布</span><el-tooltip content="自营销量 = 订单数 - 达人销量；达人销量来自达人订单导入"
                placement="top"><button class="info-button" aria-label="销量分布计算说明"><el-icon>
                    <InfoFilled />
                  </el-icon></button></el-tooltip></div>
          </div>
          <div v-if="sourceSalesTotal" class="status-layout source-layout">
            <div class="donut">
              <Chart :option="sourceSalesOption" :height="126" label="自营与达人销量分布环形图" />
              <div class="donut-center"><strong class="number">{{ number(sourceSalesTotal) }}</strong><small
                  class="muted">来源合计</small></div>
            </div>
            <div class="status-list">
              <div v-for="(item, i) in sourceSales" :key="item.label"><span class="status-dot"
                  :class="`dot-${i}`" /><span>{{ item.label }}</span><small class="number muted">{{ pct(sourceSalesTotal
                    ? Number(item.value ?? 0) / sourceSalesTotal : null) }}</small><b class="number">({{
                    number(item.value) }})</b></div>
            </div>
          </div>
          <div v-else class="no-spark">{{ loaded ? '暂无销量数据' : '等待数据加载' }}</div>
        </article>
      </section>
      <section class="analysis-grid">
        <article class="surface panel sales-panel">
          <div class="panel-head">
            <h2>销售趋势</h2><small class="legend-label blue">■ GMV ({{ currencyDisplay }})</small><small
              class="legend-label purple">● 订单数</small>
          </div>
          <Chart v-if="sales.length" :option="combo(sales, 'gmv', 'orderCount', `GMV (${currencyDisplay})`, '订单数')"
            :height="255" label="GMV 柱状图与订单数折线图" /><el-empty v-else description="此范围暂无销售趋势" :image-size="64" />
        </article>
        <article class="surface panel funnel-panel">
          <div class="panel-head">
            <h2>商品转化漏斗 <small class="muted">从曝光到下单的转化表现</small></h2>
          </div>
          <div class="funnel-rows">
            <div v-for="(s, i) in stages" :key="s.label" class="funnel-row">
              <div class="funnel-shape" :style="{ '--stage': i }"></div>
              <div class="funnel-count"><small>{{ s.label }}</small><strong class="number">{{ number(s.value)
              }}</strong></div>
              <div v-if="s.rateLabel" class="funnel-rate"><small class="muted">{{ s.rateLabel }}</small><span
                  class="number">{{ pct(s.rate) }}</span></div>
              <div v-else></div>
              <div class="funnel-extra"><small class="muted">{{ s.aux }}</small><span class="number">{{ number(s.extra)
              }}</span></div>
            </div>
          </div>
        </article>
        <article class="surface panel ads-panel">
          <div class="panel-head">
            <h2>GMV Max 广告效果 <small class="muted">广告投放数据</small></h2><small>币种: {{ adCurrencyDisplay
            }}</small>
          </div>
          <div class="ad-metrics">
            <button v-for="m in adMetrics" :key="m.key" class="ad-metric" :class="{ selected: selectedAdMetricKeys.includes(m.key) }" type="button" :aria-pressed="selectedAdMetricKeys.includes(m.key)" @click="toggleAdMetric(m.key)"><small class="muted">{{ m.label }}</small><strong
                class="number"><small v-if="m.money">{{ adCurrencyDisplay }} </small>{{ number(m.metric?.value, 2) }}</strong>
              <MetricChange :value="m.metric?.comparePrevious" :status="m.metric?.comparePreviousStatus"
                :direction="m.direction" />
            </button>
          </div>
          <Chart v-if="ads?.trend?.length && selectedAdMetrics.length"
            :option="dynamicCombo(adChartRows, selectedAdMetrics)"
            :height="146" :label="`${adChartTitle}趋势图`" />
          <div v-else-if="ads?.trend?.length" class="empty-inline">请至少选择一个指标</div>
          <div v-else class="empty-inline">此范围暂无广告趋势</div>
        </article>
      </section>
      <section class="bottom-grid">
        <article class="surface panel">
          <div class="panel-head">
            <h2>SKU销量分布</h2>
          </div>
          <div v-if="skuSalesTotal" class="status-layout">
            <div class="donut">
              <Chart :option="skuSalesOption" :height="166" label="SKU销量分布环形图" />
              <div class="donut-center"><strong class="number">{{ number(skuSalesTotal) }}</strong><small
                  class="muted">有效销量</small></div>
            </div>
            <div class="status-list">
              <div v-for="(s, i) in skuSales" :key="s.sellerSku"><span class="status-dot"
                  :class="`dot-${i % 6}`" /><span>{{
                    s.displayName }}</span><small class="number muted">{{ pct(s.ratio) }}</small><b class="number">({{
                    number(s.sales) }})</b></div>
            </div>
          </div><el-empty v-else description="暂无已配置 SKU 销量数据" :image-size="52" />
        </article>
        <article class="surface panel after-panel">
          <div class="panel-head">
            <h2>售后情况 <small class="muted">基于订单数据统计</small></h2>
          </div>
          <div class="after-grid">
            <div v-for="m in [...currentAfterMetrics, ...cumulativeAfterMetrics]" :key="m.label"><span
                class="after-icon">×</span>
              <div><small class="muted">{{ m.label }}</small><strong class="number"><small
                    v-if="'money' in m && m.money">{{ currencyDisplay }} </small>{{ number(m.metric?.value, ('money' in
                      m
                      && m.money) ? 2 : 0) }}</strong>
                <MetricChange v-if="m.metric" :value="m.metric.comparePrevious" :status="m.metric.comparePreviousStatus"
                  direction="lower" :label="compare" />
              </div>
            </div>
          </div>
        </article>
        <article class="surface panel">
          <div class="panel-head">
            <h2>商品访客与转化</h2>
          </div>
          <div class="store-grid">
            <div
              v-for="(m, i) in [{ label: '商品访客数', metric: overview?.visitorCount }, { label: '店铺转化率', metric: overview?.conversionRate }]"
              :key="m.label"><span class="store-icon"><el-icon>
                  <View v-if="!i" />
                  <Goods v-else />
                </el-icon></span>
              <div><small class="muted">{{ m.label }}</small><strong class="number">{{
                i ? pct(m.metric?.value) : number(m.metric?.value) }}</strong>
                <MetricChange :value="m.metric?.comparePrevious" :status="m.metric?.comparePreviousStatus"
                  :label="compare" />
              </div>
              <Chart v-if="m.metric?.trend?.length" :option="spark(m.metric, i ? '#7EA6C9' : '#355D86')" :height="58"
                :label="m.label + '趋势'" />
              <div v-else class="no-spark">暂无趋势数据</div>
            </div>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>
<style scoped>
.dashboard-shell {
  max-width: none;
  margin: 0;
  padding: 0 0 16px
}

.dashboard-shell>h1.sr-only {
  position: static;
  width: auto;
  height: auto;
  padding: 0;
  margin: 0 0 16px;
  overflow: visible;
  clip: auto;
  white-space: normal
}

.dashboard-filters {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: rgba(255, 255, 255, .86)
}

.inline-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--hm-text-secondary)
}

.inline-filter .el-date-editor {
  width: 260px
}

.data-alert {
  margin-bottom: 16px
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px
}

.kpi {
  height: 214px;
  padding: 16px 16px 9px;
  overflow: hidden
}

.kpi-top {
  display: flex;
  align-items: center;
  gap: 10px
}

.metric-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  color: #7A8796;
  background: var(--hm-bg-soft);
  border: 1px solid var(--hm-border)
}

.kpi-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  color: var(--hm-text)
}

.info-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--hm-text-tertiary);
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px
}

.info-button:hover {
  color: var(--hm-text)
}

.kpi-value {
  font-size: 28px;
  letter-spacing: -.55px;
  font-weight: 650;
  color: #17202B;
  white-space: nowrap;
  margin: 13px 0 6px;
  display: flex;
  gap: 5px;
  align-items: baseline
}

.kpi-value small {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0;
  color: var(--hm-text-secondary)
}

.no-spark {
  height: 43px;
  display: flex;
  align-items: center;
  color: var(--hm-text-secondary);
  font-size: 10px
}

.analysis-grid {
  display: grid;
  grid-template-columns: 1.23fr .76fr .98fr;
  gap: 16px;
  margin-bottom: 16px
}

.panel {
  padding: 16px;
  min-width: 0
}

.panel-head {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 9px;
  margin-bottom: 14px;
  min-height: 24px
}

.panel-head h2 {
  font-size: 15px;
  line-height: 1.35;
  font-weight: 650;
  color: var(--hm-text);
  white-space: nowrap
}

.panel-head h2::before {
  content: '';
  display: inline-block;
  width: 2px;
  height: 15px;
  margin: 0 8px 0 0;
  background: var(--hm-accent);
  vertical-align: -2px
}

.panel-head h2 small {
  font-weight: 400;
  font-size: 11px;
  margin-left: 6px;
  color: var(--hm-text-secondary)
}

.panel-head>small {
  font-size: 11px;
  color: var(--hm-text-secondary)
}

.legend-label {
  font-size: 11px
}

.blue {
  color: #5F8FBE
}

.purple {
  color: #AAB7C5
}

.funnel-rows {
  display: grid;
  gap: 6px
}

.funnel-row {
  display: grid;
  grid-template-columns: 74px 1.22fr .72fr .86fr;
  align-items: center;
  gap: 7px;
  background: #fff;
  border: 1px solid var(--hm-border);
  border-radius: 8px;
  padding: 5px 7px;
  height: 57px
}

.funnel-row small {
  font-size: 10.5px;
  display: block;
  white-space: nowrap;
  color: #667085
}

.funnel-row strong {
  font-size: 18px;
  line-height: 1;
  display: block;
  margin-top: 4px;
  color: var(--hm-text)
}

.funnel-row span.number {
  font-size: 14px;
  display: block;
  margin-top: 5px;
  color: var(--hm-text)
}

.funnel-shape {
  width: calc(72px - var(--stage)*9px);
  height: 49px;
  justify-self: center;
  background: #5F8FBE;
  opacity: calc(1 - var(--stage)*.1);
  clip-path: polygon(0 0, 100% 0, 85% 100%, 15% 100%)
}

.funnel-row:nth-child(2) .funnel-shape {
  background: #7EA6C9
}

.funnel-row:nth-child(3) .funnel-shape {
  background: #AAB7C5
}

.funnel-row:nth-child(4) .funnel-shape {
  background: #D9E2EB
}

.funnel-rate,
.funnel-extra {
  border-left: 1px solid var(--hm-divider);
  padding-left: 7px
}

.ad-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin: 3px 0 8px
}

.ad-metric {
  appearance: none;
  font: inherit;
  text-align: left;
  color: inherit;
  background: transparent;
  cursor: pointer;
  border: 0;
  border-radius: 8px;
  padding: 8px 10px;
  transition: background-color 150ms ease, box-shadow 150ms ease, transform 150ms ease
}

.ad-metric.selected {
  background: var(--hm-accent-soft);
  box-shadow: inset 0 2px 5px rgba(64, 95, 125, .12), inset 0 -1px 0 rgba(255, 255, 255, .6);
  transform: translateY(1px)
}

.ad-metric.selected strong {
  color: var(--hm-accent-hover)
}

.ad-metrics small {
  font-size: 10.5px;
  white-space: nowrap
}

.ad-metrics strong {
  display: block;
  color: var(--hm-text);
  font-size: 16px;
  letter-spacing: -.35px;
  font-weight: 650;
  line-height: 1;
  margin: 9px 0 4px;
  white-space: nowrap
}

.bottom-grid {
  display: grid;
  grid-template-columns: .92fr .88fr 1.8fr;
  gap: 16px
}

.status-layout {
  display: grid;
  grid-template-columns: 52% 48%;
  align-items: center
}

.donut {
  position: relative
}

.donut-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none
}

.donut-center strong {
  font-size: 24px;
  font-weight: 650;
  color: var(--hm-text)
}

.donut-center small {
  font-size: 10px;
  margin-top: 3px
}

.status-list {
  display: grid;
  gap: 10px
}

.status-list>div {
  display: grid;
  grid-template-columns: 8px 1fr auto auto;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  color: var(--hm-text)
}

.status-list b {
  font-weight: 400
}

.status-list small {
  font-size: 10.5px
}

.source-layout {
  grid-template-columns: 46% 54%;
  margin-top: -2px
}

.source-kpi .status-list {
  gap: 8px
}

.source-kpi .status-list>div {
  grid-template-columns: 7px 1fr;
  gap: 5px;
  font-size: 10.5px
}

.source-kpi .status-list small,
.source-kpi .status-list b {
  grid-column: 2;
  margin-top: -4px
}

.source-kpi .status-list .dot-1 {
  background: #E14B50
}

.source-kpi .donut-center strong {
  font-size: 17px
}

.source-kpi .donut-center small {
  font-size: 9px
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #5F8FBE
}

.dot-1 {
  background: #7EA6C9
}

.dot-2 {
  background: #AAB7C5
}

.dot-3 {
  background: #C7D3DF
}

.dot-4 {
  background: #E14B50
}

.dot-5 {
  background: #D9E2EB
}

.after-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 14px
}

.after-grid>div {
  display: flex;
  gap: 10px;
  border-bottom: 1px solid var(--hm-divider);
  padding: 8px 1px 9px
}

.after-grid>div:nth-last-child(-n+2) {
  border-bottom: 0
}

.after-icon {
  margin-top: 1px;
  flex: 0 0 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #FFF0F0;
  color: var(--hm-negative);
  font-size: 22px;
  line-height: 1
}

.after-grid small,
.store-grid small {
  font-size: 10.5px
}

.after-grid strong {
  display: block;
  color: var(--hm-text);
  font-size: 17px;
  font-weight: 650;
  line-height: 1;
  margin: 5px 0 2px
}

.after-grid strong small {
  font-size: 10px;
  font-weight: 600
}

.store-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px
}

.store-grid>div {
  min-width: 0;
  position: relative;
  display: grid;
  grid-template-columns: 46px 1fr;
  grid-template-rows: auto 60px;
  background: var(--hm-bg-soft);
  border: 1px solid var(--hm-border);
  padding: 11px;
  border-radius: 9px;
  overflow: hidden
}

.store-grid>div :deep([role="img"]) {
  grid-column: 1/-1
}

.store-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  color: #5F8FBE;
  background: #fff;
  border: 1px solid var(--hm-border);
  border-radius: 8px;
  font-size: 21px
}

.store-grid strong {
  color: var(--hm-text);
  font-size: 24px;
  font-weight: 650;
  line-height: 1;
  display: block;
  margin: 7px 0 3px
}

@media(max-width:1300px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr))
  }

  .analysis-grid {
    grid-template-columns: 1.1fr 1fr
  }

  .sales-panel {
    grid-column: 1/-1
  }

  .bottom-grid {
    grid-template-columns: 1fr 1fr
  }

  .bottom-grid>article:last-child {
    grid-column: 1/-1
  }
}

@media(max-width:767px) {
  .dashboard-shell {
    padding: 0 0 12px
  }

  .dashboard-filters {
    padding: 12px;
    align-items: flex-start
  }

  .dashboard-filters>.actions {
    width: 100%
  }

  .inline-filter {
    width: 100%;
    display: grid;
    gap: 6px
  }

  .inline-filter .el-date-editor {
    width: 100%
  }

  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px
  }

  .kpi {
    height: 194px;
    padding: 12px
  }

  .metric-icon {
    width: 34px;
    height: 34px
  }

  .kpi-value {
    font-size: 20px;
    letter-spacing: -.8px
  }

  .kpi-value small {
    font-size: 11px
  }

  .analysis-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
    gap: 12px
  }

  .sales-panel,
  .bottom-grid>article:last-child {
    grid-column: auto
  }

  .panel-head h2 {
    font-size: 15px;
    white-space: normal
  }

  .funnel-row {
    grid-template-columns: 58px 1fr .7fr .8fr
  }

  .funnel-shape {
    width: calc(56px - var(--stage)*7px)
  }

  .ad-metrics>.ad-metric {
    padding: 8px
  }

  .ad-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px 8px
  }

  .ad-metrics strong {
    font-size: 16px
  }

  .status-layout {
    grid-template-columns: 1fr 1fr
  }

  .source-layout {
    grid-template-columns: 46% 54%
  }

  .after-grid {
    grid-template-columns: 1fr
  }

  .after-grid>div:nth-last-child(2) {
    border-bottom: 1px solid var(--hm-divider)
  }

  .store-grid {
    gap: 8px
  }

  .store-grid>div {
    padding: 8px;
    grid-template-columns: 40px 1fr
  }
}
</style>
