// ============================================================
// 演示模式（DEMO_MODE）：无需后端即可预览系统所有界面
// 仅本地开发可显式开启；生产构建始终使用真实后端。
// ============================================================
const DEMO_MODE = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE === 'true'

function makeDates(n: number) {
  const d = new Date(); d.setDate(d.getDate() - 1)
  return Array.from({ length: n }, (_, i) => { const t = new Date(d); t.setDate(t.getDate() - (n - 1 - i)); return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}` })
}
const _d30 = makeDates(30), _d7 = _d30.slice(-7), _yesterday = _d30[_d30.length - 1]
const _today = new Date().toLocaleDateString('en-CA')
const _ok={comparePreviousStatus:'OK',compare7dAvgStatus:'OK'}
const _dailyMarkets=[{marketCode:'MY',marketName:'马来西亚',currencyCode:'MYR'},{marketCode:'UK',marketName:'英国',currencyCode:'GBP'},{marketCode:'US',marketName:'美国',currencyCode:'USD'},{marketCode:'DE',marketName:'德国',currencyCode:'EUR'},{marketCode:'FR',marketName:'法国',currencyCode:'EUR'}]
const _dailyReport={id:'daily-demo-1',reportDate:_today,reportType:'EDITOR',marketCode:'MY',marketName:'马来西亚',reporterName:'演示剪辑',submissionStatus:'PENDING_DEPT',rejectionReason:'',notes:'等待素材确认，已完成今日交付。',blockers:'等待素材确认。',deliveryResults:{actualNewPublish:'https://example.test/publish-001'},submittedAt:new Date().toISOString(),plannedReviewVideos:0,actualReviewVideos:0,plannedValidBenchmark:0,actualValidBenchmark:0,plannedDeconstruction:0,actualDeconstruction:0,plannedCompleteScript:0,actualCompleteScript:0,plannedReadyScript:0,actualReadyScript:0,plannedNewPublish:3,actualNewPublish:2,plannedFirstReview:4,actualFirstReview:3,plannedReworkAcceptance:2,actualReworkAcceptance:1,plannedTest:0,actualTest:0,testGap:0,newAdjustPlan:0,adSpend:0,adGmv:0,roi:0,impressions:0,clicks:0,ctr:0,orders:0,expandedMaterial:0,stoppedMaterial:0}
const MOCK: Record<string, unknown> = {
  '/auth/login': {},
  '/auth/session': { authenticated: true },
  '/auth/me': { id: '1', username: 'demo', displayName: '演示管理员', roles: [{ id: '1', roleCode: 'ADMIN', roleName: '管理员' }], permissions: ['export'], mustChangePassword: false, status: 'ACTIVE', lastLoginAt: new Date().toISOString() },
  '/me/menus': [
    { id: '1', name: '数据看板', menuCode: 'dashboard', routePath: '/dashboard', sortOrder: 1, enabled: true },
    { id: '8', name: '日报', menuCode: 'daily.report', routePath: '/daily-reports', sortOrder: 2, enabled: true },
    { id: '10', name: '拍摄工单', menuCode: 'shooting.ticket', routePath: '/shooting-tickets', sortOrder: 3, enabled: true },
    { id: '2', name: '数据导入', menuCode: 'data-import', routePath: '/data-import', sortOrder: 2, enabled: true },
    { id: '9', name: '系统管理', menuCode: 'admin', sortOrder: 3, enabled: true, children: [
      { id: '3', name: '用户管理', menuCode: 'admin-users', routePath: '/admin/users', sortOrder: 1, enabled: true },
      { id: '4', name: '角色管理', menuCode: 'admin-roles', routePath: '/admin/roles', sortOrder: 2, enabled: true },
      { id: '5', name: '菜单管理', menuCode: 'admin-menus', routePath: '/admin/menus', sortOrder: 3, enabled: true },
      { id: '6', name: '操作日志', menuCode: 'admin-audit', routePath: '/admin/audit', sortOrder: 4, enabled: true },
      { id: '7', name: '登录日志', menuCode: 'admin-login-logs', routePath: '/admin/login-logs', sortOrder: 5, enabled: true },
    ] },
  ],
  '/system/markets': [{ marketCode: 'MY', marketName: '马来西亚', currencyCode: 'MYR' }, { marketCode: 'TH', marketName: '泰国', currencyCode: 'THB' }, { marketCode: 'SG', marketName: '新加坡', currencyCode: 'SGD' }],
  '/system/shops': [{ id: '101', marketCode: 'MY', shopName: 'MY 旗舰店' }, { id: '102', marketCode: 'MY', shopName: 'MY 专卖店' }],
  '/dashboard/overview': {
    currencyCode: 'MYR', productDataAvailable:true,
    gmv: { value: 285640.50, comparePrevious: 0.128, compare7dAvg: 0.054, ..._ok, trend: _d7.map((date, i) => ({ date, value: 240000 + [18000, 32000, 25000, 41000, 28000, 37000, 45000][i] })) },
    orderCount: { value: 1842, comparePrevious: 0.073, compare7dAvg: 0.021, ..._ok, trend: _d7.map((date, i) => ({ date, value: [1580, 1720, 1660, 1890, 1750, 1820, 1960][i] })) },
    soldQty: { value: 3215, comparePrevious: 0.095, compare7dAvg: 0.031, ..._ok },
    skuOrderCount: { value: 2876, comparePrevious: 0.061, compare7dAvg: 0.018, ..._ok },
    aov: { value: 155.07, comparePrevious: 0.051, compare7dAvg: 0.022, ..._ok },
    refundAmount: { value: 8430.00, comparePrevious: -0.082, compare7dAvg: -0.034, ..._ok },
    visitorCount: { value: 24680, comparePrevious: 0.112, comparePreviousStatus:'OK', trend: _d7.map((date, i) => ({ date, value: [20100, 22400, 21800, 25600, 23900, 24100, 26800][i] })) },
    conversionRate: { value: 0.0746, comparePrevious: -0.012, comparePreviousStatus:'OK', trend: _d7.map((date, i) => ({ date, value: [0.071, 0.074, 0.072, 0.078, 0.073, 0.075, 0.079][i] })) },
    dataFreshness: { latestBizDate: _yesterday, latestImportAt: new Date().toISOString() },
    dataCoverage:{shopAnalytics:{rowsInRange:1,daysInRange:1,firstBizDate:_yesterday,lastBizDate:_yesterday,latestBizDate:_yesterday},productDaily:{rowsInRange:20,daysInRange:1,firstBizDate:_yesterday,lastBizDate:_yesterday,latestBizDate:_yesterday},orderDetail:{rowsInRange:1842,daysInRange:1,firstBizDate:_yesterday,lastBizDate:_yesterday,latestBizDate:_yesterday},ads:{rowsInRange:12,daysInRange:1,firstBizDate:_yesterday,lastBizDate:_yesterday,latestBizDate:_yesterday}},
    productDataQuality:{productRows:20,soldQty:3215,skuOrderCount:2876,soldQtyNonZeroRows:20,skuOrderCountNonZeroRows:20}
  },
  '/dashboard/sales-trend': _d30.map((date, i) => ({ date, gmv: Math.round((220000 + Math.sin(i * 0.5) * 35000 + (i * 1200)) * 10) / 10, orderCount: Math.round(1500 + Math.sin(i * 0.4) * 280 + i * 8) })),
  '/dashboard/product-funnel': { impressions: 312450, clicks: 28960, addToCartCount: 8420, orderCount: 1842, ctr: 0.0927, addToCartRate: 0.2908, ctor: 0.2187, uniqueImpressions: 195600, uniqueClicks: 19840, addedUserCount: 6210, estimatedCustomerCount: 1654 },
  '/dashboard/ads': { currencyCode: 'MYR', spend: { value: 18640.00, comparePrevious: 0.054, comparePreviousStatus:'OK' }, attributedRevenue: { value: 94820.00, comparePrevious: 0.132, comparePreviousStatus:'OK' }, roi: { value: 5.09, comparePrevious: 0.073, comparePreviousStatus:'OK' }, cpo: { value: 10.12, comparePrevious: -0.036, comparePreviousStatus:'OK' }, trend: _d30.slice(-14).map((date, i) => ({ date, spend: Math.round(1100 + Math.sin(i) * 350 + i * 30), attributedRevenue: Math.round(5800 + Math.sin(i * 0.7) * 1800 + i * 160) })) },
  '/dashboard/order-status': [{ status: 'COMPLETED', count: 1124, ratio: 0.610 }, { status: 'SHIPPED', count: 386, ratio: 0.210 }, { status: 'PAID', count: 184, ratio: 0.100 }, { status: 'CANCELLED', count: 92, ratio: 0.050 }, { status: 'REFUNDED', count: 56, ratio: 0.030 }],
  '/dashboard/sku-sales': [{ displayName: '1瓶精华液', sellerSku: '12320JN-1', sales: 94, ratio: 0.3507 }, { displayName: '2瓶精华液', sellerSku: '12320JN-11', sales: 130, ratio: 0.4851 }, { displayName: '1瓶精华液+刮痧板', sellerSku: '12320JN-GS', sales: 42, ratio: 0.1567 }, { displayName: '刮痧板', sellerSku: '12320FJGSS-1', sales: 2, ratio: 0.0075 }],
  '/dashboard/after-sales': { refundAmount: { value: 8430.00, comparePrevious: -0.082, comparePreviousStatus:'OK' }, refundedQty: { value: 124, comparePrevious: -0.064, comparePreviousStatus:'OK' }, refundCustomerCount: { value: 89, comparePrevious: -0.071, comparePreviousStatus:'OK' }, cancelOrderCount: { value: 92, comparePrevious: 0.043, comparePreviousStatus:'OK' } },
  '/imports': { items: [], total: 0 },
  '/daily-reports/context': { role:'VIEWER', marketCode:'', markets:_dailyMarkets },
  '/daily-reports/summary': _dailyMarkets.map((market,index)=>({marketCode:market.marketCode,marketName:market.marketName,plannedReviewVideos:0,actualReviewVideos:0,plannedValidBenchmark:0,actualValidBenchmark:0,plannedDeconstruction:0,actualDeconstruction:0,plannedCompleteScript:0,actualCompleteScript:0,plannedReadyScript:0,actualReadyScript:0,plannedNewPublish:index===0?3:0,actualNewPublish:index===0?2:0,plannedFirstReview:index===0?4:0,actualFirstReview:index===0?3:0,plannedReworkAcceptance:index===0?2:0,actualReworkAcceptance:index===0?1:0})),
  '/daily-reports/summary/details': [_dailyReport],
  '/daily-reports/summary/review': {id:'summary-demo-1',reportDate:_today,todayImportantResult:'完成本日重点内容交付',needBossSupport:'',tomorrowFocus:'推进明日排期',submissionStatus:'APPROVED',rejectionReason:''},
  '/daily-reports/market': [_dailyReport],
  '/daily-reports/ads': [{..._dailyReport,id:'ad-demo-1',reporterId:'1',reportType:'ADS_BUYER',reporterName:'演示投手',submissionStatus:'APPROVED',plannedTest:5,actualTest:3,testGap:2,newAdjustPlan:2,adSpend:12.5,adGmv:50,roi:4,impressions:1000,clicks:80,ctr:.08,orders:4,expandedMaterial:2,stoppedMaterial:1}],
  '/daily-reports/mine/content': _dailyReport,
  '/daily-reports/mine/ads': [{..._dailyReport,id:'ad-demo-1',reportType:'ADS_BUYER',plannedTest:5,actualTest:3,testGap:2,newAdjustPlan:2,adSpend:12.5,adGmv:50,roi:4,impressions:1000,clicks:80,ctr:.08,orders:4,expandedMaterial:2,stoppedMaterial:1}],
  '/shooting-tickets/context': { role:'ADMIN', markets:_dailyMarkets, taskTypes:[{code:'SCRIPT_SHOOT',name:'脚本拍摄'},{code:'LIBRARY',name:'素材库补充'},{code:'RESHOOT',name:'补拍'}] },
  '/shooting-tickets': [{id:'shoot-demo-1',ticketNo:'PS20260909-0001',createdAt:new Date().toISOString(),marketCode:'MY',regionName:'马来西亚',taskType:'SCRIPT_SHOOT',shotRequirement:'产品正面、背面、包装开合，按脚本完成三组构图。',plannedValidShotCount:8,deadline:new Date(Date.now()+86400000).toISOString(),shooterId:null,shooterName:null,directorName:'演示编导',sku:'',actualValidShotCount:null,materialNotes:'',actualDeliveredAt:null,status:'PENDING_SHOOT',rejectionStage:null,rejectionReason:''}],
  '/admin/users': { items: [{ id: '1', username: 'demo', displayName: '演示管理员', status: 'ACTIVE', roles: [{ id: '1', roleCode: 'ADMIN', roleName: '管理员' }], lastLoginAt: new Date().toISOString() }], total: 1 },
  '/admin/roles': [{ id: '1', roleCode: 'ADMIN', roleName: '管理员', description: '系统管理员，拥有全部权限', enabled: true, menuIds: ['1','2','3','4','5','6','7'] }, { id: '2', roleCode: 'BOSS', roleName: '老板', description: '查看数据看板与导出', enabled: true, menuIds: ['1'] }],
  '/admin/menus': [{ id: '1', name: '数据看板', menuCode: 'dashboard', routePath: '/dashboard', sortOrder: 1, enabled: true }, { id: '2', name: '数据导入', menuCode: 'data-import', routePath: '/data-import', sortOrder: 2, enabled: true }],
  '/admin/audit': { items: [], total: 0 },
  '/admin/login-logs': { items: [{ id: '1', username: 'demo', ip: '127.0.0.1', success: true, createdAt: new Date().toISOString(), userAgent: navigator.userAgent }], total: 1 },
  '/admin/org-units': [{ id: '1', name: '总公司', children: [{ id: '2', name: '电商部', children: [] }] }],
}

function mockMatch(path: string) {
  const base = path.split('?')[0]
  return Object.keys(MOCK).filter(k => base === k || base.startsWith(k + '/')).sort((a,b) => b.length-a.length)[0]
}

export class ApiError extends Error { constructor(message: string, public code = '', public requestId = '', public status = 0) { super(message) } }
export function query(params: object) { return new URLSearchParams(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null).map(([k, v]) => [k, String(v)])).toString() }
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  if (DEMO_MODE) {
    const key = mockMatch(path)
    if (key !== undefined) { await new Promise(r => setTimeout(r, 200)); return MOCK[key] as T }
  }
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), options.body instanceof FormData ? 300000 : 30000)
  try {
    const response = await fetch(`/api/v1${path}`, { ...options, credentials: 'same-origin', signal: options.signal ?? controller.signal, headers: { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), 'X-Requested-With': 'XMLHttpRequest', ...options.headers } })
    const json = await response.json().catch(() => null)
    if (!response.ok || !json?.success) {
      const error = new ApiError(json?.error?.message ?? `请求失败（${response.status}），请稍后重试`, json?.error?.code, json?.requestId, response.status)
      if (response.status === 401) window.dispatchEvent(new CustomEvent('session-expired'))
      if (error.code === 'AUTH_PASSWORD_CHANGE_REQUIRED') window.dispatchEvent(new CustomEvent('password-required'))
      throw error
    }
    return json.data as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(error instanceof Error && error.name === 'AbortError' ? '请求超时，请重试' : '无法连接服务，请检查网络后重试')
  } finally { clearTimeout(timeout) }
}
export const save = <T>(path: string, data: unknown, method = 'POST') => api<T>(path, { method, body: JSON.stringify(data) })
export async function download(path: string, params: object) {
  if (DEMO_MODE) { await new Promise(r => setTimeout(r, 500)); alert('演示模式：导出功能需要连接真实后端后才可使用'); return }
  const response = await fetch(`/api/v1${path}?${query(params)}`, { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' } })
  if (!response.ok) { const json = await response.json().catch(() => null); throw new ApiError(json?.error?.message ?? '导出失败，请重试', json?.error?.code, json?.requestId, response.status) }
  const blob = await response.blob(); const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url
  const encoded = response.headers.get('Content-Disposition')?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  a.download = encoded ? decodeURIComponent(encoded) : `明细_${new Date().toISOString().slice(0, 10)}.xlsx`
  a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
