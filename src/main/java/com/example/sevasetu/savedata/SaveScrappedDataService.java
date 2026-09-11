package com.example.sevasetu.savedata;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.savedata.dto.ReadJsonDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaveScrappedDataService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SaveScrappedDataService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResponseEntity<
        ApiResponse<Map<String, String>>
    > saveFilterWithSlug() {
        try {
            File file = new File(
                "/home/aniket/seva-setu-java/seva-setu-spring/src/main/java/com/example/sevasetu/data-scrapper/filter_slugs.json"
            );

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
                "status",
                "success",
                "totalRead",
                String.valueOf(list.size()),
                "inserted",
                String.valueOf(totalInserted)
            );

            return ResponseEntity.ok(
                ApiResponse.success("filters saved successfully", result)
            );
        } catch (IOException io) {
            throw new RuntimeException(
                "Failed to read filters JSON: " + io.getMessage(),
                io
            );
        }
    }

    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> saveSchemesData() {
        File jsonFile = new File(
            "/home/aniket/seva-setu-java/seva-setu-spring/src/main/java/com/example/sevasetu/data-scrapper/scraped_records.json"
        );

        try {
            Map<String, Object> schemesMap = objectMapper.readValue(
                jsonFile,
                new TypeReference<Map<String, Object>>() {}
            );

            String insertSchemeSql = """
            INSERT INTO schemes (slug, details)
            VALUES (?, ?::jsonb)
            ON CONFLICT (slug) DO UPDATE SET
                details = EXCLUDED.details,
                updated_at = CURRENT_TIMESTAMP;
            """;

            List<Map.Entry<String, Object>> entries = new ArrayList<>(
                schemesMap.entrySet()
            );

            int batchSize = 1000;
            int[][] updateCounts = jdbcTemplate.batchUpdate(
                insertSchemeSql,
                entries,
                batchSize,
                (PreparedStatement ps, Map.Entry<String, Object> entry) -> {
                    ps.setString(1, entry.getKey());
                    try {
                        // Wrap checked exception inside lambda
                        ps.setString(
                            2,
                            objectMapper.writeValueAsString(entry.getValue())
                        );
                    } catch (
                        com.fasterxml.jackson.core.JsonProcessingException e
                    ) {
                        throw new RuntimeException(
                            "Failed to serialize scheme JSON for slug: " +
                                entry.getKey(),
                            e
                        );
                    }
                }
            );

            int totalSaved = 0;
            for (int[] batch : updateCounts) {
                for (int count : batch) {
                    if (count > 0) totalSaved += count;
                }
            }

            Map<String, String> result = Map.of(
                "status",
                "success",
                "totalRead",
                String.valueOf(entries.size()),
                "savedOrUpdated",
                String.valueOf(totalSaved)
            );

            return ResponseEntity.ok(
                ApiResponse.success("schemes saved successfully", result)
            );
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to read schemes JSON: " + e.getMessage(),
                e
            );
        }
    }
}
