from pathlib import Path
from zipfile import ZipFile
from collections import Counter
import xml.etree.ElementTree as ET
import hashlib, json, re

ROOT = Path(__file__).parent
BASE = Path(r'C:\Users\Administrator\Desktop\新建文件夹')
NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
TAG = '{' + NS['m'] + '}'
TOKEN = 'tomato facial mask'

def read(path):
    with ZipFile(path) as archive:
        strings = []
        if 'xl/sharedStrings.xml' in archive.namelist():
            strings = [''.join(n.itertext()) for n in ET.fromstring(archive.read('xl/sharedStrings.xml')).findall('m:si', NS)]
        sheets = [n for n in archive.namelist() if n.startswith('xl/worksheets/') and n.endswith('.xml')]
        assert len(sheets) == 1, (path, sheets)
        sheet = sheets[0]
        xml = ET.fromstring(archive.read(sheet))
        rows = {}
        for node in xml.findall('m:sheetData/m:row', NS):
            row = rows.setdefault(int(node.attrib['r']), {})
            for cell in node.findall('m:c', NS):
                value, inline = cell.find('m:v', NS), cell.find('m:is', NS)
                text = (value.text or '') if value is not None else ''.join(inline.itertext()) if inline is not None else ''
                if cell.attrib.get('t') == 's':
                    text = strings[int(text)] if text else ''
                row[re.sub(r'\d', '', cell.attrib['r'])] = text
        return sheet, xml, rows

def scan():
    paths = [p for d in range(1, 8) for p in (BASE / f'10-0{d}').rglob('*') if p.is_file() and not p.name.startswith('~$')]
    print('EXTENSIONS', dict(Counter(p.suffix.lower() for p in paths)))
    cache, bundle = {}, []
    for p in paths:
        assert p.suffix.lower() == '.xlsx', p
        digest = hashlib.sha256(p.read_bytes()).hexdigest()
        if digest not in cache:
            sheet, xml, rows = read(p)
            cache[digest] = (sheet, rows)
        sheet, rows = cache[digest]
        rel = p.relative_to(BASE).as_posix()
        bundle.append({'name': rel, 'hash': digest, 'sheet': sheet, 'rows': rows})
        if p.name.startswith('全部'):
            fake = [(n, {c: r.get(c) for c in ['A', 'B', 'F', 'G', 'H', 'J', 'K', 'N', 'W', 'X', 'Y', 'Z']}) for n, r in rows.items() if n > 2 and TOKEN in r.get('H', '').lower()]
            print('ORDERS', rel, 'N', len(rows) - 2, 'FAKE', json.dumps(fake, ensure_ascii=False))
        elif p.name.startswith('Shop'):
            print('SHOP', rel, 'TITLE', rows.get(1), 'SUMMARY', json.dumps({k: rows.get(4, {}).get(k) for k in ['B', 'C', 'D', 'E', 'G', 'H', 'J', 'K', 'P']}, ensure_ascii=False))
        elif p.name.startswith('Campaign') and '/马来/' in rel:
            print('CAMPAIGN', rel, 'ORDER_ROWS', json.dumps([(n, r) for n, r in rows.items() if n > 1 and r.get('C') not in ['0', '', None]], ensure_ascii=False))
        elif p.name.startswith(('affiliate', 'Product data')):
            fake = [(n, r) for n, r in rows.items() if any(TOKEN in str(v).lower() for v in r.values())]
            if fake:
                print('AD_OR_AFFILIATE', rel, 'FAKE', json.dumps(fake, ensure_ascii=False))
        elif p.name.startswith('product_list') and p.parent.name != '加购' and '/马来/' in rel:
            print('PERIOD', rel, 'DATE', rows.get(1), 'FAKE', json.dumps([(n, {c: r.get(c) for c in ['B', 'D', 'S', 'T', 'U', 'V', 'AJ', 'AK', 'AL', 'AM']}) for n, r in rows.items() if TOKEN in r.get('A', '').lower()], ensure_ascii=False))
    (ROOT / 'inventory.json').write_text(json.dumps(bundle, ensure_ascii=False), encoding='utf-8')
    print('FILES', len(bundle), 'DISTINCT', len(cache))

if __name__ == '__main__':
    scan()
