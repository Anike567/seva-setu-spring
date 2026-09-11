package com.example.sevasetu.schemes;

import java.sql.Array;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.savedata.dto.SchemeRowDto;
import com.example.sevasetu.schemes.dto.Filter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SchemesService {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public SchemesService(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<ApiResponse<List<SchemeRowDto>>> getSchemes(Filter filterDto) {
        boolean hasIdentifier = filterDto != null && filterDto.identifier() != null && !filterDto.identifier().isBlank();
        boolean hasLabel = filterDto != null && filterDto.label() != null && !filterDto.label().isBlank();

        StringBuilder sql = new StringBuilder("""
            SELECT id, slug, details::text AS details_json, created_at, updated_at
            FROM schemes
            """);

        // If filters exist, restrict by matching slugs in filter_slugs
        if (hasIdentifier || hasLabel) {
            sql.append("""
                WHERE slug IN (
                    SELECT slug FROM filter_slugs WHERE 1=1
                """);

            if (hasIdentifier) {
                sql.append(" AND identifier = :identifier");
            }
            if (hasLabel) {
                sql.append(" AND label = :label");
            }

            sql.append(")");
        }

        sql.append(" ORDER BY created_at DESC LIMIT 10");

        var spec = jdbcClient.sql(sql.toString());

        if (hasIdentifier) {
            spec = spec.param("identifier", filterDto.identifier().trim());
        }
        if (hasLabel) {
            spec = spec.param("label", filterDto.label().trim());
        }

        List<SchemeRowDto> schemes = spec.query((rs, rowNum) -> {
            String jsonText = rs.getString("details_json");
            Map<String, Object> detailsMap;

            try {
                detailsMap = (jsonText != null && !jsonText.isBlank())
                    ? objectMapper.readValue(jsonText, new TypeReference<Map<String, Object>>() {})
                    : Collections.emptyMap();
            } catch (Exception e) {
                detailsMap = Collections.emptyMap();
            }

            return new SchemeRowDto(
                rs.getObject("id", UUID.class),
                rs.getString("slug"),
                detailsMap,
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            );
        }).list();

        return ResponseEntity.ok(
            ApiResponse.success("Fetched schemes successfully", schemes)
        );
    }

    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getAvailableFilter() {
        String getFilterSql = """
            SELECT identifier, array_agg(DISTINCT label) AS labels
            FROM filter_slugs
            GROUP BY identifier
            ORDER BY identifier
            """;

        Map<String, List<String>> result = jdbcClient
            .sql(getFilterSql)
            .query((rs, rowNum) -> {
                String identifier = rs.getString("identifier");
                Array labelsSqlArray = rs.getArray("labels");

                String[] labelsArr = new String[0];
                if (labelsSqlArray != null) {
                    Object arrayObj = labelsSqlArray.getArray();
                    if (arrayObj instanceof String[] stringArray) {
                        labelsArr = stringArray;
                    } else if (arrayObj instanceof Object[] objectArray) {
                        labelsArr = Arrays.stream(objectArray)
                            .map(String::valueOf)
                            .toArray(String[]::new);
                    }
                }

                return Map.entry(identifier, Arrays.asList(labelsArr));
            })
            .list()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return ResponseEntity.ok(
            ApiResponse.success("Fetched available filters successfully", result)
        );
    }
}
