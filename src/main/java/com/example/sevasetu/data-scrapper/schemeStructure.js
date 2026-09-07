import fs from 'node:fs';

async function checkSchemeStructure() {
  try {
    const res = await fetch(
      "https://api.myscheme.gov.in/schemes/v6/public/schemes?slug=sui&lang=en",
      {
        headers: {
          accept: "application/json, text/plain, */*",
          "accept-language": "en-GB,en;q=0.8",
          priority: "u=1, i",
          "sec-ch-ua":
            '"Chromium";v="152", "Not?A_Brand";v="24", "Brave";v="152"',
          "sec-ch-ua-mobile": "?0",
          "sec-ch-ua-platform": '"Linux"',
          "sec-fetch-dest": "empty",
          "sec-fetch-mode": "cors",
          "sec-fetch-site": "same-site",
          "sec-gpc": "1",
          "x-api-key": "tYTy5eEhlu9rFjyxuCr7ra7ACp4dv1RH8gWuHTDc",
        },
        method: "GET",
      }
    );

    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`);
    return await res.json();
  } catch (err) {
    console.error(err);
    return null;
  }
}

checkSchemeStructure().then((res) => {
  if (!res) return;

  // 1. Unwrap the payload
  let root = res.data || res;
  if (Array.isArray(root)) root = root[0];

  // Save the full raw un-flattened payload for inspection
  fs.writeFileSync('raw_scheme.json', JSON.stringify(root, null, 2), 'utf8');

  // 2. Flatten all keys with their types and preview values
  const flattened = {};

  function flatten(obj, currentPath = "") {
    if (obj === null || obj === undefined) {
      flattened[currentPath] = { type: 'null', value: null };
      return;
    }

    if (Array.isArray(obj)) {
      flattened[`${currentPath}[]`] = {
        type: `Array (${obj.length} items)`,
        sampleValue: obj.length > 0 ? obj[0] : []
      };

      // If array items are objects, recurse into the first element to map the structure
      if (obj.length > 0 && typeof obj[0] === 'object' && obj[0] !== null) {
        flatten(obj[0], `${currentPath}[]`);
      }
      return;
    }

    if (typeof obj === 'object') {
      for (const [key, value] of Object.entries(obj)) {
        const nextPath = currentPath ? `${currentPath}.${key}` : key;
        flatten(value, nextPath);
      }
      return;
    }

    // Primitive values (string, number, boolean)
    flattened[currentPath] = {
      type: typeof obj,
      value: obj
    };
  }

  flatten(root);

  // 3. Save flattened key-value map as JSON
  fs.writeFileSync('scheme_structure.json', JSON.stringify(flattened, null, 2), 'utf8');

  // 4. Save a readable text summary for scanning
  const textReport = Object.entries(flattened)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([path, info]) => {
      const val = typeof info.value === 'string' && info.value.length > 80
        ? `${info.value.slice(0, 80)}...`
        : JSON.stringify(info.value ?? info.sampleValue);
      return `[${info.type}] ${path}\n  -> Value: ${val}`;
    })
    .join('\n\n');

  fs.writeFileSync('scheme_structure.txt', textReport, 'utf8');

  console.log('Saved 3 inspection files:');
  console.log(' - raw_scheme.json        (Complete original JSON)');
  console.log(' - scheme_structure.json  (Flattened key/type/value map)');
  console.log(' - scheme_structure.txt   (Human-readable path report)');
});
