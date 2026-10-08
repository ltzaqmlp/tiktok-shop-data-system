import fs from 'node:fs/promises';
import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';
const base = 'C:/Users/Administrator/Desktop/新建文件夹/10-02/马来/';
for (const [name, range, tag] of [
  ['全部 笔订单-2026-10-07-10_27.xlsx', 'A1:BD8', 'orders'],
  ['Shop Analytics_Key metrics_20261007 (1).xlsx', 'A3:P10', 'shop'],
  ['新建文件夹/加购/product_list_20261001.xlsx', 'A4:W9', 'product'],
  ['Campaign overview data 20261001 - 20261001.xlsx', 'A18:G26', 'ads'],
]) {
  try {
    const wb = await SpreadsheetFile.importXlsx(await FileBlob.load(base + name));
    console.log(tag, JSON.stringify(wb.worksheets.getItemAt(0).getRange(range).values).slice(0, 2500));
    const blob = await wb.render({ sheetName: 'Sheet1', range, scale: 1, format: 'png' });
    await fs.writeFile(new URL(`before-${tag}.png`, import.meta.url), new Uint8Array(await blob.arrayBuffer()));
    console.log('RENDERED', tag);
  } catch (e) { console.log('ERROR', tag, e.message); }
}
