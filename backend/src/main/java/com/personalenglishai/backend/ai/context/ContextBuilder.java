package com.personalenglishai.backend.ai.context;

import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.service.document.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Builds AIContext from contextRefs.docId/revision.
 */
@Component
public class ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilder.class);
    private final DocumentService documentService;

    public ContextBuilder(DocumentService documentService) {
        this.documentService = documentService;
    }

    public AIContext build(AICommandRequest req, RequestContext ctx) {
        long start = System.currentTimeMillis();
        String intent = req.getIntent() == null ? "" : req.getIntent().trim().toLowerCase(Locale.ROOT);
        String docId = req.getContextRefs() != null ? req.getContextRefs().getDocId() : null;
        String tenantId = ctx.getTenantId();
        String workspaceId = ctx.getWorkspaceId() != null ? ctx.getWorkspaceId() : "default";
        boolean authPresent = ctx.isAuthPresent();

        if (docId == null || docId.isBlank()) {
            log.info("buildContext traceId={} authPresent={} resolvedTenantId={} resolvedWorkspaceId={} docId= found=false latencyMs={}",
                    ctx.getRequestId(), authPresent, tenantId, workspaceId, System.currentTimeMillis() - start);
            if ("generate".equals(intent) || "chat".equals(intent)) {
                return AIContext.success("", null, false);
            }
            return AIContext.failed("docId required", null, false);
        }
        Integer revision = req.getContextRefs().getRevision();

        Optional<DocumentService.DocContent> docOpt = documentService.getContentForAI(tenantId, workspaceId, docId, revision);
        boolean found = docOpt.isPresent();
        log.info("buildContext traceId={} authPresent={} resolvedTenantId={} resolvedWorkspaceId={} docId={} found={} latencyMs={}",
                ctx.getRequestId(), authPresent, tenantId, workspaceId, docId, found, System.currentTimeMillis() - start);

        if (found) {
            String content = docOpt.get().content != null ? docOpt.get().content : "";
            return AIContext.success(content, docId, true);
        }

        if ("generate".equals(intent) || "chat".equals(intent)) {
            return AIContext.success("", docId, false);
        }

        return AIContext.failed("document not found", docId, false);
    }
}
