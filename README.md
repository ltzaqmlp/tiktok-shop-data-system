# 跨境电商经营数据平台（数据口径修正版）

TikTok Shop 企业内部经营系统，包含 Vue 3 前端、Spring Boot 后端、PostgreSQL、Excel 导入、Dashboard、RBAC、审计和明细导出。

## 本次修正（2026-09-07）

本版针对实际提供的 4 类 TikTok Shop Excel 做了字段级核对和数据口径修正：

- **Shop Analytics** 改为经营驾驶舱每日核心指标的主数据源：GMV、订单数、商品成交件数、SKU 订单数、退款金额、商品访客数、转化率。
- 新增 Shop Analytics 字段入库：`sku_order_count`、`refund_amount`、客户数、页面浏览次数、曝光/点击及去重曝光/点击。
- **商品报表支持单日和日期区间**。多日商品报表不会再错误分摊/归属到某一天，而是保存到 `fact_product_period`，仅在看板选择的日期范围与文件区间**精确一致**时用于完整商品漏斗。
- 商品漏斗 CTOR 改为 `SKU 订单数 ÷ 商品点击量`，与 TikTok 商品报表的“CTOR（SKU 订单）”口径一致。
- 若当前日期没有可精确匹配的商品报表，商品漏斗会用 Shop Analytics 中的曝光、点击、SKU 订单等字段做**部分回退**；加购相关字段保持缺失，不伪造为 0。
- 订单明细额外保存 `Sku Quantity of return` 和 `Order Refund Amount`，用于核对；由于订单文件缺少权威退款发生日期，驾驶舱的每日退款金额仍以 Shop Analytics 为准。
- 广告 Campaign overview 的 `成本 / SKU orders (Current shop) / Gross revenue (Current shop) / 币种` 可直接识别；广告币种与店铺币种分开处理。
- “较昨日 / 较前 7 日均值”区分无历史数据、数据不完整、基线为 0 等情况，不再全部显示为 `—`。
- 业务日期快捷项：**今日 / 昨日 / 本周 / 本月 / 自定义**。

详细设计和实测结果见 `OPTIMIZATION_NOTES.md`、`DATA_VALIDATION.md`。

## 数据源职责

| 数据源 | 系统用途 |
| --- | --- |
| `SHOP_ANALYTICS` | 每日核心 KPI、销售趋势、访客/转化、每日退款金额、商品漏斗部分回退 |
| `PRODUCT_DAILY` | 商品维度及完整商品漏斗；支持单日文件和区间汇总文件 |
| `ORDER_DETAIL` | 订单状态、取消订单、订单/退货退款字段核对 |
| `GMV_MAX_CAMPAIGN` | 广告花费、广告归因收入、广告订单、ROI、CPO |

## 升级已有数据库后的重要操作

Flyway 会自动执行 `V2__correct_data_source_metrics.sql`。但旧版本导入的 Shop Analytics 数据没有保存 SKU 订单数和退款金额等新字段，所以**升级后请重新导入 Shop Analytics 文件一次**。

如果系统提示文件重复，请由管理员选择“强制重跑”。建议这 4 份源文件都重新导入一次，以便新字段和商品区间表完整落库。

## 部署

需要 Docker Desktop / Docker Compose：

```powershell
Copy-Item deploy/.env.example deploy/.env
# 修改 deploy/.env 中的密码
Set-Location deploy
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
```

## 前端开发与测试

```bash
cd frontend
npm install
npm run test
npm run build
```

## 后端测试

Java 21 + Maven 3.9：

```bash
cd backend
mvn -B test
```


## 版本确认与无缓存升级

当前修复版本：`2026.09.07-product-period-v3`。

如果页面仍显示“商品日数据”或导入多日 `product_list` 时提示“商品日数据必须导出单日范围”，说明服务器仍在运行旧前端/旧后端镜像。

Linux/macOS：

```bash
cd deploy
./update-no-cache.sh
```

Windows PowerShell：

```powershell
cd deploy
.\update-no-cache.ps1
```

升级后访问 `/api/v1/system/health`，响应中的 `buildVersion` 必须为 `2026.09.07-product-period-v3`。数据导入页标题下方也会显示同一个版本号。

本版本允许 `PRODUCT_DAILY` 入口接收类似 `数据分析日期: 30/08/2026~05/09/2026` 的多日商品报表，并保存到 `fact_product_period`，不会把整个区间错误记到某一个业务日。
