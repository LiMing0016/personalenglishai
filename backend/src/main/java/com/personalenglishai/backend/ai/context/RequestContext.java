package com.personalenglishai.backend.ai.context;

/**
 * 请求上下文：tenantId/workspaceId/userId 可从 JWT 取，requestId(traceId) 由 Orchestrator 生成
 */
public class RequestContext {

    private String tenantId;
    private String workspaceId;
    private Long userId;
    private boolean authPresent;
    /** traceId，由 Orchestrator 在 execute 时生成并设置 */
    private String requestId;
    /** dev/local 调试：X-Debug-Fail 头，如 429 */
    private String xDebugFail;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isAuthPresent() {
        return authPresent;
    }

    public void setAuthPresent(boolean authPresent) {
        this.authPresent = authPresent;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getXDebugFail() {
        return xDebugFail;
    }

    public void setXDebugFail(String xDebugFail) {
        this.xDebugFail = xDebugFail;
    }
}
