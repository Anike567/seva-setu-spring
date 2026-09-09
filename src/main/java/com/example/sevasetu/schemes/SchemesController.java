package com.example.sevasetu.schemes;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.savedata.dto.SchemeRowDto;
import com.example.sevasetu.schemes.dto.Filter;

@RestController
@RequestMapping("/schemes")
public class SchemesController {

    private final SchemesService schemesService;

    public SchemesController(SchemesService schemesService) {
        this.schemesService = schemesService;
    }

    // Accessible via: GET /schemes?identifier=abc&label=xyz
    @GetMapping
    public ResponseEntity<ApiResponse<List<SchemeRowDto>>> getSchemes(
        @RequestParam(name = "identifier", required = false) String identifier,
        @RequestParam(name = "label", required = false) String label
    ) {
        Filter filterDto = new Filter(identifier, label);
        return this.schemesService.getSchemes(filterDto);
    }

    // Accessible via: GET /schemes/available-filter
    @GetMapping("/available-filter")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getAvailableFilter() {
        return this.schemesService.getAvailableFilter();
    }
}
