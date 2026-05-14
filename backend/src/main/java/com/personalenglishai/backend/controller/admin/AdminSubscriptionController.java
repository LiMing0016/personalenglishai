package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.admin.AdminSubscriptionQuotaRuleUpdateRequest;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.admin.AdminSubscriptionService;
import com.personalenglishai.backend.service.subscription.SubscriptionService;
import com.personalenglishai.backend.service.subscription.dto.CreateRedeemCodesRequest;
import com.personalenglishai.backend.service.subscription.dto.CreateRedeemCodesResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminSubscriptionController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final SubscriptionService subscriptionService;
    private final AdminSubscriptionService adminSubscriptionService;

    public AdminSubscriptionController(AdminAuthorizationService adminAuthorizationService,
                                       SubscriptionService subscriptionService,
                                       AdminSubscriptionService adminSubscriptionService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.subscriptionService = subscriptionService;
        this.adminSubscriptionService = adminSubscriptionService;
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<AdminPageResponse<Map<String, Object>>> listSubscriptions(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String planCode,
            @RequestParam(required = false) String subscriptionStatus,
            @RequestParam(required = false) Boolean overLimit,
            @RequestParam(required = false) String expiresFrom,
            @RequestParam(required = false) String expiresTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.SUBSCRIPTION_READ);
        return ResponseEntity.ok(adminSubscriptionService.listSubscriptions(
                keyword, planCode, subscriptionStatus, overLimit, expiresFrom, expiresTo, page, size
        ));
    }

    @GetMapping("/subscriptions/overview")
    public ResponseEntity<Map<String, Object>> subscriptionOverview(@RequestAttribute("userId") Long adminUserId) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.SUBSCRIPTION_READ);
        return ResponseEntity.ok(adminSubscriptionService.getOverview());
    }

    @GetMapping("/subscriptions/daily-stats")
    public ResponseEntity<List<Map<String, Object>>> subscriptionDailyStats(@RequestAttribute("userId") Long adminUserId,
                                                                            @RequestParam(required = false) String dateFrom,
                                                                            @RequestParam(required = false) String dateTo) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.SUBSCRIPTION_READ);
        return ResponseEntity.ok(adminSubscriptionService.listDailyStats(dateFrom, dateTo));
    }

    @GetMapping("/subscription/quota-rules")
    public ResponseEntity<List<Map<String, Object>>> listQuotaRules(@RequestAttribute("userId") Long adminUserId) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.SUBSCRIPTION_READ);
        return ResponseEntity.ok(adminSubscriptionService.listQuotaRules());
    }

    @PutMapping("/subscription/quota-rules/{planCode}")
    public ResponseEntity<Map<String, Object>> updateQuotaRule(@RequestAttribute("userId") Long adminUserId,
                                                               @PathVariable String planCode,
                                                               @RequestBody AdminSubscriptionQuotaRuleUpdateRequest request,
                                                               HttpServletRequest httpRequest) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.SUBSCRIPTION_WRITE);
        return ResponseEntity.ok(adminSubscriptionService.updateQuotaRule(adminUserId, planCode, request, httpRequest));
    }

    @PostMapping("/subscription/redeem-codes")
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
