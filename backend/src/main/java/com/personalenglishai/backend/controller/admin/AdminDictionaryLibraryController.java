package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.dto.admin.AdminDictionaryImportJobResponse;
import com.personalenglishai.backend.dto.admin.AdminDictionaryLibraryResponse;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.admin.AdminDataCleaningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dictionaries")
public class AdminDictionaryLibraryController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminDataCleaningService dataCleaningService;

    public AdminDictionaryLibraryController(AdminAuthorizationService adminAuthorizationService,
                                            AdminDataCleaningService dataCleaningService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.dataCleaningService = dataCleaningService;
    }

    @GetMapping
    public ResponseEntity<List<AdminDictionaryLibraryResponse>> listDictionaries(
            @RequestAttribute("userId") Long adminUserId) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_READ);
        return ResponseEntity.ok(dataCleaningService.listDictionaryLibraries());
    }

    @GetMapping("/{dictionaryUid}")
    public ResponseEntity<AdminDictionaryLibraryResponse> getDictionary(
            @RequestAttribute("userId") Long adminUserId,
            @PathVariable String dictionaryUid) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_READ);
        return ResponseEntity.ok(dataCleaningService.getDictionaryLibrary(dictionaryUid));
    }

    @GetMapping("/{dictionaryUid}/import-jobs")
    public ResponseEntity<List<AdminDictionaryImportJobResponse>> listImportJobs(
            @RequestAttribute("userId") Long adminUserId,
            @PathVariable String dictionaryUid) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_READ);
        return ResponseEntity.ok(dataCleaningService.listDictionaryImportJobs(dictionaryUid));
    }

    @GetMapping("/{dictionaryUid}/entries/samples")
    public ResponseEntity<List<Map<String, Object>>> listEntrySamples(
            @RequestAttribute("userId") Long adminUserId,
            @PathVariable String dictionaryUid,
            @RequestParam(defaultValue = "10") Integer limit) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_READ);
        return ResponseEntity.ok(dataCleaningService.listDictionaryEntrySamples(dictionaryUid, limit));
    }

    @GetMapping("/import-jobs/{importJobUid}/failures")
    public ResponseEntity<List<Map<String, Object>>> listImportFailures(
            @RequestAttribute("userId") Long adminUserId,
            @PathVariable String importJobUid) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_READ);
        return ResponseEntity.ok(dataCleaningService.listDictionaryImportFailureSamples(importJobUid));
    }

    @PostMapping("/{dictionaryUid}/import-jobs")
    public ResponseEntity<AdminDictionaryImportJobResponse> createImportJob(
            @RequestAttribute("userId") Long adminUserId,
            @PathVariable String dictionaryUid,
            @RequestParam(defaultValue = "100") Integer limit) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_WRITE);
        return ResponseEntity.ok(dataCleaningService.createDictionaryImportJob(adminUserId, dictionaryUid, limit));
    }
}
