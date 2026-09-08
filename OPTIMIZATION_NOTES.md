# Dashboard / Excel 数据口径修正说明

## 1. 为什么原系统有字段但页面没有数据

原系统把 `sold_qty / sku_order_count / refund_amount` 等 Dashboard 指标绑定到 `fact_product_daily`。但实际提供的 Shop Analytics 每日文件已经包含：

- 商品成交件数
- SKU 订单数
- 退款金额
- 商品曝光次数 / 去重曝光次数
- 商品点击量 / 去重点击次数

因此当商品报表没有按单日导入时，页面会把这些字段显示为空，即使 Shop Analytics 本身有正确数据。

本版修正为：**每日核心 KPI 以 Shop Analytics 为准**，商品报表只负责商品维度和完整漏斗。

## 2. 多日商品报表不能当成“某一天”

实际商品文件 `product_list_20260830...xlsx` 的数据分析日期是 `30/08/2026~05/09/2026`，文件中的每个商品值是整个 7 天区间的汇总，并非 9 月 5 日单日值。

旧逻辑要求 PRODUCT_DAILY 必须是单日文件；如果绕过校验把 7 天汇总写入 9 月 5 日，会造成严重重复/错算。

本版新增 `fact_product_period`：

- 单日商品报表仍写 `fact_product_daily`；
- 多日商品报表写 `fact_product_period(date_from,date_to,product_id,...)`；
- Dashboard 仅当用户选择范围和商品报表范围完全一致时使用该区间商品数据；
- 不会把区间总量拆分到单日。

## 3. 商品漏斗口径

完整商品报表优先提供：曝光、点击、加购、SKU 订单、去重曝光、去重点击、加购用户、预计客户。

CTOR 修正为：

`SKU 订单数 / 商品点击量`

而不是旧版的 `订单数 / 商品点击量`。

如果所选范围没有完全匹配的商品报表，则使用 Shop Analytics 做部分漏斗：曝光、点击、SKU 订单、去重曝光、去重点击、客户数仍可显示；Shop Analytics 没有的加购字段显示缺失，不会显示虚假的 0。

## 4. 退款和订单

每日 `refund_amount` 以 Shop Analytics 为准，因为它有按日退款金额。

订单文件中的 `Order Refund Amount` 和 `Sku Quantity of return` 现在也会入库，但只作为订单维度的核对字段。订单导出没有提供可靠的“退款发生日期”，因此不能直接拿订单创建日期当退款日期计算每日退款趋势。

商品报表中的 `已退款的商品件数 / 退款客户数` 在商品区间完全匹配时仍可用于售后汇总。

## 5. “较昨日 / 较前 7 日均值”

后端返回独立状态：

- `NO_HISTORY`：没有历史数据
- `ZERO_BASELINE`：历史基线为 0，百分比无定义
- `CURRENT_MISSING`：当前没有数据
- `CURRENT_PARTIAL`：当前数据覆盖不完整
- `HISTORY_PARTIAL`：历史数据覆盖不完整
- `VALUE_UNAVAILABLE`：字段本身不可用
- `OK`：正常计算

前 7 日均值只有在 7 天 Shop Analytics 数据完整时才计算。

## 6. 数据更新时间

经营驾驶舱的 `dataFreshness` 现在以 `SHOP_ANALYTICS` 的成功导入为准，不会再因为广告或其它来源更新得更晚而误导用户认为核心经营数据也已经更新到同一天。

## 7. 升级后的历史数据

`V2__correct_data_source_metrics.sql` 新增的 Shop Analytics 字段为 nullable，目的是区分：

- 旧版本没有保存这个字段；
- 新版本真实值就是 0。

因此升级后必须至少重新导入一次 Shop Analytics，建议 4 份文件全部重新导入。管理员可使用强制重跑处理相同文件哈希。


## 历史商品报表导入兼容

- `PRODUCT_DAILY` 入口现同时接受单日和多日 `product_list` 导出。
- 多日文件以文件内日期范围为准，即使导入表单误填了“业务日期”也不会阻止历史文件导入。
- 多日商品数据保存到 `fact_product_period`，绝不会伪拆为逐日商品数据。
- 导入历史页面会显示完整的 `bizDateFrom ~ bizDateTo`。
- 日级经营看板继续以 Shop Analytics 的每日数据为准，因此历史日趋势不要求逐日导出 product_list。
