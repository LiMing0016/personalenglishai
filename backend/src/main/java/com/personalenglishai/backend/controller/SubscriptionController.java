package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.controller.dto.MockSubscriptionPurchaseRequest;
import com.personalenglishai.backend.controller.dto.RedeemSubscriptionRequest;
import com.personalenglishai.backend.service.subscription.SubscriptionService;
import com.personalenglishai.backend.service.subscription.dto.SubscriptionPlanResponse;
import com.personalenglishai.backend.service.subscription.dto.SubscriptionStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> plans() {
        ApiResponse<List<SubscriptionPlanResponse>> body = ApiResponse.success(subscriptionService.listPlans());
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> me(@RequestAttribute("userId") Long userId) {
        ApiResponse<SubscriptionStatusResponse> body = ApiResponse.success(subscriptionService.getCurrentSubscription(userId));
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/mock-purchase")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> mockPurchase(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody MockSubscriptionPurchaseRequest request) {
        ApiResponse<SubscriptionStatusResponse> body = ApiResponse.success(
                subscriptionService.mockPurchase(userId, request.getPlanCode())
        );
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> redeem(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody RedeemSubscriptionRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<SubscriptionStatusResponse> body = ApiResponse.success(
                subscriptionService.redeemCode(userId, request.getCode(), resolveClientIp(httpRequest))
        );
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
