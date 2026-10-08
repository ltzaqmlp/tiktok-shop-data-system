from scan import ROOT, BASE, TOKEN, read, NS
from pathlib import Path
from decimal import Decimal
from datetime import datetime
from collections import defaultdict
import json, re, sys

sys.path.insert(0, str(ROOT.parent / '20261007-tomato-exclusion'))
from clean import number, money, percent, index

SALES = re.compile(r'GMV|商品交易总额|订单数|成交件数|客户数|平均订单金额|AOV|CTOR|点击成交转化率|税费|运费|退款|退货', re.I)
files = json.loads((ROOT / 'inventory.json').read_text(encoding='utf-8'))
for file in files:
    file['rows'] = {int(n): row for n, row in file['rows'].items()}
by_name = {f['name']: f for f in files}
changes = {}
summary = []
removed_orders = []

def put(file, cells=None, deletes=None):
    cells = {ref: val for ref, val in (cells or {}).items() if file['rows'][int(re.search(r'\d+', ref)[0])].get(re.sub(r'\d', '', ref), '') != val}
    if cells or deletes:
        current = changes.setdefault(file['name'], {'cells': {}, 'delete_rows': []})
        current['cells'].update(cells)
        current['delete_rows'] = sorted(set(current['delete_rows'] + (deletes or [])))

def is_fake(row, column='A'):
    return TOKEN in row.get(column, '').lower()

def active(rows):
    return {n: r for n, r in rows.items() if n > 2 and r.get('A') and r.get('Z') and r.get('B') not in ['已取消', '未付款', '已退款']}

def product_date(file):
    found = re.findall(r'\d{2}/\d{2}/\d{4}', file['rows'].get(1, {}).get('A', ''))
    assert len(found) == 2, file['name']
    return found

# Product exports repeat historical rows in multiple folders; clean every copy.
for file in files:
    name, rows = file['name'], file['rows']
    if Path(name).name.startswith('product_list'):
        cols = [c for c, label in rows[4].items() if c not in ['A', 'B', 'C'] and SALES.search(label)]
        cells = {}
        for n, row in rows.items():
            if n <= 4 or not is_fake(row):
                continue
            for col in cols:
                old = row.get(col, '')
                if old not in ['', '-', '/'] and number(old) != 0:
                    cells[f'{col}{n}'] = 'RM0.00' if old.startswith('RM') else '0.00%' if old.endswith('%') else '0'
        put(file, cells)
    elif Path(name).name.startswith('全部'):
        assert rows[1]['H'] == 'Product Name'
        deletes = [n for n, row in rows.items() if n > 2 and is_fake(row, 'H')]
        removed_orders.extend({'file': name, 'id': rows[n]['A'], 'amount': rows[n]['W']} for n in deletes)
        put(file, deletes=deletes)
    elif Path(name).name.startswith('affiliate'):
        deletes = [n for n, row in rows.items() if n > 1 and any(TOKEN in str(v).lower() for v in row.values())]
        put(file, deletes=deletes)
    elif Path(name).name.startswith('Product data'):
        cells = {f'{c}{n}': '0' if c == 'E' else '0.00' for n, row in rows.items() if n > 1 and is_fake(row) for c in ['E', 'F', 'G', 'H']}
        put(file, cells)

# Match shop totals to the complete remaining paid order details and product exports.
clean_shops = {}
for file in files:
    if not Path(file['name']).name.startswith('Shop'):
        continue
    folder = str(Path(file['name']).parent).replace('\\', '/')
    title = file['rows'][1]['A']
    date = re.search(r'\d{2}/\d{2}/\d{4}', title)[0]
    day_products = [f for f in files if str(Path(f['name']).parent).replace('\\', '/') == folder + '/新建文件夹/加购' and product_date(f) == [date, date]]
    assert len(day_products) == 1, (folder, date, len(day_products))
    fake = [r for n, r in day_products[0]['rows'].items() if n > 4 and is_fake(r)]
    old = file['rows'][4]
    values = dict(old)
    if any(number(r['S']) > 0 or number(r['D']) > 0 for r in fake):
        order_files = [f for f in files if str(Path(f['name']).parent).replace('\\', '/') == folder and Path(f['name']).name.startswith('全部')]
        assert len(order_files) == 1
        orders = order_files[0]['rows']
        kept = {n: r for n, r in active(orders).items() if not is_fake(r, 'H')}
        assert all(r['Z'].startswith(date) for r in kept.values())
        gmv = sum((number(r['W']) for r in kept.values()), Decimal(0))
        count = len({r['A'] for r in kept.values()})
        sku_count = len({(r['A'], r['F']) for r in kept.values()})
        qty = sum(number(r['J']) for r in kept.values())
        gross = sum(number(r['W']) + number(r['N']) for r in kept.values())
        assert number(old['B']) - sum(number(r['D']) for r in fake) == gmv
        assert number(old['C']) - sum(number(r['S']) for r in fake) == count
        assert number(old['G']) - sum(number(r['T']) for r in fake) == sku_count
        assert number(old['E']) - sum(number(r['U']) for r in fake) == qty
        assert old['C'] == old['D'], 'Customer deduplication needs evidence'
        assert number(old['H']) - sum(number(r['AL']) for r in fake) == gross
        values.update({'B': money(gmv), 'C': str(count), 'D': str(count), 'E': str(qty), 'G': str(sku_count), 'H': money(gross), 'K': str(Decimal(count) / number(old['J'])) if number(old['J']) else '0', 'P': money(gmv / count) if count else '0.00'})
        assert sum(number(r['D']) for n, r in day_products[0]['rows'].items() if n > 4 and not is_fake(r)) == gmv
        put(file, {f'{c}{n}': values[c] for c in ['B', 'C', 'D', 'E', 'G', 'H', 'K', 'P'] for n in [4, 10]})
    clean_shops[(folder.split('/')[1], date)] = (file, values)
    if folder.endswith('/马来'):
        summary.append({'folder': folder.split('/')[0], 'date': date, 'fake_orders': str(sum((number(r['S']) for r in fake), Decimal(0))), 'gmv_before': old['B'], 'gmv_after': values['B'], 'orders_after': values['C']})

# Recalculate comparisons on following days even when that day has no fake sales.
for (market, date), (file, values) in clean_shops.items():
    title = file['rows'].get(1, {}).get('B', '')
    match = re.search(r'\d{2}/\d{2}/\d{4}', title)
    if not match or (market, match[0]) not in clean_shops or file['rows'].get(5, {}).get('A') != '百分比变化':
        continue
    previous_file, previous = clean_shops[(market, match[0])]
    affected = [c for c in ['B', 'C', 'D', 'E', 'G', 'H', 'K', 'P'] if values.get(c) != file['rows'][4].get(c) or previous.get(c) != previous_file['rows'][4].get(c)]
    cells = {}
    for col in affected:
        current, baseline = number(values[col]), number(previous[col])
        if col == 'P':
            current = number(values['B']) / number(values['C']) if number(values['C']) else Decimal(0)
            baseline = number(previous['B']) / number(previous['C']) if number(previous['C']) else Decimal(0)
        cells[f'{col}5'] = percent(current / baseline - 1) if baseline else '-'
    put(file, cells)

# Ad attribution is reconciled against all exported non-Tomato product campaigns.
# Only pure Tomato hours are cleared; paid legitimate-order hours remain intact.
ad_changes = []
for file in files:
    if not Path(file['name']).name.startswith('Campaign'):
        continue
    folder = str(Path(file['name']).parent).replace('\\', '/')
    date = re.search(r'\d{8}', Path(file['name']).name)[0]
    iso = datetime.strptime(date, '%Y%m%d').strftime('%Y-%m-%d')
    ad_products = [f for f in files if f['name'].startswith(folder + '/新建文件夹 (2)/') and Path(f['name']).name.startswith('Product data') and iso in Path(f['name']).name]
    if not ad_products:
        continue
    rows = file['rows']
    totals = [(n, r) for n, r in rows.items() if r.get('A') == '-']
    if not totals:
        continue
    assert len(totals) == 1
    total_n, total = totals[0]
    normal = [r for f in ad_products for n, r in f['rows'].items() if n > 1 and not is_fake(r)]
    normal_orders = sum((number(r['E']) for r in normal), Decimal(0))
    normal_revenue = sum((number(r['G']) for r in normal), Decimal(0))
    removed_count = number(total['C']) - normal_orders
    if removed_count == 0:
        continue
    assert folder.endswith('/马来') and removed_count > 0
    order_file = next(f for f in files if str(Path(f['name']).parent).replace('\\', '/') == folder and Path(f['name']).name.startswith('全部'))
    normal_hours = {datetime.strptime(r['Z'], '%d/%m/%Y %H:%M:%S').strftime('%Y-%m-%d %H:00:00') for r in active(order_file['rows']).values() if not is_fake(r, 'H')}
    candidates = [(n, r) for n, r in rows.items() if r.get('A', '').startswith(iso) and number(r['C']) > 0 and r['A'] not in normal_hours]
    assert sum(number(r['C']) for n, r in candidates) == removed_count, (folder, 'Mixed-hour attribution')
    removed_revenue = number(total['E']) - normal_revenue
    assert abs(sum(number(r['E']) for n, r in candidates) - removed_revenue) <= Decimal('.02')
    assert all(number(r['E']) / number(r['C']) <= Decimal('2') for n, r in candidates)
    cells = {f'{c}{n}': '0' if c == 'C' else '0.00' for n, r in candidates for c in ['C', 'D', 'E', 'F']}
    cells.update({f'C{total_n}': str(int(normal_orders)), f'D{total_n}': money(number(total['B']) / normal_orders) if normal_orders else '0.00', f'E{total_n}': money(normal_revenue), f'F{total_n}': money(normal_revenue / number(total['B'])) if number(total['B']) else '0.00'})
    put(file, cells)
    ad_changes.append({'file': file['name'], 'removed_orders': str(removed_count), 'removed_revenue': money(removed_revenue)})

plan = []
for name, change in changes.items():
    file = by_name[name]
    rows = file['rows']
    width = max(index(col) + 1 for row in rows.values() for col in row)
    matrix = [[''] * width for _ in range(max(rows))]
    for n, row in rows.items():
        for col, value in row.items():
            matrix[n - 1][index(col)] = value
    xml = read(BASE / name)[1]
    numeric = [c.attrib['r'] for r in xml.findall('m:sheetData/m:row', NS) for c in r.findall('m:c', NS) if c.attrib['r'] in change['cells'] and c.attrib.get('t', 'n') == 'n']
    plan.append({'name': name, 'source_hash': file['hash'], 'sheet': file['sheet'], 'matrix': matrix, 'numeric_cells': numeric, **change})
(ROOT / 'plan.json').write_text(json.dumps(plan, ensure_ascii=False), encoding='utf-8')
(ROOT / 'summary.json').write_text(json.dumps({'scanned': len(files), 'modified': len(plan), 'days': summary, 'deleted_orders': removed_orders, 'ads': ad_changes}, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'modified': len(plan), 'days': summary, 'deleted_order_count': len(removed_orders), 'ads': ad_changes}, ensure_ascii=False))
