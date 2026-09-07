async function fetchFilterLabelAndCount() {
  try {
    const res = await fetch(
      "https://api.myscheme.gov.in/search/v6/schemes?lang=en&q=%5B%5D&keyword=&sort=&from=0&size=10",
      {
        headers: {
          accept: "application/json, text/plain, */*",
          "accept-language": "en-GB,en;q=0.8",
          "x-api-key": "tYTy5eEhlu9rFjyxuCr7ra7ACp4dv1RH8gWuHTDc",
        },
        method: "GET",
      }
    );

    const response = await res.json();
    const filters = response.data.facets;

    return filters.map((filter) => ({
      identifier: filter.identifier,
      label: filter.label,
      entries: (filter.entries || []).map((entry) => ({
        label: entry.label,
        count: entry.count,
      })),
    }));
  } catch (err) {
    throw err;
  }
}

function sleep(time = 250) {
  return new Promise((resolve) => setTimeout(resolve, time));
}

async function fetchSlugsForFilters(filters) {
  const result = {};

  // 1. Iterate over every filter category (e.g. caste, gender, schemeCategory)
  for (const filter of filters) {
    const identifier = filter.identifier;
    result[identifier] = {};

    // 2. Iterate over each label option inside the category
    for (const entry of filter.entries) {
      const filterCriteria = [
        {
          identifier: identifier,
          value: entry.label,
        },
      ];

      const queryParams = new URLSearchParams({
        lang: "en",
        q: JSON.stringify(filterCriteria),
        keyword: "",
        sort: "",
        from: "0",
        // Increase size if you need more slugs per category (e.g., entry.count or 50)
        size: Math.min(entry.count, 50).toString(),
      });

      const url = `https://api.myscheme.gov.in/search/v6/schemes?${queryParams}`;

      try {
        const response = await fetch(url, {
          method: "GET",
          headers: {
            accept: "application/json",
            "x-api-key": "tYTy5eEhlu9rFjyxuCr7ra7ACp4dv1RH8gWuHTDc",
          },
        });

        const data = await response.json();

        // Extract slugs from the schemes array returned by Elasticsearch/API
        const hits = data?.data?.hits?.items || data?.data?.schemes || [];
        const slugs = hits
          .map((item) => item.fields?.slug || item.slug)
          .filter(Boolean);

        result[identifier][entry.label] = slugs;
        console.log(`Fetched ${slugs.length} slugs for [${identifier}] -> ${entry.label}`);
      } catch (err) {
        console.error(`Failed fetching slugs for ${entry.label}:`, err.message);
        result[identifier][entry.label] = [];
      }

      // Prevent rate-limiting (429 errors)
      await sleep(250);
    }
  }

  return result;
}

// Execution pipeline
(async () => {
  try {
    const filters = await fetchFilterLabelAndCount();
    console.log(`Found ${filters.length} filter categories. Fetching slugs...`);

    const slugsByIdentifier = await fetchSlugsForFilters(filters);
    console.log(JSON.stringify(slugsByIdentifier, null, 2));
  } catch (err) {
    console.error("Pipeline failed:", err);
  }
})();
