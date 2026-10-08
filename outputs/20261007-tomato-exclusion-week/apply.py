from scan import ROOT, BASE, NS, TAG, TOKEN, read
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from decimal import Decimal
import copy, csv, ctypes, hashlib, json, os, re, sys, uuid

plan = json.loads((ROOT / 'plan.json').read_text(encoding='utf-8'))
inventory = json.loads((ROOT / 'inventory.json').read_text(encoding='utf-8'))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def writable():
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateFileW.argtypes = [ctypes.c_wchar_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p]
    kernel.CreateFileW.restype = ctypes.c_void_p
    kernel.CloseHandle.argtypes = [ctypes.c_void_p]
    locked = []
    for item in plan:
        target = (BASE / item['name']).resolve()
        assert target.is_relative_to(BASE.resolve()) and item['name'] in {f['name'] for f in inventory}
        handle = kernel.CreateFileW(str(target), 0xC0000000, 0, None, 3, 0x80, None)
        if handle == ctypes.c_void_p(-1).value:
            locked.append((item['name'], ctypes.get_last_error()))
        else:
            kernel.CloseHandle(handle)
    assert not locked, ('Files locked; originals have not been modified', locked)

def backup():
    writable()
    for item in inventory:
        assert digest(BASE / item['name']) == item['hash'], ('Source changed; rescan required', item['name'])
    folder = Path(r'C:\Users\Administrator\Desktop\刷单清理备份') / ('20261007_10-01至10-07_Tomato_' + uuid.uuid4().hex[:6])
    folder.mkdir(parents=True)
    archive_path = folder / '修改前全部原文件.zip'
    with ZipFile(archive_path, 'w', ZIP_DEFLATED) as archive:
        for item in inventory:
            archive.write(BASE / item['name'], item['name'])
    with ZipFile(archive_path) as archive:
        assert archive.testzip() is None and len(archive.namelist()) == len(inventory)
        assert all(hashlib.sha256(archive.read(item['name'])).hexdigest() == item['hash'] for item in inventory)
    (ROOT / 'backup-path.txt').write_text(str(folder), encoding='utf-8')
    print('BACKUP', folder, 'FILES', len(inventory))

def apply(in_memory_rollback=False):
    import xml.etree.ElementTree as ET
    import openpyxl
    ET.register_namespace('', NS['m'])
    folder = ROOT if in_memory_rollback else Path((ROOT / 'backup-path.txt').read_text(encoding='utf-8'))
    archive_path = folder / '修改前全部原文件.zip'
    originals = {item['name']: (BASE / item['name']).read_bytes() for item in plan} if in_memory_rollback else None
    if not in_memory_rollback:
        assert archive_path.is_file()
    staged = ROOT / 'staged'
    audit = []
    # Prepare and verify all replacements before touching any original.
    for i, item in enumerate(plan):
        source = BASE / item['name']
        assert digest(source) == item['source_hash']
        sheet_path, original, before = read(source)
        generated_path, generated_xml, generated = read(ROOT / f'authored-{i}.xlsx')
        generated_cells = {cell.attrib['r']: cell for row in generated_xml.findall('m:sheetData/m:row', NS) for cell in row.findall('m:c', NS)}
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
            # Merge TikTok's repeated row fragments so standard Excel parsers see every cell.
            merged = {}
            for row in list(part):
                rowno = int(row.attrib['r'])
                if rowno not in merged:
                    merged[rowno] = row
                else:
                    merged[rowno].extend(list(row))
                    part.remove(row)
            dimension = original.find('m:dimension', NS)
            if dimension is not None:
                dim = dimension.attrib['ref']
                dimension.attrib['ref'] = re.sub(r'\d+$', str(max(before) - len(deletes)), dim)
        path = staged / item['name']
        path.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(source) as zin, ZipFile(path, 'w', ZIP_DEFLATED) as zout:
            for entry in zin.infolist():
                payload = ET.tostring(original, encoding='utf-8', xml_declaration=True) if entry.filename == sheet_path else zin.read(entry.filename)
                zout.writestr(entry, payload)
        expected = copy.deepcopy(before)
        for ref, value in item['cells'].items():
            n = int(re.search(r'\d+', ref)[0])
            col = re.sub(r'\d', '', ref)
            audit.append([item['name'], ref, before[n].get(col, ''), value])
            expected[n][col] = value
        for n in deletes:
            audit.append([item['name'], f'删除第{n}行', before[n].get('A', ''), '已删除'])
        expected = {n - sum(d < n for d in deletes): row for n, row in expected.items() if n not in deletes}
        assert read(path)[2] == expected, item['name']
        with ZipFile(source) as zin, ZipFile(path) as zout:
            assert zin.namelist() == zout.namelist()
            assert all(zin.read(n) == zout.read(n) for n in zin.namelist() if n != sheet_path)
        book = openpyxl.load_workbook(path, data_only=False)
        ws = book.worksheets[0]
        for n, row in expected.items():
            for col, value in row.items():
                actual = ws[f'{col}{n}'].value
                assert str(actual or '') == value if value == '' else str(actual) == value, (item['name'], col, n, value, actual)
        book.close()
    writable()
    for item in inventory:
        assert digest(BASE / item['name']) == item['hash'], ('Source changed before commit', item['name'])
    committed = []
    try:
        for item in plan:
            target = BASE / item['name']
            temp = target.with_name(target.name + '.codex-tmp')
            temp.write_bytes((staged / item['name']).read_bytes())
            os.replace(temp, target)
            committed.append(item)
        for item in inventory:
            original = BASE / item['name']
            changed = next((p for p in plan if p['name'] == item['name']), None)
            assert digest(original) == digest(staged / item['name']) if changed else digest(original) == item['hash']
    except BaseException:
        if originals is not None:
            for item in committed:
                (BASE / item['name']).write_bytes(originals[item['name']])
        else:
            with ZipFile(archive_path) as archive:
                for item in committed:
                    (BASE / item['name']).write_bytes(archive.read(item['name']))
        raise
    with (folder / '修改明细.csv').open('w', encoding='utf-8-sig', newline='') as handle:
        writer = csv.writer(handle)
        writer.writerow(['文件', '单元格或行', '修改前', '修改后'])
        writer.writerows(audit)
    summary = json.loads((ROOT / 'summary.json').read_text(encoding='utf-8'))
    (folder / '处理结果.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print('COMMITTED', len(plan), 'FILES; ALL', len(inventory), 'HASH CHECKS PASSED; AUDIT', folder / '修改明细.csv')

if __name__ == '__main__':
    {'backup': backup, 'apply': apply}[sys.argv[1]]()
