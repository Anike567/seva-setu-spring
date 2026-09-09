package com.example.sevasetu.schemes;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.savedata.dto.SchemeRowDto; // or your Scheme response DTO
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

    public ResponseEntity<ApiResponse<List<SchemeRowDto>>> getSchemes(
        Filter filterDto
    ) {
        StringBuilder sql = new StringBuilder(
            """
            SELECT * FROM schemes
            WHERE slug IN (
                SELECT slug FROM filter_slugs WHERE 1=1
            """
        );

        boolean hasIdentifier =
            filterDto != null &&
            filterDto.identifier() != null &&
            !filterDto.identifier().isBlank();
        boolean hasLabel =
            filterDto != null &&
            filterDto.label() != null &&
            !filterDto.label().isBlank();

        if (hasIdentifier) {
            sql.append(" AND identifier = :identifier");
        }
        if (hasLabel) {
            sql.append(" AND label = :label");
        }

        // Close the subquery before LIMIT
        sql.append(") LIMIT 10");

        var spec = jdbcClient.sql(sql.toString());

        if (hasIdentifier) {
            spec = spec.param("identifier", filterDto.identifier());
        }
        if (hasLabel) {
            spec = spec.param("label", filterDto.label());
        }

        List<SchemeRowDto> schemes = spec.query(SchemeRowDto.class).list();

        return ResponseEntity.ok(
            ApiResponse.success("Fetched schemes successfully", schemes)
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
