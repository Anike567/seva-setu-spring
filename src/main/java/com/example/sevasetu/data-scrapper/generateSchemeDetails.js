import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

// ==========================================
// CONFIGURATION & PATHS
// ==========================================
const BASE_DIR = path.dirname(fileURLToPath(import.meta.url));
const SLUGS_FILE = path.join(BASE_DIR, "slugs.json");
const OUTPUT_FILE = path.join(BASE_DIR, "scraped_records.json");

const HEADERS = {
  accept: "application/json, text/plain, */*",
  "accept-language": "en-GB,en;q=0.8",
  origin: "https://www.myscheme.gov.in",
  referer: "https://www.myscheme.gov.in/",
  priority: "u=1, i",
  "sec-ch-ua": '"Chromium";v="124", "Not?A_Brand";v="24"',
  "sec-ch-ua-mobile": "?0",
  "sec-ch-ua-platform": '"Linux"',
  "sec-fetch-dest": "empty",
  "sec-fetch-mode": "cors",
  "sec-fetch-site": "same-site",
  "user-agent":
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
  "x-api-key":
    process.env.MYSCHEME_API_KEY || "tYTy5eEhlu9rFjyxuCr7ra7ACp4dv1RH8gWuHTDc",
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// ==========================================
// NETWORK REQUEST WITH RETRY / BACKOFF
// ==========================================
async function fetchDataForSlug(slug, maxRetries = 5) {
  const query = new URLSearchParams({ slug, lang: "en" });
  const url = `https://api.myscheme.gov.in/schemes/v6/public/schemes?${query.toString()}`;

  let retryDelay = 2000;

  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      const res = await fetch(url, {
        method: "GET",
        headers: HEADERS,
        signal: AbortSignal.timeout(20_000),
      });

      if (res.status === 429) {
        console.warn(
          `[429 Rate Limit] Pausing ${retryDelay / 1000}s on slug "${slug}" (Attempt ${attempt}/${maxRetries})...`
        );
        await sleep(retryDelay);
        retryDelay *= 2;
        continue;
      }

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      }

      return await res.json();
    } catch (err) {
      console.warn(
        `[Attempt ${attempt}/${maxRetries}] Failed for "${slug}":`,
        err.cause || err.message
      );

      if (attempt === maxRetries) {
        console.error(`Gave up on slug "${slug}" after ${maxRetries} attempts.`);
        return null;
      }

      await sleep(1500 * attempt);
    }
  }

  return null;
}

// ==========================================
// DATA TRANSFORMATION HELPERS
// ==========================================
function extractLabel(field) {
  if (!field) return "";
  if (typeof field === "string") return field.trim();
  if (typeof field === "object") {
    return field.label || field.name || field.value || "";
  }
  return String(field);
}

function extractArray(arr) {
  if (!Array.isArray(arr)) return "";
  return arr
    .map((item) => (typeof item === "object" ? item.label || item.name : item))
    .filter(Boolean)
    .join(", ");
}

function mapSchemeToExcelRow(response) {
  let record = response?.data || response;
  if (Array.isArray(record)) record = record[0] || {};

  const en = record?.en || {};
  const basic = en.basicDetails || {};
  const content = en.schemeContent || {};
  const eligibility = en.eligibilityCriteria || {};

  return {
    id: record._id || "",
    slug: record.slug || "",
    schemeName: basic.schemeName || "N/A",
    shortTitle: basic.schemeShortTitle || "",
    ministry: extractLabel(basic.nodalMinistryName),
    department: extractLabel(basic.nodalDepartmentName),
    level: extractLabel(basic.level),
    schemeFor: basic.schemeFor || "",
    targetBeneficiaries: extractArray(basic.targetBeneficiaries),
    categories: extractArray(basic.schemeCategory),
    openDate: basic.schemeOpenDate || "",
    closeDate: basic.schemeCloseDate || "Open/Ongoing",
    briefDescription: content.briefDescription || "",
    detailedDescription: (content.detailedDescription_md || "").trim(),
    benefits: (content.benefits_md || "").trim(),
    eligibility: (eligibility.eligibilityDescription_md || "").trim(),
    exclusions: (content.exclusions_md || "")
      .replace(/<br\s*\/?>/gi, "")
      .trim(),
  };
}

// ==========================================
// MAIN EXPORT PIPELINE
// ==========================================
export const exportAllSchemesToExcel = async () => {
  // Read and parse slugs.json safely
  const fileContent = await fs.readFile(SLUGS_FILE, "utf-8");
  const parsedData = JSON.parse(fileContent);

  // Normalize: handles array of strings ["slug1", "slug2"] or objects [{slug: "slug1"}]
  const allSlugs = parsedData
    .map((item) => (typeof item === "string" ? item : item?.slug))
    .filter(Boolean);

  console.log(`Loaded ${allSlugs.length} total slugs from ${SLUGS_FILE}`);

  // Load existing data if file exists to resume progress
  let schemesMap = {};
  try {
    const existingData = await fs.readFile(OUTPUT_FILE, "utf-8");
    schemesMap = JSON.parse(existingData);
    console.log(`Resuming: ${Object.keys(schemesMap).length} schemes already present in ${OUTPUT_FILE}`);
  } catch {
    schemesMap = {};
  }

  let completedCount = 0;
  let skippedCount = 0;

  for (let i = 0; i < allSlugs.length; i++) {
    const slug = allSlugs[i];

    // Resume capability: skip if already in the dictionary
    if (schemesMap[slug]) {
      skippedCount++;
      continue;
    }

    const rawResponse = await fetchDataForSlug(slug);
    if (!rawResponse) {
      console.warn(`Skipping write for "${slug}" due to fetch failure.`);
      continue;
    }

    const rowData = mapSchemeToExcelRow(rawResponse);

    // Save as value under the slug key
    schemesMap[slug] = rowData;
    completedCount++;

    console.log(`[${i + 1}/${allSlugs.length}] Added: ${slug}`);

    // Checkpoint save every 25 schemes
    if (completedCount % 25 === 0) {
      await fs.writeFile(
        OUTPUT_FILE,
        JSON.stringify(schemesMap, null, 2),
        "utf-8"
      );
      console.log(`>>> Checkpoint saved to ${OUTPUT_FILE}`);
    }

    // Throttle requests: 400ms delay between calls
    await sleep(400);
  }

  // Final flush to ensure all newly added schemes are saved
  await fs.writeFile(
    OUTPUT_FILE,
    JSON.stringify(schemesMap, null, 2),
    "utf-8"
  );

  console.log(`\nPipeline completed.`);
  console.log(`- Newly downloaded: ${completedCount}`);
  console.log(`- Skipped (already existed): ${skippedCount}`);
  console.log(`- Total schemes in file: ${Object.keys(schemesMap).length}`);
  console.log(`- Output file: ${OUTPUT_FILE}`);
};

// ==========================================
// RUN SCRIPT
// ==========================================
exportAllSchemesToExcel().catch((error) => {
  console.error("Fatal pipeline error:", error.message);
  if (error.cause) {
    console.error("Root network cause:", error.cause);
  }
  process.exit(1);
});
