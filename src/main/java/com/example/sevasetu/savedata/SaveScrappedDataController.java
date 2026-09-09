package com.example.sevasetu.savedata;

import java.util.Map;


import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



import com.example.sevasetu.common.ApiResponse;



@RestController
@RequestMapping("/data")
/**
 * SaveScrappedData
 */
public class SaveScrappedDataController {

    final private SaveScrappedDataService saveScrappeddataService;

    public SaveScrappedDataController(
        SaveScrappedDataService saveScrappedDataService
    ){
        this.saveScrappeddataService = saveScrappedDataService;
    }


    @GetMapping("/save-filters-sluf")
    public ResponseEntity<ApiResponse<Map<String, String>>> saveFilterSlug(){

        return this.saveScrappeddataService.saveFilterWithSlug();
    }

    @GetMapping ("/save-scheme-data")
    public ResponseEntity<ApiResponse<Map<String, String>>> saveSchemeData(){
        return  this.saveScrappeddataService.saveSchemesData();
    }
}
