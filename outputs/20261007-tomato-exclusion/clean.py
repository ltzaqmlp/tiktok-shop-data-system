from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from decimal import Decimal, ROUND_HALF_UP
import xml.etree.ElementTree as ET
import json, re, sys, hashlib, copy

ROOT = Path(__file__).parent
SOURCE = Path(r'C:\Users\Administrator\Desktop\新建文件夹\10-02\马来')
OUTPUT = ROOT / '剔除版'
NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
TAG = '{' + NS['m'] + '}'
TOKEN = 'tomato facial mask'

def read(path):
    with ZipFile(path) as archive:
        strings = []
        if 'xl/sharedStrings.xml' in archive.namelist():
            strings = [''.join(n.itertext()) for n in ET.fromstring(archive.read('xl/sharedStrings.xml')).findall('m:si', NS)]
        xml = ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
        rows = {}
        for node in xml.findall('m:sheetData/m:row', NS):
            row = rows.setdefault(int(node.attrib['r']), {})
            for cell in node.findall('m:c', NS):
                value, inline = cell.find('m:v', NS), cell.find('m:is', NS)
                text = (value.text or '') if value is not None else ''.join(inline.itertext()) if inline is not None else ''
                if cell.attrib.get('t') == 's':
                    text = strings[int(text)] if text else ''
                row[re.sub(r'\d', '', cell.attrib['r'])] = text
        return xml, rows

def number(value):
    return Decimal(re.sub(r'^(RM|MYR|USD)\s*', '', str(value)).replace(',', '').replace('%', '') or '0')

def money(value):
    return str(value.quantize(Decimal('.01'), rounding=ROUND_HALF_UP))

def percent(value):
    return money(value * 100) + '%'

def index(col):
    n = 0
    for char in col:
        n = n * 26 + ord(char) - 64
    return n - 1

def prepare():
    files = [p for p in SOURCE.rglob('*.xlsx') if not p.name.startswith('~$')]
    data = {p.relative_to(SOURCE).as_posix(): read(p)[1] for p in files}
    order_name = '全部 笔订单-2026-10-07-10_27.xlsx'
    orders = data[order_name]
    rejected = {n: r for n, r in orders.items() if n > 2 and TOKEN in r.get('H', '').lower()}
    kept = {n: r for n, r in orders.items() if n > 2 and n not in rejected}
    assert len(rejected) == 3 and len(kept) == 3
    assert sum(number(r['W']) for r in rejected.values()) == Decimal('13.41')
    assert all(r['J'] == '1' and r['K'] == '0' and not r.get('X') for r in rejected.values())
    changes = {order_name: {'delete_rows': sorted(rejected), 'cells': {}}}

    product_day = data['新建文件夹/加购/product_list_20261001.xlsx']
    fake_products = {r['B'] for n, r in product_day.items() if n > 4 and TOKEN in r.get('A', '').lower()}
    assert fake_products == {'1737247167739692708'}
    assert sum(number(r['D']) for n, r in product_day.items() if r.get('B') in fake_products) == Decimal('13.41')

    for name, rows in data.items():
        if not Path(name).name.startswith('product_list'):
            continue
        columns = [col for col, label in rows[4].items() if col not in ['A', 'B', 'C'] and re.search(r'GMV|商品交易总额|订单数|成交件数|客户数|平均订单金额|AOV|CTOR|点击成交转化率|税费|运费|退款|退货', label, re.I)]
        cells = {}
        for n, row in rows.items():
            if n <= 4 or row.get('B') not in fake_products:
                continue
            for col in columns:
                old = row.get(col, '')
                if old and old not in ['-', '/'] and number(old) != 0:
                    cells[f'{col}{n}'] = 'RM0.00' if old.startswith('RM') else '0.00%' if old.endswith('%') else '0'
        if cells:
            changes[name] = {'cells': cells, 'delete_rows': []}

    shop_name = 'Shop Analytics_Key metrics_20261007 (1).xlsx'
    shop = data[shop_name]
    assert shop[1]['A'].startswith('分析日期：01/10/2026')
    assert number(shop[4]['B']) == sum(number(r['W']) for n, r in orders.items() if n > 2)
    assert number(shop[4]['H']) == sum(number(r['W']) + number(r['N']) for n, r in orders.items() if n > 2)
    assert all(shop[4][col] == '6' for col in ['C', 'D', 'E', 'G'])
    gmv = sum(number(r['W']) for r in kept.values())
    gross = sum(number(r['W']) + number(r['N']) for r in kept.values())
    values = {'B': money(gmv), 'C': '3', 'D': '3', 'E': '3', 'G': '3', 'H': money(gross), 'K': str(Decimal(3) / number(shop[4]['J'])), 'P': money(gmv / 3)}
    cells = {f'{col}{n}': value for n in [4, 10] for col, value in values.items()}
    previous = read(SOURCE.parent.parent / '10-01/马来/Shop Analytics_Key metrics_20261007.xlsx')[1][4]
    precise = {**{k: number(v) for k, v in values.items()}, 'P': gmv / 3}
    for col, value in precise.items():
        baseline = number(previous[col])
        assert baseline > 0
        cells[f'{col}5'] = percent(value / baseline - 1)
    changes[shop_name] = {'cells': cells, 'delete_rows': []}

    campaign_name = 'Campaign overview data 20261001 - 20261001.xlsx'
    campaign = data[campaign_name]
    hits = [(n, r) for n, r in campaign.items() if r.get('A') == '2026-10-01 19:00:00']
    assert len(hits) == 1 and hits[0][1]['C'] == '1' and hits[0][1]['E'] == '1.89'
    n = hits[0][0]
    total = campaign[26]
    assert total['C'] == '4' and total['E'] == '45.26' and total['B'] == '29.91'
    revenue = number(total['E']) - Decimal('1.89')
    cells = {f'{c}{n}': '0' if c == 'C' else '0.00' for c in ['C', 'D', 'E', 'F']}
    cells.update({'C26': '3', 'D26': money(number(total['B']) / 3), 'E26': money(revenue), 'F26': money(revenue / number(total['B']))})
    changes[campaign_name] = {'cells': cells, 'delete_rows': []}

    for name, rows in data.items():
        if not Path(name).name.startswith(('affiliate', 'Product data')):
            continue
        assert not any(TOKEN in str(v).lower() for row in rows.values() for v in row.values()), name

    plan = []
    for name, change in changes.items():
        rows = data[name]
        width = max(index(col) + 1 for row in rows.values() for col in row)
        matrix = [[''] * width for _ in range(max(rows))]
        for n, row in rows.items():
            for col, value in row.items():
                matrix[n - 1][index(col)] = value
        plan.append({'name': name, **change, 'matrix': matrix, 'source_hash': hashlib.sha256((SOURCE / name).read_bytes()).hexdigest()})
    assert len(plan) == 6, len(plan)
    (ROOT / 'plan.json').write_text(json.dumps(plan, ensure_ascii=False), encoding='utf-8')
    print(json.dumps({'files': len(plan), 'removed_orders': [r['A'] for r in rejected.values()], 'shop_gmv': str(gmv), 'shop_gross': str(gross), 'ad_revenue_summary': str(revenue)}, ensure_ascii=False))

def finish():
    plan = json.loads((ROOT / 'plan.json').read_text(encoding='utf-8'))
    OUTPUT.mkdir(exist_ok=True)
    audit = []
    for i, item in enumerate(plan):
        source = SOURCE / item['name']
        assert hashlib.sha256(source.read_bytes()).hexdigest() == item['source_hash'], 'Source changed during edit'
        original, before = read(source)
        authored, generated = read(ROOT / f'authored-{i}.xlsx')
        generated_cells = {cell.attrib['r']: cell for row in authored.findall('m:sheetData/m:row', NS) for cell in row.findall('m:c', NS)}
        part = original.find('m:sheetData', NS)
        deletes = item['delete_rows']
        for row in list(part):
            rowno = int(row.attrib['r'])
            if rowno in deletes:
                part.remove(row)
                continue
            for cell in row.findall('m:c', NS):
                ref = cell.attrib['r']
                if ref not in item['cells']:
                    continue
                replacement = generated_cells[ref]
                cell.attrib.pop('t', None)
                if 't' in replacement.attrib:
                    cell.attrib['t'] = replacement.attrib['t']
                for child in list(cell):
                    if child.tag in {TAG + 'v', TAG + 'is', TAG + 'f'}:
                        cell.remove(child)
                for child in replacement:
                    if child.tag in {TAG + 'v', TAG + 'is', TAG + 'f'}:
                        cell.append(copy.deepcopy(child))
                # Resolve exported shared strings locally so the original sharedStrings stays intact.
                if cell.attrib.get('t') == 's':
                    cell.attrib['t'] = 'inlineStr'
                    for child in list(cell):
                        if child.tag == TAG + 'v':
                            cell.remove(child)
                    inline = ET.SubElement(cell, TAG + 'is')
                    ET.SubElement(inline, TAG + 't').text = generated[rowno][re.sub(r'\d', '', ref)]
            if deletes:
                shifted = rowno - sum(n < rowno for n in deletes)
                row.attrib['r'] = str(shifted)
                for cell in row.findall('m:c', NS):
                    cell.attrib['r'] = re.sub(r'\d+', str(shifted), cell.attrib['r'])
        if deletes:
            dimension = original.find('m:dimension', NS)
            if dimension is not None:
                dimension.attrib['ref'] = 'A1:BD5'
        path = OUTPUT / item['name']
        path.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(source) as zin, ZipFile(path, 'w', ZIP_DEFLATED) as zout:
            for entry in zin.infolist():
                payload = ET.tostring(original, encoding='utf-8', xml_declaration=True) if entry.filename == 'xl/worksheets/sheet1.xml' else zin.read(entry.filename)
                zout.writestr(entry, payload)
        _, after = read(path)
        expected = copy.deepcopy(before)
        for ref, value in item['cells'].items():
            expected[int(re.search(r'\d+', ref)[0])][re.sub(r'\d', '', ref)] = value
        expected = {n - sum(d < n for d in deletes): row for n, row in expected.items() if n not in deletes}
        assert after == expected, item['name']
        with ZipFile(source) as zin, ZipFile(path) as zout:
            assert zin.namelist() == zout.namelist()
            assert all(zin.read(n) == zout.read(n) for n in zin.namelist() if n != 'xl/worksheets/sheet1.xml')
        audit.append({'file': item['name'], 'removed_rows': deletes, 'changed_cells': item['cells']})
    (ROOT / 'verification.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS: 6 files; every unaffected cell and ZIP part preserved; originals unchanged.')

if __name__ == '__main__':
    {'prepare': prepare, 'finish': finish}[sys.argv[1]]()
