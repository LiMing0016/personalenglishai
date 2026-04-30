package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.subscription.SubscriptionService;
import com.personalenglishai.backend.service.subscription.dto.CreateRedeemCodesRequest;
import com.personalenglishai.backend.service.subscription.dto.CreateRedeemCodesResponse;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/subscription")
public class AdminSubscriptionController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final SubscriptionService subscriptionService;

    public AdminSubscriptionController(AdminAuthorizationService adminAuthorizationService,
                                       SubscriptionService subscriptionService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/redeem-codes")
    public ResponseEntity<ApiResponse<CreateRedeemCodesResponse>> createRedeemCodes(
            @RequestAttribute("userId") Long adminUserId,
            @Valid @RequestBody CreateRedeemCodesRequest request) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.SUBSCRIPTION_WRITE);
        ApiResponse<CreateRedeemCodesResponse> body = ApiResponse.success(
                subscriptionService.createRedeemCodes(adminUserId, request)
        );
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }
}
