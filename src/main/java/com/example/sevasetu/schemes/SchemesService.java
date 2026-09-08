package com.example.sevasetu.schemes;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.schemes.dto.Filter;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class SchemesService {

    private final JdbcClient jdbcClient;

    public SchemesService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ResponseEntity<ApiResponse<List<String>>> getSchemes(
        Filter filterDto
    ) {
        StringBuilder sql = new StringBuilder(
            "SELECT slug FROM filter_slugs WHERE 1=1"
        );
        var clientSpec = jdbcClient.sql("");

        if (
            filterDto.identifier() != null && !filterDto.identifier().isBlank()
        ) {
            sql.append(" AND identifier = :identifier");
        }
        if (filterDto.label() != null && !filterDto.label().isBlank()) {
            sql.append(" AND label = :label");
        }

        sql.append(" LIMIT 10");

        var spec = jdbcClient.sql(sql.toString());

        if (
            filterDto.identifier() != null && !filterDto.identifier().isBlank()
        ) {
            spec = spec.param("identifier", filterDto.identifier());
        }
        if (filterDto.label() != null && !filterDto.label().isBlank()) {
            spec = spec.param("label", filterDto.label());
        }

        List<String> slugs = spec.query(String.class).list();

        return ResponseEntity.ok(
            ApiResponse.success("Fetched schemes successfully", slugs)
        );
    }

    public ResponseEntity<
        ApiResponse<Map<String, List<String>>>
    > getAvailableFilter() {
        String getFilterSql = """
        SELECT identifier, array_agg(DISTINCT label) AS labels
        FROM filter_slugs
        GROUP BY identifier
        """;

        Map<String, List<String>> result = jdbcClient
            .sql(getFilterSql)
            .query((rs, rowNum) -> {
                String identifier = rs.getString("identifier");
                Array labelsSqlArray = rs.getArray("labels");
                String[] labelsArr =
                    labelsSqlArray != null
                        ? (String[]) labelsSqlArray.getArray()
                        : new String[0];
                return Map.entry(identifier, Arrays.asList(labelsArr));
            })
            .list()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return ResponseEntity.ok(
            ApiResponse.success(
                "Fetched available filters successfully",
                result
            )
        );
    }
}
