package com.example.sevasetu.savedata;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.savedata.dto.ReadJsonDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
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

    // Inject JdbcTemplate instead of JdbcClient for native batch operations
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
                ON CONFLICT (slug) DO NOTHING
            """;

            // Send up to 1000 records per network packet
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

            // Count total rows written
            int totalInserted = 0;
            for (int[] batch : updateCounts) {
                for (int count : batch) {
                    if (count > 0) {
                        totalInserted += count;
                    }
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
                "Failed to read JSON: " + io.getMessage(),
                io
            );
        }
    }

    // public ResourcePoolEntry<Map<String, List<String>>> saveSchemeInfo()
}
