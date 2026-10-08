import fs from 'node:fs/promises';
import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';
const base = 'C:/Users/Administrator/Desktop/新建文件夹/10-02/马来/';
const plan = JSON.parse(await fs.readFile(new URL('plan.json', import.meta.url), 'utf8'));
for (const [i, item] of plan.entries()) {
  const wb = await SpreadsheetFile.importXlsx(await FileBlob.load(base + item.name));
  const sheet = wb.worksheets.getItemAt(0);
  // Restore exact source values: these exports contain empty shared strings that import as indexes.
  sheet.getRangeByIndexes(0, 0, item.matrix.length, item.matrix[0].length).values = item.matrix;
  for (const [ref, value] of Object.entries(item.cells)) sheet.getRange(ref).values = [[value]];
  for (const row of item.delete_rows) sheet.getRangeByIndexes(row - 1, 0, 1, item.matrix[0].length).clear({ applyTo: 'contents' });
  wb.recalculate();
  for (const [ref, value] of Object.entries(item.cells)) {
    if (String(sheet.getRange(ref).values[0][0]) !== value) throw new Error(`${item.name}: ${ref}`);
  }
  await (await SpreadsheetFile.exportXlsx(wb)).save(new URL(`authored-${i}.xlsx`, import.meta.url).pathname.replace(/^\/(\w:)/, '$1'));
  console.log('AUTHORED', item.name, Object.keys(item.cells).length, 'cells');
}
