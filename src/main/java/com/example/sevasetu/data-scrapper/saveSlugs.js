const API_KEY = process.env.MYSCHEME_API_KEY;
const PUBLIC_API_KEY = 'tYTy5eEhlu9rFjyxuCr7ra7ACp4dv1RH8gWuHTDc';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const HEADERS = {
  'accept': 'application/json, text/plain, */*',
  'accept-language': 'en-GB,en;q=0.8',
  'origin': 'https://www.myscheme.gov.in',
  'referer': 'https://www.myscheme.gov.in/',
  'user-agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36',
  'x-api-key': API_KEY || PUBLIC_API_KEY
};

async function fetchJson(url, options = {}, maxRetries = 4) {
  for (let attempt = 1; attempt <= maxRetries; attempt += 1) {
    try {
      const response = await fetch(url, { ...options, signal: AbortSignal.timeout(30_000) });
      if (response.status === 429 || response.status >= 500) {
        if (attempt === maxRetries) throw new Error(`HTTP ${response.status}`);
        await sleep(2 ** (attempt - 1) * 1_000);
        continue;
      }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } catch (error) {
      if (attempt === maxRetries) throw error;
      await sleep(2 ** (attempt - 1) * 1_000);
    }
  }
}

async function populateTotal() {
  const url = 'https://api.myscheme.gov.in/search/v6/schemes/facets?lang=en';
  const response = await fetchJson(url, { method: 'GET', headers: HEADERS });
  return Number(response?.data?.summary?.total || 0);
}

async function getSchemes(from = 0, size = 50) {
  const params = new URLSearchParams({
    lang: 'en',
    q: '[]',
    keyword: '',
    sort: '',
    from: String(from),
    size: String(size)
  });

  const url = `https://api.myscheme.gov.in/search/v6/schemes?${params.toString()}`;

  const json = await fetchJson(url, { method: 'GET', headers: HEADERS });
  return json?.data?.hits?.items || [];
}

export async function populateSlugData() {
  const total = await populateTotal();
  if (total === 0) throw new Error('The API returned zero schemes');

  const PAGE_SIZE = 50;
  let from = 0;
  const slugs = new Set();

  while (from < total) {
    const items = await getSchemes(from, PAGE_SIZE);
    const fetchedCount = items.length;
    items.forEach((item) => {
      const value = item.fields?.slug || item.slug || item.id;
      if (value) slugs.add(value);
    });

    if (fetchedCount === 0) throw new Error(`The API returned no items at offset ${from}`);

    from += fetchedCount;
    console.log(`Fetched offset ${from - fetchedCount}..${from} | Collected slugs: ${slugs.size}`);
    await sleep(250);
  }

  return [...slugs];
}


