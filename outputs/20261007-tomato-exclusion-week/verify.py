from scan import ROOT, BASE, TOKEN, read
from pathlib import Path
from decimal import Decimal
import hashlib, json, re, sys

sys.path.insert(0, str(ROOT.parent / '20261007-tomato-exclusion'))
from clean import number

inventory = json.loads((ROOT / 'inventory.json').read_text(encoding='utf-8'))
plan = json.loads((ROOT / 'plan.json').read_text(encoding='utf-8'))
summary = json.loads((ROOT / 'summary.json').read_text(encoding='utf-8'))
changed = {p['name'] for p in plan}
rows_by_name = {}
cache = {}
sales = re.compile(r'GMV|商品交易总额|订单数|成交件数|客户数|平均订单金额|AOV|CTOR|点击成交转化率|税费|运费|退款|退货', re.I)

for item in inventory:
    path = BASE / item['name']
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if item['name'] not in changed:
        assert digest == item['hash'], item['name']
    else:
        assert digest == hashlib.sha256((ROOT / 'staged' / item['name']).read_bytes()).hexdigest()
    if digest not in cache:
        cache[digest] = read(path)[2]
    rows = rows_by_name[item['name']] = cache[digest]
    if path.name.startswith('全部'):
        assert all(TOKEN not in r.get('H', '').lower() for n, r in rows.items() if n > 2), item['name']
    elif path.name.startswith('product_list'):
        columns = [c for c, title in rows[4].items() if c not in ['A', 'B', 'C'] and sales.search(title)]
        for n, row in rows.items():
            if n > 4 and TOKEN in row.get('A', '').lower():
                assert all(row.get(c, '') in ['', '-', '/'] or number(row[c]) == 0 for c in columns), (item['name'], n)

for day in summary['days']:
    folder = day['folder'] + '/马来/'
    shop = next(rows for name, rows in rows_by_name.items() if name.startswith(folder) and Path(name).name.startswith('Shop'))
    orders = next(rows for name, rows in rows_by_name.items() if name.startswith(folder) and Path(name).name.startswith('全部'))
    active = [r for n, r in orders.items() if n > 2 and r.get('A') and r.get('Z') and r.get('B') not in ['已取消', '未付款', '已退款']]
    assert sum((number(r['W']) for r in active), Decimal(0)) == number(shop[4]['B']) == number(day['gmv_after']), day
    assert len({r['A'] for r in active}) == number(shop[4]['C']) == number(day['orders_after']), day
    daily = next(rows for name, rows in rows_by_name.items() if name.startswith(folder + '新建文件夹/加购/') and re.findall(r'\d{2}/\d{2}/\d{4}', rows.get(1, {}).get('A', '')) == [day['date'], day['date']])
    assert sum((number(r['D']) for n, r in daily.items() if n > 4), Decimal(0)) == number(shop[4]['B']), day
    assert sum((number(r['S']) for n, r in daily.items() if n > 4), Decimal(0)) == number(shop[4]['C']), day

for ad in summary['ads']:
    rows = rows_by_name[ad['file']]
    folder = str(Path(ad['file']).parent).replace('\\', '/') + '/新建文件夹 (2)/'
    date = re.search(r'\d{8}', Path(ad['file']).name)[0]
    iso = date[:4] + '-' + date[4:6] + '-' + date[6:]
    products = [r for name, product in rows_by_name.items() if name.startswith(folder) and Path(name).name.startswith('Product data') and iso in Path(name).name for n, r in product.items() if n > 1 and TOKEN not in r.get('A', '').lower()]
    total = next(r for r in rows.values() if r.get('A') == '-')
    assert number(total['C']) == sum((number(r['E']) for r in products), Decimal(0)), ad
    assert number(total['E']) == sum((number(r['G']) for r in products), Decimal(0)), ad

folder = Path((ROOT / 'backup-path.txt').read_text(encoding='utf-8'))
report = ['# Tomato Facial Mask 刷单清理结果', '', '已直接修改 10-01 至 10-07 目录中的 30 份原文件，扫描并核对 971 份文件。', '', '订单表删除 7 条剩余刷单记录；10 月 4 日的 4 条刷单记录在修改前已不存在，相关商品及店铺销售汇总仍已扣除。', '', '这批每日数据对应 9 月 30 日至 10 月 6 日，共排除 11 笔刷单成交、RM37.41；期间商品报表中的历史 Tomato 成交也已归零。广告汇总另排除 6 笔归因订单、USD5.57。', '', '商品刷单成交、客户、退款和成交转化指标归零；店铺销售总计及比较值重新计算。流量、广告花费和其他商品数据保留。', '', '|目录|报表日期|剔除刷单数|原 GMV (MYR)|现 GMV (MYR)|现订单数|', '|---|---|---:|---:|---:|---:|']
for day in summary['days']:
    report.append('| ' + ' | '.join(day[c] for c in ['folder', 'date', 'fake_orders', 'gmv_before', 'gmv_after', 'orders_after']) + ' |')
report += ['', '## 已修改原文件', '']
for item in plan:
    target = (BASE / item['name']).as_posix()
    report.append(f"- [{item['name']}](<{target}>)")
report += ['', '校验通过：所有订单表不含 Tomato Facial Mask 订单；商品表对应销售字段均为零；马来每日订单、商品与店铺 GMV/订单数一致；修改后的广告汇总与合法商品广告数据一致；其余 941 份文件逐字节不变。', '']
(folder / '处理结果.md').write_text('\n'.join(report), encoding='utf-8')
print('VERIFIED 971 files; all Tomato order exclusions, product sales, daily shop reconciliations, and ad totals passed.')
print('REPORT', folder / '处理结果.md')
