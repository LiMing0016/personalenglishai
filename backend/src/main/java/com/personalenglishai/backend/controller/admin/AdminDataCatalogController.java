package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.dto.admin.AdminDataCatalogTableDetailResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCatalogTableResponse;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.admin.AdminDataCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/data-catalog")
public class AdminDataCatalogController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminDataCatalogService dataCatalogService;

    public AdminDataCatalogController(AdminAuthorizationService adminAuthorizationService,
                                      AdminDataCatalogService dataCatalogService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.dataCatalogService = dataCatalogService;
    }

    @GetMapping("/tables")
    public ResponseEntity<List<AdminDataCatalogTableResponse>> listTables(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String sensitivity,
            @RequestParam(required = false) Boolean hasAdminRoute) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CATALOG_READ);
        return ResponseEntity.ok(dataCatalogService.listTables(keyword, module, sensitivity, hasAdminRoute));
    }

    @GetMapping("/tables/{tableName}")
    public ResponseEntity<AdminDataCatalogTableDetailResponse> getTable(
            @RequestAttribute("userId") Long adminUserId,
            @PathVariable String tableName) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.DATA_CATALOG_READ);
        return ResponseEntity.ok(dataCatalogService.getTableDetail(tableName));
    }
}
