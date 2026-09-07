import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import ExcelJS from 'exceljs';

const HEADERS = {
  'accept': 'application/json, text/plain, */*',
  'accept-language': 'en-GB,en;q=0.8',
  'origin': 'https://www.myscheme.gov.in',
  'priority': 'u=1, i',
  'sec-ch-ua': '"Chromium";v="152", "Not?A_Brand";v="24", "Brave";v="152"',
  'sec-ch-ua-mobile': '?0',
  'sec-ch-ua-platform': '"Linux"',
  'sec-fetch-dest': 'empty',
  'sec-fetch-mode': 'cors',
  'sec-fetch-site': 'same-site',
  'sec-gpc': '1',
  'user-agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36',
  'x-api-key': process.env.MYSCHEME_API_KEY || 'tYTy5eEhlu9rFjyxuCr7ra7ACp4dv1RH8gWuHTDc'
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const CACHE_FILE = fileURLToPath(new URL('./scraped_records.json', import.meta.url));

// Fetch with Exponential Backoff on 429
async function fetchDataForSlug(slug, maxRetries = 5) {
  const query = new URLSearchParams({ slug, lang: 'en' });
  const url = `https://api.myscheme.gov.in/schemes/v6/public/schemes?${query.toString()}`;

  let retryDelay = 200; // start with 2-second pause

  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      const res = await fetch(url, { method: 'GET', headers: HEADERS, signal: AbortSignal.timeout(30_000) });

      if (res.status === 429) {
        console.warn(`[429 Rate Limit] Pausing ${retryDelay / 1000}s on slug "${slug}" (Attempt ${attempt}/${maxRetries})...`);
        await sleep(retryDelay);
        // retryDelay = 2; // exponential backoff: 2s -> 4s -> 8s -> 16s
        continue;
      }

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } catch (err) {
      if (attempt === maxRetries) {
        console.error(`Gave up on [${slug}]:`, err.message);
        return null;
      }
      await sleep(1000);
    }
  }
  return null;
}

function extractLabel(field) {
  if (!field) return '';
  if (typeof field === 'string') return field.trim();
  if (typeof field === 'object') return field.label || field.name || field.value || '';
  return String(field);
}

function extractArray(arr) {
  if (!Array.isArray(arr)) return '';
  return arr.map(item => (typeof item === 'object' ? item.label || item.name : item)).join(', ');
}

function mapSchemeToExcelRow(response) {
  let record = response?.data || response;
  if (Array.isArray(record)) record = record[0] || {};

  const en = record?.en || {};
  const basic = en.basicDetails || {};
  const content = en.schemeContent || {};
  const eligibility = en.eligibilityCriteria || {};

  return {
    id: record._id || '',
    slug: record.slug || '',
    schemeName: basic.schemeName || 'N/A',
    shortTitle: basic.schemeShortTitle || '',
    ministry: extractLabel(basic.nodalMinistryName),
    department: extractLabel(basic.nodalDepartmentName),
    level: extractLabel(basic.level),
    schemeFor: basic.schemeFor || '',
    targetBeneficiaries: extractArray(basic.targetBeneficiaries),
    categories: extractArray(basic.schemeCategory),
    openDate: basic.schemeOpenDate || '',
    closeDate: basic.schemeCloseDate || 'Open/Ongoing',
    briefDescription: content.briefDescription || '',
    detailedDescription: (content.detailedDescription_md || '').trim(),
    benefits: (content.benefits_md || '').trim(),
    eligibility: (eligibility.eligibilityDescription_md || '').trim(),
    exclusions: (content.exclusions_md || '').replace(/<br\s*\/?>/gi, '').trim()
  };
}

function* getBatches(array, size = 3) {
  for (let i = 0; i < array.length; i += size) {
    yield array.slice(i, i + size);
  }
}

export async function exportAllSchemesToExcel(allSlugs, outputFilename = 'all_schemes.xlsx') {
  if (!Array.isArray(allSlugs) || allSlugs.length === 0) throw new Error('No slugs were provided');

  // Load existing checkpoint if available
  let cachedRecords = {};
  try {
    const cache = await fs.readFile(CACHE_FILE, 'utf8');
    cachedRecords = JSON.parse(cache);
    if (!cachedRecords || Array.isArray(cachedRecords) || typeof cachedRecords !== 'object') {
      throw new Error('Cache must contain an object keyed by slug');
    }
  } catch (error) {
    if (error.code !== 'ENOENT') console.warn(`Ignoring invalid cache: ${error.message}`);
    cachedRecords = {};
  }

  // Only trust records keyed by a current slug. This ignores caches from the old index-based implementation.
  cachedRecords = Object.fromEntries(
    allSlugs
      .filter((slug) => cachedRecords[slug] && cachedRecords[slug].slug === slug)
      .map((slug) => [slug, cachedRecords[slug]])
  );
  console.log(`Resuming from cache: found ${Object.keys(cachedRecords).length} already completed.`);

  // Filter out slugs we already have
  const remainingSlugs = allSlugs.filter(slug => !cachedRecords[slug]);
  console.log(`Remaining to fetch: ${remainingSlugs.length} / ${allSlugs.length}\n`);

  let count = Object.keys(cachedRecords).length;

  for (const slug of remainingSlugs) {
    const batchResponses = await fetchDataForSlug(slug);
    if (!batchResponses) continue;
    cachedRecords[slug] = mapSchemeToExcelRow(batchResponses);

    count += 1;

    // Periodic checkpoint save every 30 items
    if (count % 30 === 0 || count >= allSlugs.length) {
      await fs.writeFile(CACHE_FILE, JSON.stringify(cachedRecords, null, 2), 'utf8');
      console.log(`Progress: ${count} / ${allSlugs.length} (${Math.round((count / allSlugs.length) * 100)}%) — Checkpoint saved.`);
    }

    // 400ms delay to keep overall rate around 7 requests/second
    await sleep(400);
  }

  // Final export from complete cache to Excel
  console.log(`\nGenerating final Excel file from ${Object.keys(cachedRecords).length} records...`);
  const workbook = new ExcelJS.Workbook();
  const worksheet = workbook.addWorksheet('Schemes');

  worksheet.columns = [
    { header: 'ID', key: 'id', width: 26 },
    { header: 'Slug', key: 'slug', width: 25 },
    { header: 'Scheme Name', key: 'schemeName', width: 35 },
    { header: 'Short Title', key: 'shortTitle', width: 15 },
    { header: 'Ministry', key: 'ministry', width: 30 },
    { header: 'Department', key: 'department', width: 25 },
    { header: 'Level', key: 'level', width: 15 },
    { header: 'For', key: 'schemeFor', width: 15 },
    { header: 'Target Beneficiaries', key: 'targetBeneficiaries', width: 30 },
    { header: 'Categories', key: 'categories', width: 25 },
    { header: 'Open Date', key: 'openDate', width: 15 },
    { header: 'Close Date', key: 'closeDate', width: 15 },
    { header: 'Brief Description', key: 'briefDescription', width: 45 },
    { header: 'Detailed Description', key: 'detailedDescription', width: 50 },
    { header: 'Benefits', key: 'benefits', width: 45 },
    { header: 'Eligibility', key: 'eligibility', width: 45 },
    { header: 'Exclusions', key: 'exclusions', width: 30 }
  ];

  const headerRow = worksheet.getRow(1);
  headerRow.font = { bold: true, color: { argb: 'FFFFFFFF' } };
  headerRow.fill = {
    type: 'pattern',
    pattern: 'solid',
    fgColor: { argb: 'FF1F4E79' }
  };

  for (const slug of allSlugs) {
    const record = cachedRecords[slug];
    if (record) worksheet.addRow(record);
  }

  await workbook.xlsx.writeFile(outputFilename);
  console.log(`Finished! Excel written to ${outputFilename}`);
}
