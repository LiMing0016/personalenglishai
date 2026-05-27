package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.dto.admin.AdminDataCleaningJobResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCleaningOverviewResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCleaningSourceResponse;
import com.personalenglishai.backend.dto.admin.CreateDictionaryDataCleaningSourceRequest;
import com.personalenglishai.backend.dto.admin.CreateDictionaryProbeJobRequest;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.admin.AdminDataCleaningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/data-cleaning")
public class AdminDataCleaningController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminDataCleaningService dataCleaningService;

    public AdminDataCleaningController(AdminAuthorizationService adminAuthorizationService,
                                       AdminDataCleaningService dataCleaningService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.dataCleaningService = dataCleaningService;
    }

    @GetMapping("/overview")
    public ResponseEntity<AdminDataCleaningOverviewResponse> overview(@RequestAttribute("userId") Long adminUserId) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_READ);
        return ResponseEntity.ok(dataCleaningService.getOverview());
    }

    @GetMapping("/sources")
    public ResponseEntity<List<AdminDataCleaningSourceResponse>> listSources(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam(required = false) String sourceType) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_READ);
        return ResponseEntity.ok(dataCleaningService.listSources(sourceType));
    }

    @PostMapping("/dictionary-sources")
    public ResponseEntity<AdminDataCleaningSourceResponse> createDictionarySource(
            @RequestAttribute("userId") Long adminUserId,
            @RequestBody CreateDictionaryDataCleaningSourceRequest request) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_WRITE);
        return ResponseEntity.ok(dataCleaningService.createDictionarySource(adminUserId, request));
    }

    @PostMapping("/dictionary-uploads")
    public ResponseEntity<AdminDataCleaningJobResponse> uploadDictionarySource(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam String sourceCode,
            @RequestParam String displayName,
            @RequestParam(defaultValue = "unknown") String licenseStatus,
            @RequestParam("files") List<MultipartFile> files) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_WRITE);
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode(sourceCode);
        request.setDisplayName(displayName);
        request.setLicenseStatus(licenseStatus);
        return ResponseEntity.ok(dataCleaningService.uploadDictionarySourceAndProbe(adminUserId, request, files));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<AdminDataCleaningJobResponse>> listJobs(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam(required = false) String sourceUid,
            @RequestParam(required = false) String jobType) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_READ);
        return ResponseEntity.ok(dataCleaningService.listJobs(sourceUid, jobType));
    }

    @PostMapping("/dictionary-probe-jobs")
    public ResponseEntity<AdminDataCleaningJobResponse> createDictionaryProbeJob(
            @RequestAttribute("userId") Long adminUserId,
            @RequestBody CreateDictionaryProbeJobRequest request) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_WRITE);
        return ResponseEntity.ok(dataCleaningService.createDictionaryProbeJob(adminUserId, request.getSourceUid()));
    }

    @GetMapping("/jobs/{jobUid}")
    public ResponseEntity<AdminDataCleaningJobResponse> getJob(
            @RequestAttribute("userId") Long adminUserId,
            @PathVariable String jobUid) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CLEANING_READ);
        return ResponseEntity.ok(dataCleaningService.getJob(jobUid));
    }
}
