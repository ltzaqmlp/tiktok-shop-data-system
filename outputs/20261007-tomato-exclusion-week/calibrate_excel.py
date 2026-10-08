from scan import ROOT, BASE, NS, TAG, read
from zipfile import ZipFile, ZIP_DEFLATED
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
import copy, csv, hashlib, json, os, re, subprocess, sys

sys.path.insert(0, str(ROOT.parent / '20261007-tomato-exclusion'))
from clean import number, money, index

def database():
    sql = """select json_build_object('campaigns',(select json_agg(row_to_json(c)) from
      (select biz_date,spend::text,attributed_revenue::text,attributed_order_count from fact_ad_campaign_daily
      where shop_id=1 and market_code='MY' and biz_date between '2026-10-01' and '2026-10-06' order by biz_date) c),
      'product',(select row_to_json(p) from (select product_id,spend::text,attributed_revenue::text,attributed_order_count
      from fact_ad_product_daily where shop_id=1 and campaign_id='1871565679015985'
      and biz_date='2026-10-06' and product_id='1736479479514957476') p));"""
    output = subprocess.run(['docker','exec','shop-test-postgres-1','psql','-U','shop_app','-d','shop_operations','-At','-c',sql], check=True, capture_output=True, text=True, encoding='utf-8')
    result = json.loads(output.stdout)
    assert len(result['campaigns']) == 6
    assert sum(number(c['spend']) for c in result['campaigns']) == Decimal('218.11')
    assert sum(number(c['attributed_revenue']) for c in result['campaigns']) == Decimal('329.73')
    assert sum(c['attributed_order_count'] for c in result['campaigns']) == 20
    return result

def allocate(values, target):
    # The daily calibration has no verified hourly amounts. Preserve source
    # hourly proportions and allocate cents by largest remainder for exact totals.
    cents = {n: int(v * 100) for n, v in values.items()}
    target_cents = int(target * 100)
    total = sum(cents.values())
    assert total > 0 and target_cents >= 0
    result = {n: target_cents * v // total for n, v in cents.items()}
    remaining = target_cents - sum(result.values())
    order = sorted(cents, key=lambda n: (-(target_cents * cents[n] % total), n))
    for n in order[:remaining]:
        result[n] += 1
    assert sum(result.values()) == target_cents
    return {n: Decimal(v) / 100 for n, v in result.items()}

def prepare():
    data = database()
    inventory = json.loads((ROOT / 'inventory.json').read_text(encoding='utf-8'))
    hashes = {f['name']: hashlib.sha256((BASE / f['name']).read_bytes()).hexdigest() for f in inventory}
    plan = []
    def add(path, desired):
        part, xml, rows = read(path)
        numeric = {c.attrib['r'] for r in xml.findall('m:sheetData/m:row', NS) for c in r if c.attrib.get('t', 'n') == 'n'}
        desired = {ref: format(number(v).normalize(), 'f') if ref in numeric else v for ref, v in desired.items()}
        cells = {ref: v for ref, v in desired.items() if rows[int(re.search(r'\d+', ref)[0])].get(re.sub(r'\d', '', ref), '') != v}
        width = max(index(c) + 1 for row in rows.values() for c in row)
        matrix = [[''] * width for _ in range(max(rows))]
        for n, row in rows.items():
            for c, value in row.items():
                matrix[n-1][index(c)] = value
        name = path.relative_to(BASE).as_posix()
        assert cells
        plan.append({'name':name,'source_hash':hashes[name],'sheet':part,'matrix':matrix,'cells':cells,'numeric_cells':sorted(numeric & cells.keys()),'delete_rows':[]})
    for day in data['campaigns']:
        date = day['biz_date'].replace('-', '')
        paths = [BASE / f['name'] for f in inventory if '/马来/' in f['name'] and Path(f['name']).name == f'Campaign overview data {date} - {date}.xlsx']
        assert len(paths) == 1
        rows = read(paths[0])[2]
        hours = {n:r for n,r in rows.items() if r.get('A','').startswith(day['biz_date'])}
        total_n = next(n for n,r in rows.items() if r.get('A') == '-')
        assert len(hours) == 24 and sum(number(r['C']) for r in hours.values()) == day['attributed_order_count']
        spend = allocate({n:number(r['B']) for n,r in hours.items()},number(day['spend']))
        revenue = allocate({n:number(r['E']) for n,r in hours.items()},number(day['attributed_revenue']))
        cells = {}
        for n, row in hours.items():
            orders = number(row['C'])
            cells.update({f'B{n}':money(spend[n]),f'D{n}':money(spend[n]/orders) if orders else row['D'],
              f'E{n}':money(revenue[n]),f'F{n}':money(revenue[n]/spend[n]) if spend[n] else row['F']})
        orders = Decimal(day['attributed_order_count'])
        cells.update({f'B{total_n}':money(number(day['spend'])),f'D{total_n}':money(number(day['spend'])/orders),
          f'E{total_n}':money(number(day['attributed_revenue'])),f'F{total_n}':money(number(day['attributed_revenue'])/number(day['spend']))})
        add(paths[0],cells)
    path = BASE / '10-07/马来/新建文件夹 (2)/Product data 2026-10-06 - 2026-10-06 - Campaign 1871565679015985.xlsx'
    product = data['product']
    rows = read(path)[2]
    n = next(n for n,r in rows.items() if r.get('B') == product['product_id'])
    assert number(rows[n]['E']) == product['attributed_order_count']
    add(path,{f'D{n}':money(number(product['spend'])),f'F{n}':money(number(product['spend'])/product['attributed_order_count']),
      f'G{n}':money(number(product['attributed_revenue'])),f'H{n}':money(number(product['attributed_revenue'])/number(product['spend']))})
    assert len(plan) == 7
    (ROOT/'calibration-plan.json').write_text(json.dumps(plan,ensure_ascii=False),encoding='utf-8')
    (ROOT/'calibration-hashes.json').write_text(json.dumps(hashes,ensure_ascii=False),encoding='utf-8')
    (ROOT/'calibration-targets.json').write_text(json.dumps(data,ensure_ascii=False),encoding='utf-8')
    print('PREPARED',len(plan),'files;',sum(len(p['cells']) for p in plan),'changed cells; no backup requested')

def apply():
    import xml.etree.ElementTree as ET
    import openpyxl
    ET.register_namespace('',NS['m'])
    plan = json.loads((ROOT/'calibration-plan.json').read_text(encoding='utf-8'))
    hashes = json.loads((ROOT/'calibration-hashes.json').read_text(encoding='utf-8'))
    stage = ROOT/'calibration-staged'
    audit = []
    for i,item in enumerate(plan):
        source = BASE/item['name']
        part,xml,before = read(source)
        _,authored,values = read(ROOT/f'calibration-authored-{i}.xlsx')
        authored_cells = {c.attrib['r']:c for r in authored.findall('m:sheetData/m:row',NS) for c in r}
        expected = copy.deepcopy(before)
        for row in xml.findall('m:sheetData/m:row',NS):
            for cell in row:
                ref = cell.attrib['r']
                if ref not in item['cells']:
                    continue
                n,col = int(re.search(r'\d+',ref)[0]),re.sub(r'\d','',ref)
                replacement = authored_cells[ref]
                for child in list(cell):
                    if child.tag in {TAG+'v',TAG+'is',TAG+'f'}:
                        cell.remove(child)
                cell.attrib.pop('t',None)
                if replacement.attrib.get('t') == 's':
                    cell.attrib['t'] = 'inlineStr'
                    ET.SubElement(ET.SubElement(cell,TAG+'is'),TAG+'t').text = values[n][col]
                else:
                    if 't' in replacement.attrib:
                        cell.attrib['t'] = replacement.attrib['t']
                    for child in replacement:
                        if child.tag in {TAG+'v',TAG+'is',TAG+'f'}:
                            cell.append(copy.deepcopy(child))
                expected[n][col] = item['cells'][ref]
                audit.append([item['name'],ref,before[n][col],expected[n][col]])
        dest = stage/item['name']
        dest.parent.mkdir(parents=True,exist_ok=True)
        with ZipFile(source) as zin,ZipFile(dest,'w',ZIP_DEFLATED) as zout:
            for entry in zin.infolist():
                zout.writestr(entry,ET.tostring(xml,encoding='utf-8',xml_declaration=True) if entry.filename == part else zin.read(entry.filename))
        assert read(dest)[2] == expected,item['name']
        with ZipFile(source) as zin,ZipFile(dest) as zout:
            assert all(zin.read(n)==zout.read(n) for n in zin.namelist() if n!=part)
        book = openpyxl.load_workbook(dest)
        for ref,value in item['cells'].items():
            actual = book.worksheets[0][ref].value
            assert number(str(actual)) == number(value),(item['name'],ref)
        book.close()
    for name,digest in hashes.items():
        assert hashlib.sha256((BASE/name).read_bytes()).hexdigest() == digest,('Source changed',name)
    import apply as previous_apply
    previous_apply.plan = plan
    previous_apply.writable()
    assert database() == json.loads((ROOT/'calibration-targets.json').read_text(encoding='utf-8'))
    # Keep rollback bytes only in memory during the overwrite; no backup files.
    originals = {item['name']:(BASE/item['name']).read_bytes() for item in plan}
    committed = []
    try:
        for item in plan:
            target = BASE/item['name']
            temp = target.with_name(target.name+'.codex-tmp')
            temp.write_bytes((stage/item['name']).read_bytes())
            os.replace(temp,target)
            committed.append(item['name'])
        for name,digest in hashes.items():
            if name in originals:
                assert (BASE/name).read_bytes() == (stage/name).read_bytes()
            else:
                assert hashlib.sha256((BASE/name).read_bytes()).hexdigest() == digest
    except BaseException:
        for name in committed:
            (BASE/name).write_bytes(originals[name])
        raise
    with (ROOT/'广告校准修改明细.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['文件','单元格','修改前','修改后'])
        writer.writerows(audit)
    current = database()
    assert current == json.loads((ROOT/'calibration-targets.json').read_text(encoding='utf-8')),'Database changed'
    totals = [Decimal(0)]*3
    for item in plan:
        rows = read(BASE/item['name'])[2]
        if not Path(item['name']).name.startswith('Campaign'):
            continue
        date = next(r['A'][:10] for r in rows.values() if re.match(r'2026-',r.get('A','')))
        target = next(d for d in current['campaigns'] if d['biz_date']==date)
        hours = [r for r in rows.values() if r.get('A','').startswith(date)]
        actual = [sum(number(r[c]) for r in hours) for c in ['B','C','E']]
        assert actual == [number(target['spend']),Decimal(target['attributed_order_count']),number(target['attributed_revenue'])]
        summary = next(r for r in rows.values() if r.get('A')=='-')
        assert actual == [number(summary[c]) for c in ['B','C','E']]
        totals = [a+b for a,b in zip(totals,actual)]
    assert totals == [Decimal('218.11'),Decimal(20),Decimal('329.73')]
    print('MODIFIED 7 ORIGINAL FILES; 964 OTHER FILES UNCHANGED; HOURS = SUMMARIES = TEST DATABASE',totals)

if __name__ == '__main__':
    {'prepare':prepare,'apply':apply}[sys.argv[1]]()
