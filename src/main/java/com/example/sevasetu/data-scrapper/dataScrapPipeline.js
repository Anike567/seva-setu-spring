import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import ExcelJS from 'exceljs';
import { populateSlugData } from './saveSlugs.js';
import { exportAllSchemesToExcel } from './generateSchemeDetails.js';
import { normalizeSlugs, replaceEmptyCellsWithNA } from './cleanData.js';
import { fetchFilterSlugsAndIdentifier } from './filtersWithCount.js';

const outputDirectory = new URL('./', import.meta.url);
const resolveOutput = (name) => fileURLToPath(new URL(name, outputDirectory));

async function main() {
  const slugs = await populateSlugData();
  await fs.writeFile(resolveOutput('slugs.json'), JSON.stringify(slugs, null, 2), 'utf8');
  console.log(`Saved ${slugs.length} unique slugs`);

  await fetchFilterSlugsAndIdentifier();
  await exportAllSchemesToExcel();
  Promise.all([
    await fetch('http://localhost:8081/data/save-scheme-data'),
    await fetch('http://localhost:8081/data/save-filters-sluf')
  ])

}

main().catch((error) => {

  console.error(`Pipeline failed: ${error.stack || error.message}`);
  process.exitCode = 1;
});
