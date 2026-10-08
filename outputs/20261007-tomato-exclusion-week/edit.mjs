import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';
const base = 'C:/Users/Administrator/Desktop/新建文件夹/';
const plan = JSON.parse(await fs.readFile(new URL(process.argv[2] ?? 'plan.json', import.meta.url), 'utf8'));
const prefix = process.argv[3] ?? '';
for (const [i, item] of plan.entries()) {
  const wb = await SpreadsheetFile.importXlsx(await FileBlob.load(base + item.name));
  const sheet = wb.worksheets.getItemAt(0);
  // Vendor exports have empty shared strings that import as indexes; restore source values.
  const block = sheet.getRangeByIndexes(0, 0, item.matrix.length, item.matrix[0].length);
  block.values = item.matrix.map(row => [...row]);
  const type = item.name.split('/').at(-1);
  const range = type.startsWith('全部') ? `A1:W${item.matrix.length}` : type.startsWith('affiliate') ? `A${Math.max(1, item.delete_rows[0] - 2)}:P${item.delete_rows[0] + 2}` : type.startsWith('Shop') ? `A3:P${item.matrix.length}` : type.startsWith('Campaign') ? 'A1:G26' : type.startsWith('Product data') ? `A1:I${item.matrix.length}` : `D4:AI${item.matrix.length}`;
  for (const phase of ['before', 'after']) {
    if (phase === 'after') {
      for (const [ref, value] of Object.entries(item.cells)) sheet.getRange(ref).values = [[item.numeric_cells.includes(ref) ? Number(value) : value]];
      if (item.delete_rows.length) {
        block.clear({ applyTo: 'contents' });
        const remaining = item.matrix.filter((_, n) => !item.delete_rows.includes(n + 1));
        sheet.getRangeByIndexes(0, 0, remaining.length, remaining[0].length).values = remaining;
      }
      wb.recalculate();
    }
    const png = await wb.render({ sheetName: sheet.name, range, scale: 1, format: 'png' });
    await fs.writeFile(new URL(`${prefix}${phase}-${i}.png`, import.meta.url), new Uint8Array(await png.arrayBuffer()));
  }
  for (const [ref, value] of Object.entries(item.cells)) {
    if (String(sheet.getRange(ref).values[0][0]) !== value) throw new Error(`${item.name}: ${ref}`);
  }
  const errors = await wb.inspect({ kind: 'match', searchTerm: '#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!', options: { useRegex: true, maxResults: 5 }, maxChars: 300 });
  if (!errors.ndjson.includes('matched 0 entries')) throw new Error(errors.ndjson);
  await (await SpreadsheetFile.exportXlsx(wb)).save(fileURLToPath(new URL(`${prefix}authored-${i}.xlsx`, import.meta.url)));
  console.log('AUTHORED', i, item.name, Object.keys(item.cells).length, 'cells', item.delete_rows.length, 'rows');
}
