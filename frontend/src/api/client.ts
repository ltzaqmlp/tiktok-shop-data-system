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
const MOCK: Record<string, unknown> = {
  '/auth/login': {},
  '/auth/session': { authenticated: true },
  '/auth/me': { id: '1', username: 'demo', displayName: '演示管理员', roles: [{ id: '1', roleCode: 'ADMIN', roleName: '管理员' }], permissions: ['export'], mustChangePassword: false, status: 'ACTIVE', lastLoginAt: new Date().toISOString() },
  '/me/menus': [
    { id: '1', name: '经营驾驶舱', menuCode: 'dashboard', routePath: '/dashboard', sortOrder: 1, enabled: true },
    { id: '8', name: '日报', menuCode: 'daily.report', routePath: '/daily-reports', sortOrder: 2, enabled: true },
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
  '/dashboard/after-sales': { refundAmount: { value: 8430.00, comparePrevious: -0.082, comparePreviousStatus:'OK' }, refundedQty: { value: 124, comparePrevious: -0.064, comparePreviousStatus:'OK' }, refundCustomerCount: { value: 89, comparePrevious: -0.071, comparePreviousStatus:'OK' }, cancelOrderCount: { value: 92, comparePrevious: 0.043, comparePreviousStatus:'OK' } },
  '/imports': { items: [], total: 0 },
  '/daily-reports': [{ reportDate: _today, groupName: '视频组', roleMarket: '美国编导', todayFocus: '完成精华油脚本审核', keyResult: '审核 8 条脚本，通过 6 条', needBossSupport: '无', tomorrowFocus: '复核修改后的 2 条脚本', submissionStatus: 'SUBMITTED', reporterName: '演示管理员', createdBy: '1', taskId: '1', sortOrder: 0, workModule: '内容审核', workDetail: '审核精华油口播脚本', planDelivery: '审核 8 条脚本', actualResult: '通过 6 条；2 条退回修改', completionStatus: '已完成', issueNextStep: '编导修改开头，明早复核', resultLink: '' }],
  '/daily-reports/mine': { reportDate: _today, groupName: '视频组', roleMarket: '美国编导', todayFocus: '完成精华油脚本审核', keyResult: '审核 8 条脚本，通过 6 条', needBossSupport: '无', tomorrowFocus: '复核修改后的 2 条脚本', submissionStatus: 'SUBMITTED', reporterName: '演示管理员', tasks: [] },
  '/admin/users': { items: [{ id: '1', username: 'demo', displayName: '演示管理员', status: 'ACTIVE', roles: [{ id: '1', roleCode: 'ADMIN', roleName: '管理员' }], lastLoginAt: new Date().toISOString() }], total: 1 },
  '/admin/roles': [{ id: '1', roleCode: 'ADMIN', roleName: '管理员', description: '系统管理员，拥有全部权限', enabled: true, menuIds: ['1','2','3','4','5','6','7'] }, { id: '2', roleCode: 'BOSS', roleName: '老板', description: '查看驾驶舱与导出', enabled: true, menuIds: ['1'] }],
  '/admin/menus': [{ id: '1', name: '经营驾驶舱', menuCode: 'dashboard', routePath: '/dashboard', sortOrder: 1, enabled: true }, { id: '2', name: '数据导入', menuCode: 'data-import', routePath: '/data-import', sortOrder: 2, enabled: true }],
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
