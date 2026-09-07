export function replaceEmptyCellsWithNA(worksheet) {
    worksheet.eachRow((row) => {
        row.eachCell({ includeEmpty: true }, (cell) => {
            cell.value = cleanText(cell.value);
        });
    });
}

export function normalizeSlugs(worksheet) {
    const slugColumn = worksheet.getRow(1).values.indexOf("Slug");

    if (slugColumn === -1) {
        throw new Error("Slug column not found");
    }

    for (let row = 2; row <= worksheet.rowCount; row++) {
        const cell = worksheet.getCell(row, slugColumn);

        cell.value = cleanSlug(cell.value);
    }
}

export function cleanText(value) {
    if (value === null || value === undefined) {
        return "NA";
    }

    if (typeof value !== "string") {
        return value;
    }

    value = value
        .replace(/\u00A0/g, " ")
        .replace(/[\t\r\n]+/g, " ")
        .replace(/\s+/g, " ")
        .trim();

    return value === "" ? "NA" : value;
}

function cleanSlug(value) {
    if (value === null || value === undefined) {
        return "NA";
    }

    return String(value)
        .trim()
        .toLowerCase()
        .replace(/\s+/g, "-");
}

