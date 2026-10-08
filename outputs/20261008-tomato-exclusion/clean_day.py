from pathlib import Path
from decimal import Decimal
from datetime import datetime
from collections import Counter
import copy, hashlib, json, re, sys

WEEK = Path(__file__).resolve().parent.parent / '20261007-tomato-exclusion-week'
sys.path.insert(0, str(WEEK))
from scan import BASE, NS, TOKEN, read
sys.path.insert(0, str(WEEK.parent / '20261007-tomato-exclusion'))
from clean import number, money, index

ROOT = Path(__file__).resolve().parent
SOURCE = BASE / '10-08' / '马来'
SALES = re.compile(r'GMV|商品交易总额|订单数|成交件数|客户数|平均订单金额|AOV|CTOR|点击成交转化率|税费|运费|退款|退货', re.I)

def prepare():
    files = []
    for path in sorted(SOURCE.rglob('*.xlsx')):
        if path.name.startswith('~$'):
            continue
        sheet, xml, rows = read(path)
        files.append(dict(name=path.relative_to(BASE).as_posix(), hash=hashlib.sha256(path.read_bytes()).hexdigest(), sheet=sheet, rows=rows))
    assert len(files) == 90
    by_name = {f['name']: f for f in files}
    changes = {}
    def put(f, cells=None, deletes=None):
        cells = {ref:v for ref,v in (cells or {}).items() if f['rows'][int(re.search(r'\d+',ref)[0])].get(re.sub(r'\d','',ref),'') != v}
        if cells or deletes:
            changes[f['name']] = dict(cells=cells, delete_rows=deletes or [])
    order = next(f for f in files if Path(f['name']).name.startswith('全部'))
    assert order['rows'][1]['H'] == 'Product Name'
    fake = {n:r for n,r in order['rows'].items() if n>2 and TOKEN in r.get('H','').lower()}
    assert len(fake) == 5
    valid = {n:r for n,r in order['rows'].items() if n>2 and r.get('Z') and r['B'] not in ('已取消','未付款','已退款')}
    rejected = {n:r for n,r in valid.items() if n in fake}
    kept = {n:r for n,r in valid.items() if n not in fake}
    assert len(rejected)==4 and len(kept)==2
    assert all(r['Z'].startswith('07/10/2026') for r in valid.values())
    assert len({r['A'] for r in valid.values()}) == len(valid), 'Order totals require deduplicating multi-SKU orders'
    assert sum(number(r['W']) for r in rejected.values()) == Decimal('11.98')
    assert all(number(r['J'])==1 and number(r['K'])==0 for r in valid.values())
    fake_ids = {r['A'] for r in fake.values()}
    put(order, deletes=sorted(fake))
    for f in files:
        name, rows = Path(f['name']).name, f['rows']
        if name.startswith('product_list'):
            columns = [c for c,label in rows[4].items() if c not in ('A','B','C') and SALES.search(label)]
            cells = {}
            for n,r in rows.items():
                if n<=4 or TOKEN not in r.get('A','').lower():
                    continue
                for c in columns:
                    v=r.get(c,'')
                    if v not in ('','-','/') and number(v)!=0:
                        cells[f'{c}{n}']='RM0.00' if v.startswith('RM') else '0.00%' if v.endswith('%') else '0'
            put(f,cells)
        elif name.startswith('affiliate'):
            deletes = [n for n,r in rows.items() if n>1 and (any(TOKEN in str(v).lower() for v in r.values()) or any(str(v) in fake_ids for v in r.values()))]
            put(f,deletes=deletes)
        elif name.startswith('Product data'):
            assert not any(TOKEN in str(v).lower() for r in rows.values() for v in r.values()), 'Unexpected Tomato product-ad row'
    shop=next(f for f in files if Path(f['name']).name.startswith('Shop'))
    rows=shop['rows']
    assert rows[1]['A']=='分析日期：07/10/2026'
    assert number(rows[4]['B'])==sum(number(r['W']) for r in valid.values())
    assert number(rows[4]['C'])==len(valid) and number(rows[4]['E'])==sum(number(r['J']) for r in valid.values())
    assert number(rows[4]['H'])==sum(number(r['W'])+number(r['N']) for r in valid.values())
    gmv=sum(number(r['W']) for r in kept.values())
    qty=sum(number(r['J']) for r in kept.values())
    values={'B':money(gmv),'C':str(len(kept)),'D':str(len(kept)),'E':str(qty),'G':str(len(kept)),
            'H':money(sum(number(r['W'])+number(r['N']) for r in kept.values())),
            'K':str(Decimal(len(kept))/number(rows[4]['J'])),'P':money(gmv/len(kept))}
    cells={f'{c}4':v for c,v in values.items()}
    fake_hours=Counter(datetime.strptime(r['Z'],'%d/%m/%Y %H:%M:%S').strftime('%Y-%m-%dT%H:00:00') for r in rejected.values())
    for n,r in rows.items():
        if r.get('A') not in fake_hours:
            continue
        hour=r['A']
        original=[x for x in valid.values() if datetime.strptime(x['Z'],'%d/%m/%Y %H:%M:%S').strftime('%Y-%m-%dT%H:00:00')==hour]
        retained=[x for x in kept.values() if datetime.strptime(x['Z'],'%d/%m/%Y %H:%M:%S').strftime('%Y-%m-%dT%H:00:00')==hour]
        assert number(r['B'])==sum(number(x['W']) for x in original) and number(r['C'])==len(original)
        amount=sum((number(x['W']) for x in retained),Decimal(0))
        new={'B':money(amount),'C':str(len(retained)),'D':str(len(retained)),'E':str(sum((number(x['J']) for x in retained),Decimal(0))),
             'G':str(len(retained)),'K':str(Decimal(len(retained))/number(r['J'])) if number(r['J']) else '0','P':money(amount/len(retained)) if retained else '0'}
        cells.update({f'{c}{n}':v for c,v in new.items()})
    assert sum(number(r.get('B','0')) for n,r in rows.items() if r.get('A','').startswith('2026-10-07T'))==number(rows[4]['B'])
    put(shop,cells)
    campaign=next(f for f in files if Path(f['name']).name.startswith('Campaign'))
    rows=campaign['rows']; total_n=next(n for n,r in rows.items() if r.get('A')=='-'); total=rows[total_n]
    normal_ads=[r for f in files if '/新建文件夹 (2)/Product data' in f['name'] for n,r in f['rows'].items() if n>1]
    ad_orders=sum(number(r['E']) for r in normal_ads)
    remaining_hours={datetime.strptime(r['Z'],'%d/%m/%Y %H:%M:%S').strftime('%Y-%m-%d %H:00:00') for r in kept.values()}
    tomato_hours={datetime.strptime(r['Z'],'%d/%m/%Y %H:%M:%S').strftime('%Y-%m-%d %H:00:00') for r in rejected.values()}
    candidates={n:r for n,r in rows.items() if r.get('A') in tomato_hours-remaining_hours and number(r['C'])>0}
    removed_count=sum(number(r['C']) for r in candidates.values()); removed_revenue=sum(number(r['E']) for r in candidates.values())
    assert removed_count==1 and number(total['C'])-removed_count==ad_orders==2
    assert removed_revenue==Decimal('.73')
    revenue=number(total['E'])-removed_revenue
    cells={f'{c}{n}':'0' if c=='C' else '0.00' for n in candidates for c in ('C','D','E','F')}
    cells.update({f'C{total_n}':str(int(ad_orders)),f'D{total_n}':money(number(total['B'])/ad_orders),f'E{total_n}':money(revenue),f'F{total_n}':money(revenue/number(total['B']))})
    put(campaign,cells)
    plan=[]
    for name,change in changes.items():
        f=by_name[name]; rows=f['rows']; width=max(index(c)+1 for r in rows.values() for c in r)
        matrix=[['']*width for _ in range(max(rows))]
        for n,r in rows.items():
            for c,v in r.items(): matrix[n-1][index(c)]=v
        xml=read(BASE/name)[1]
        numeric=[c.attrib['r'] for r in xml.findall('m:sheetData/m:row',NS) for c in r.findall('m:c',NS) if c.attrib['r'] in change['cells'] and c.attrib.get('t','n')=='n']
        plan.append(dict(name=name,source_hash=f['hash'],sheet=f['sheet'],matrix=matrix,numeric_cells=numeric,**change))
    (ROOT/'inventory.json').write_text(json.dumps(files,ensure_ascii=False),encoding='utf-8')
    (ROOT/'plan.json').write_text(json.dumps(plan,ensure_ascii=False),encoding='utf-8')
    summary=dict(scanned=len(files),modified=len(plan),fake_paid_orders=len(rejected),cancelled_fake_orders=len(fake)-len(rejected),removed_ids=sorted(fake_ids),gmv_after=str(gmv),orders_after=len(kept),qty_after=str(qty),ad_spend=total['B'],ad_orders_after=str(ad_orders),ad_revenue_after=str(revenue),removed_ad_revenue=str(removed_revenue))
    (ROOT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False))
    print('MODIFIED',*[p['name'] for p in plan],sep='\n')

def verify():
    inventory=json.loads((ROOT/'inventory.json').read_text(encoding='utf-8'))
    plan=json.loads((ROOT/'plan.json').read_text(encoding='utf-8'))
    summary=json.loads((ROOT/'summary.json').read_text(encoding='utf-8'))
    expected_changes={f['name'] for f in plan}
    actual_changes={f['name'] for f in inventory if hashlib.sha256((BASE/f['name']).read_bytes()).hexdigest()!=f['hash']}
    assert actual_changes==expected_changes and len(actual_changes)==6
    for f in inventory:
        rows=read(BASE/f['name'])[2]; name=Path(f['name']).name
        if name.startswith('全部'):
            assert len(rows)==4 and all(TOKEN not in r.get('H','').lower() for r in rows.values())
            assert sum(number(r['W']) for n,r in rows.items() if n>2)==Decimal('83.27')
        elif name.startswith('affiliate'):
            assert all(not any(TOKEN in str(v).lower() or str(v) in summary['removed_ids'] for v in r.values()) for n,r in rows.items() if n>1)
        elif name.startswith('product_list'):
            for n,r in rows.items():
                if n>4 and TOKEN in r.get('A','').lower():
                    assert all(v in ('','-','/') or number(v)==0 for c,v in r.items() if c not in ('A','B','C') and SALES.search(rows[4].get(c,'')))
        elif name.startswith('Shop'):
            assert rows[4]['B']=='83.27' and rows[4]['C']==rows[4]['D']==rows[4]['E']==rows[4]['G']=='2'
            assert rows[4]['H']=='96.87'
            hours=[r for r in rows.values() if r.get('A','').startswith('2026-10-07T')]
            assert sum(number(r['B']) for r in hours)==Decimal('83.27') and sum(number(r['C']) for r in hours)==2
        elif name.startswith('Campaign'):
            assert rows[26]['B']=='19.34' and rows[26]['C']=='2' and rows[26]['E']=='23.71'
            before={int(n):r for n,r in f['rows'].items()}
            assert all(rows[n]['B']==r['B'] for n,r in before.items() if n>1)
            assert rows[13]==before[13]
            assert all(number(rows[11][c])==0 for c in ('C','D','E','F'))
            def rounding_gap(data):
                return number(data[26]['E'])-sum(number(r['E']) for r in data.values() if r.get('A','').startswith('2026-10-07 '))
            assert rounding_gap(rows)==rounding_gap(before)==Decimal('.01')
    print('PASS: 90 files checked, exactly 6 changed; Tomato orders and sales removed; shop hourly totals reconcile; original ad spend and rounding gap preserved; Product data unchanged')

if __name__=='__main__':
    verify() if '--verify' in sys.argv else prepare()
