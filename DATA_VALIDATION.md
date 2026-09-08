# 4 份真实 Excel 的回归校验结果

本次修改按以下实际文件字段校验：

- Campaign overview data 20260701 - 20260905
- product_list_20260830
- Shop Analytics_Key metrics_20260905
- 全部订单 2026-09-05

## 表头映射结果

4 个数据源的 REQUIRED 字段均能匹配；新增确认可识别：

- Shop Analytics：SKU 订单数、退款金额、客户数、页面浏览次数、商品曝光/点击、去重曝光/点击
- 商品报表：商品成交件数、SKU 订单数、曝光、点击、加购、退款商品件数、退款客户数
- 订单：Sku Quantity of return、Order Refund Amount、Created Time
- 广告：成本、SKU orders (Current shop)、Gross revenue (Current shop)、币种

## 2026-09-05 核心 KPI 预期

Shop Analytics 原始行：

| 指标 | 值 |
| --- | ---: |
| GMV | 59.65 |
| 订单数 | 2 |
| 商品成交件数 | 2 |
| SKU 订单数 | 2 |
| 商品访客数 | 280 |
| 转化率 | 0.7142857% |
| 商品曝光次数 | 4,848 |
| 商品点击量 | 252 |

与 2026-09-04 比较：

- GMV：约 -54.45%
- 商品成交件数：约 -33.33%
- SKU 订单数：0%
- 商品访客数：约 -35.48%

退款金额 9 月 4 日和 9 月 5 日均为 0，因此比较状态应为 `ZERO_BASELINE`，而不是“无数据”。

## 2026-08-30 ～ 2026-09-05 商品区间报表

商品报表汇总：

| 指标 | 值 |
| --- | ---: |
| GMV | 1,655.61 |
| 订单数 | 36 |
| SKU 订单数 | 36 |
| 商品成交件数 | 40 |
| 商品曝光次数 | 53,265 |
| 商品点击量 | 4,676 |
| 加购次数 | 195 |
| 去重商品曝光次数 | 33,183 |
| 去重点击次数 | 3,787 |
| 已加购用户数 | 148 |

其中 GMV、订单数、SKU 订单数、成交件数与同期 Shop Analytics 汇总完全一致。商品曝光/点击存在很小的平台口径差异，因此完整商品漏斗以商品报表为准，Shop Analytics 仅作为没有精确商品区间数据时的回退来源。

## Historical product import behavior

Historical `product_list` exports may span multiple days. These files are accepted as period data and stored in `fact_product_period` using the date range embedded in the workbook. They are not expanded into synthetic daily product rows. Daily dashboard KPIs and comparisons should be populated from Shop Analytics daily data; product period files enrich product/funnel analysis for their covered ranges.
