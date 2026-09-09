package com.example.sevasetu.savedata;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.savedata.dto.ReadJsonDto;
import com.example.sevasetu.savedata.dto.SchemeRowDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SaveScrappedDataService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SaveScrappedDataService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> saveFilterWithSlug() {
        try {
            File file = new File("/home/aniket/seva-setu-java/seva-setu-spring/src/main/java/com/example/sevasetu/data-scrapper/filter_slugs.json");

            List<ReadJsonDto> list = objectMapper.readValue(
                file,
                new TypeReference<List<ReadJsonDto>>() {}
            );

            String sql = """
            INSERT INTO filter_slugs (identifier, label, slug)
            VALUES (?, ?, ?)
            ON CONFLICT (identifier, label, slug) DO NOTHING;
                """;

            int batchSize = 1000;
            int[][] updateCounts = jdbcTemplate.batchUpdate(
                sql,
                list,
                batchSize,
                (PreparedStatement ps, ReadJsonDto item) -> {
                    ps.setString(1, item.identifier());
                    ps.setString(2, item.label());
                    ps.setString(3, item.slug());
                }
            );

            int totalInserted = 0;
            for (int[] batch : updateCounts) {
                for (int count : batch) {
                    if (count > 0) totalInserted += count;
                }
            }

            Map<String, String> result = Map.of(
                "status", "success",
                "totalRead", String.valueOf(list.size()),
                "inserted", String.valueOf(totalInserted)
            );

            return ResponseEntity.ok(ApiResponse.success("filters saved successfully", result));
        } catch (IOException io) {
            throw new RuntimeException("Failed to read JSON: " + io.getMessage(), io);
        }
    }

    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> saveSchemesData() {
        File schemeExcelFile = new File("/home/aniket/seva-setu-java/seva-setu-spring/src/main/java/com/example/sevasetu/data-scrapper/all_schemes_cleaned.xlsx");

        List<SchemeRowDto> schemeRows = new ArrayList<>();
        DataFormatter df = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(schemeExcelFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new IllegalStateException("Excel sheet is missing header row");
            }

            // Map column names to column indexes (case-insensitive)
            Map<String, Integer> colMap = new HashMap<>();
            for (int col = 0; col < headerRow.getLastCellNum(); col++) {
                String headerVal = df.formatCellValue(headerRow.getCell(col)).trim();
                if (!headerVal.isEmpty()) {
                    colMap.put(headerVal.toLowerCase(), col);
                }
            }

            // Read all data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String slug = getCellValue(row, colMap, "slug", df);
                if (slug.isBlank()) continue;

                schemeRows.add(new SchemeRowDto(
                    getCellValue(row, colMap, "id", df),
                    slug,
                    getCellValue(row, colMap, "schemename", df),
                    getCellValue(row, colMap, "shorttitle", df),
                    getCellValue(row, colMap, "ministry", df),
                    getCellValue(row, colMap, "department", df),
                    getCellValue(row, colMap, "level", df),
                    getCellValue(row, colMap, "schemefor", df),
                    getCellValue(row, colMap, "targetbeneficiaries", df),
                    getCellValue(row, colMap, "categories", df),
                    getCellValue(row, colMap, "opendate", df),
                    getCellValue(row, colMap, "closedate", df),
                    getCellValue(row, colMap, "briefdescription", df),
                    getCellValue(row, colMap, "detaileddescription", df),
                    getCellValue(row, colMap, "benefits", df),
                    getCellValue(row, colMap, "eligibility", df),
                    getCellValue(row, colMap, "exclusions", df)
                ));
            }

            String insertSchemeSql = """
                INSERT INTO schemes (
                    id, slug, scheme_name, short_title, ministry, department, level,
                    scheme_for, target_beneficiaries, categories, open_date, close_date,
                    brief_description, detailed_description, benefits, eligibility, exclusions
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?
                )
                ON CONFLICT (slug) DO UPDATE SET
                    id = EXCLUDED.id,
                    scheme_name = EXCLUDED.scheme_name,
                    short_title = EXCLUDED.short_title,
                    ministry = EXCLUDED.ministry,
                    department = EXCLUDED.department,
                    level = EXCLUDED.level,
                    scheme_for = EXCLUDED.scheme_for,
                    target_beneficiaries = EXCLUDED.target_beneficiaries,
                    categories = EXCLUDED.categories,
                    open_date = EXCLUDED.open_date,
                    close_date = EXCLUDED.close_date,
                    brief_description = EXCLUDED.brief_description,
                    detailed_description = EXCLUDED.detailed_description,
                    benefits = EXCLUDED.benefits,
                    eligibility = EXCLUDED.eligibility,
                    exclusions = EXCLUDED.exclusions
                """;

            jdbcTemplate.batchUpdate(
                insertSchemeSql,
                schemeRows,
                1000,
                (PreparedStatement ps, SchemeRowDto s) -> {
                    ps.setString(1, s.id());
                    ps.setString(2, s.slug());
                    ps.setString(3, s.schemeName());
                    ps.setString(4, s.shortTitle());
                    ps.setString(5, s.ministry());
                    ps.setString(6, s.department());
                    ps.setString(7, s.level());
                    ps.setString(8, s.schemeFor());
                    ps.setString(9, s.targetBeneficiaries());
                    ps.setString(10, s.categories());
                    ps.setString(11, s.openDate());
                    ps.setString(12, s.closeDate());
                    ps.setString(13, s.briefDescription());
                    ps.setString(14, s.detailedDescription());
                    ps.setString(15, s.benefits());
                    ps.setString(16, s.eligibility());
                    ps.setString(17, s.exclusions());
                }
            );

            Map<String, String> result = Map.of(
                "status", "success",
                "totalRead", String.valueOf(schemeRows.size())
            );

            return ResponseEntity.ok(ApiResponse.success("schemes saved successfully", result));

        } catch (IOException e) {
            throw new RuntimeException("Failed to read Excel file: " + e.getMessage(), e);
        }
    }

    private String getCellValue(Row row, Map<String, Integer> colMap, String colName, DataFormatter df) {
        Integer colIdx = colMap.get(colName.toLowerCase());
        if (colIdx == null) return "";
        return df.formatCellValue(row.getCell(colIdx)).trim();
    }
}
