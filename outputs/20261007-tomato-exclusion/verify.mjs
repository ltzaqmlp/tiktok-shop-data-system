import fs from 'node:fs/promises';
import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';
const plan = JSON.parse(await fs.readFile(new URL('plan.json', import.meta.url), 'utf8'));
for (const [i, item] of plan.entries()) {
  const filename = new URL(`剔除版/${item.name}`, import.meta.url);
  const wb = await SpreadsheetFile.importXlsx(await FileBlob.load(decodeURIComponent(filename.pathname).replace(/^\/(\w:)/, '$1')));
  const sheet = wb.worksheets.getItemAt(0);
  const range = item.name.startsWith('全部') ? 'A1:BD5' : item.name.startsWith('Shop') ? 'A3:P10' : item.name.startsWith('Campaign') ? 'A18:G26' : `D4:AI${item.matrix.length}`;
  // Restore known empty shared strings for accurate inspection of vendor files.
  const expected = item.matrix.filter((_, row) => !item.delete_rows.includes(row + 1));
  sheet.getRangeByIndexes(0, 0, expected.length, expected[0].length).values = expected;
  for (const [ref, value] of Object.entries(item.cells)) sheet.getRange(ref).values = [[value]];
  wb.recalculate();
  const check = await wb.inspect({ kind: 'match', searchTerm: '#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!', options: { useRegex: true, maxResults: 5 }, maxChars: 1000 });
  console.log('SCAN', item.name, check.ndjson);
  const png = await wb.render({ sheetName: sheet.name, range, scale: 1, format: 'png' });
  await fs.writeFile(new URL(`after-${i}.png`, import.meta.url), new Uint8Array(await png.arrayBuffer()));
}
