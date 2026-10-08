from scan import ROOT, BASE, NS, read
from zipfile import ZipFile, ZIP_DEFLATED
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET
import hashlib, os

folder = Path((ROOT / 'backup-path.txt').read_text(encoding='utf-8'))
targets = [BASE / '10-03/马来/全部 笔订单-2026-10-07-10_33.xlsx', BASE / '10-06/马来/全部 笔订单-2026-10-07-16_06.xlsx']
archive = folder / '订单结构修复前.zip'
assert not archive.exists(), 'Repair backup already exists; do not overwrite'
payloads = {p: p.read_bytes() for p in targets}
with ZipFile(archive, 'w', ZIP_DEFLATED) as z:
    for p in targets:
        z.writestr(p.relative_to(BASE).as_posix(), payloads[p])
ET.register_namespace('', NS['m'])
for p in targets:
    part_name, xml, before = read(p)
    data = xml.find('m:sheetData', NS)
    counts = Counter(int(row.attrib['r']) for row in data)
    assert max(counts.values()) > 1
    merged = {}
    for row in list(data):
        n = int(row.attrib['r'])
        if n not in merged:
            merged[n] = row
        else:
            merged[n].extend(list(row))
            data.remove(row)
    assert all(len({c.attrib['r'] for c in row}) == len(row) for row in data)
    temp = p.with_name(p.name + '.codex-tmp')
    with ZipFile(p) as original, ZipFile(temp, 'w', ZIP_DEFLATED) as fixed:
        for entry in original.infolist():
            fixed.writestr(entry, ET.tostring(xml, encoding='utf-8', xml_declaration=True) if entry.filename == part_name else original.read(entry.filename))
    assert read(temp)[2] == before, 'Cell content changed'
    assert all(v == 1 for v in Counter(int(r.attrib['r']) for r in read(temp)[1].findall('m:sheetData/m:row', NS)).values())
    with ZipFile(p) as original, ZipFile(temp) as fixed:
        assert all(original.read(n) == fixed.read(n) for n in original.namelist() if n != part_name)
    assert hashlib.sha256(p.read_bytes()).digest() == hashlib.sha256(payloads[p]).digest(), 'Source changed'
    os.replace(temp, p)
    # Keep the verified staging copy in sync for the existing full-data check.
    (ROOT / 'staged' / p.relative_to(BASE)).write_bytes(p.read_bytes())
    print('REPAIRED', p, 'rows', len(merged), 'all cell values preserved')
