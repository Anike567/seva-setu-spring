import fs from 'node:fs/promises';

const slugs = [];
let total = 0;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const HEADERS = {
  'accept': 'application/json, text/plain, */*',
  'accept-language': 'en-GB,en;q=0.8',
  'origin': 'https://www.myscheme.gov.in',
  'referer': 'https://www.myscheme.gov.in/',
  'user-agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36',
  'x-api-key': 'tYTy5eEhlu9rFjyxuCr7ra7ACp4dv1RH8gWuHTDc'
};

async function populateTotal() {
  const url = 'https://api.myscheme.gov.in/search/v6/schemes/facets?lang=en';

  try {
    const res = await fetch(url, { method: 'GET', headers: HEADERS });
    if (!res.ok) throw new Error(`HTTP error: ${res.status}`);

    const response = await res.json();
    total = response?.data?.summary?.total || 0;
    console.log(`Target total schemes: ${total}`);
  } catch (err) {
    console.error('Failed to get total count:', err.message);
  }
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

  try {
    const res = await fetch(url, { method: 'GET', headers: HEADERS });
    if (!res.ok) throw new Error(`HTTP error: ${res.status}`);

    const json = await res.json();
    const items = json?.data?.hits?.items || [];

    items.forEach((item) => {
      const slug = item.fields?.slug || item.slug || item.id;
      if (slug) {
        slugs.push(slug);
      }
    });

    console.log(`Fetched offset ${from}..${from + items.length} | Collected slugs: ${slugs.length}`);
    return items.length;
  } catch (err) {
    console.error(`Request failed at offset ${from}:`, err.message);
    return 0;
  }
}

async function populateSlugData() {
  await populateTotal();

  if (total === 0) {
    console.error('Total is 0, aborting.');
    return;
  }

  const PAGE_SIZE = 50;
  let from = 0;

  while (from < total) {
    const fetchedCount = await getSchemes(from, PAGE_SIZE);

    if (fetchedCount === 0) {
      console.warn('No more items returned, breaking early.');
      break;
    }

    from += PAGE_SIZE;
    await sleep(250);
  }

  console.log(`\nSuccessfully gathered ${slugs.length} total slugs!`);
}

// Notice the async keyword here and the reference to 'slugs'
populateSlugData()
  .then(async () => {
    const filename = 'slugs.json';
    try {
      await fs.writeFile(filename, JSON.stringify(slugs, null, 2), 'utf-8');
      console.log(`Saved ${slugs.length} slugs to ${filename}`);
    } catch (err) {
      console.error('Failed to write JSON file:', err);
    }
  })
  .catch((err) => {
    console.error('Execution error:', err);
  });
