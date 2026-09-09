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

  const workbookPath = resolveOutput('all_schemes.xlsx');
  const cleanedWorkbookPath = resolveOutput('all_schemes_cleaned.xlsx');
  await exportAllSchemesToExcel(slugs, workbookPath);

  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.readFile(workbookPath);
  const worksheet = workbook.worksheets[0];
  replaceEmptyCellsWithNA(worksheet);
  normalizeSlugs(worksheet);
  await workbook.xlsx.writeFile(cleanedWorkbookPath);
  await fetchFilterSlugsAndIdentifier();

  Promise.all([
    await fetch('http://localhost:8081/data/save-scheme-data'),
    await fetch('http://localhost:8081/data/save-filters-sluf')
  ])
  console.log(`Excel file cleaned and saved to ${cleanedWorkbookPath}`);
}

main().catch((error) => {

  console.error(`Pipeline failed: ${error.stack || error.message}`);
  process.exitCode = 1;
});
