package com.personalenglishai.backend.controller.document;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.document.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @Nested
    @DisplayName("POST /api/docs")
    class CreateDoc {

        @Test
        @DisplayName("creates doc and returns docId with latestRevision")
        void create_success() throws Exception {
            when(documentService.createDocument("1", "default", 1L, "My Title", "Hello"))
                    .thenReturn(new DocumentService.CreateResult("doc_abc123", 1));

            mockMvc.perform(post("/api/docs")
                            .requestAttr("userId", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"My Title","content":"Hello"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.docId").value("doc_abc123"))
                    .andExpect(jsonPath("$.latestRevision").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/docs/{docId}/revisions")
    class AppendRevision {

        @Test
        @DisplayName("appends revision and returns latestRevision")
        void append_success() throws Exception {
            when(documentService.appendRevision("1", "default", "doc_abc123", 1, "v2", 1L))
                    .thenReturn(new DocumentService.AppendResult(2));

            mockMvc.perform(post("/api/docs/doc_abc123/revisions")
                            .requestAttr("userId", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"expectedLatestRevision":1,"content":"v2"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.latestRevision").value(2));
        }

        @Test
        @DisplayName("returns 409 when revision conflicts")
        void append_conflict() throws Exception {
            when(documentService.appendRevision("1", "default", "doc_abc123", 1, "v2", 1L))
                    .thenThrow(new BizException(ErrorCode.DOC_CONFLICT, "revision conflict"));

            mockMvc.perform(post("/api/docs/doc_abc123/revisions")
                            .requestAttr("userId", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"expectedLatestRevision":1,"content":"v2"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("409002"));
        }
    }

    @Nested
    @DisplayName("GET /api/docs/{docId}")
    class GetDoc {

        @Test
        @DisplayName("returns latest content")
        void get_success() throws Exception {
            when(documentService.getLatestContent("1", "default", "doc_abc123", 1L))
                    .thenReturn(Optional.of(new DocumentService.DocContent("My Title", 3, "latest content")));

            mockMvc.perform(get("/api/docs/doc_abc123")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("My Title"))
                    .andExpect(jsonPath("$.latestRevision").value(3))
                    .andExpect(jsonPath("$.content").value("latest content"));
        }

        @Test
        @DisplayName("returns 404 when doc not found or not accessible")
        void get_notFound() throws Exception {
            when(documentService.getLatestContent("1", "default", "doc_missing", 1L))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/docs/doc_missing")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/docs/{docId}/revisions/{rev}")
    class GetRevision {

        @Test
        @DisplayName("returns specific revision")
        void getRevision_success() throws Exception {
            when(documentService.getContentByRevision("1", "default", "doc_abc123", 2, 1L))
                    .thenReturn(Optional.of(new DocumentService.DocContent("My Title", 2, "revision-2")));

            mockMvc.perform(get("/api/docs/doc_abc123/revisions/2")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("My Title"))
                    .andExpect(jsonPath("$.revision").value(2))
                    .andExpect(jsonPath("$.content").value("revision-2"));
        }

        @Test
        @DisplayName("returns 404 when revision not found")
        void getRevision_notFound() throws Exception {
            when(documentService.getContentByRevision("1", "default", "doc_abc123", 99, 1L))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/docs/doc_abc123/revisions/99")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/docs/{docId}")
    class DeleteDoc {

        @Test
        @DisplayName("soft deletes and returns 204")
        void delete_success() throws Exception {
            doNothing().when(documentService).softDelete("1", "default", "doc_abc123", 1L);

            mockMvc.perform(delete("/api/docs/doc_abc123")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isNoContent());

            verify(documentService).softDelete("1", "default", "doc_abc123", 1L);
        }

        @Test
        @DisplayName("returns 403 when user is not owner")
        void delete_forbidden() throws Exception {
            doThrow(new BizException(ErrorCode.DOC_FORBIDDEN, "not owner"))
                    .when(documentService)
                    .softDelete("1", "default", "doc_abc123", 1L);

            mockMvc.perform(delete("/api/docs/doc_abc123")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("403001"));
        }
    }
}
