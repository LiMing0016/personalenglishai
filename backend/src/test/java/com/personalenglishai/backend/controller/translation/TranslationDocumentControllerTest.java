package com.personalenglishai.backend.controller.translation;

import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAgentAnswerResponse;
import com.personalenglishai.backend.dto.translation.TranslationDocumentUserBookmarkDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentWorkspaceStateDto;
import com.personalenglishai.backend.dto.translation.TranslationSourceCitationDto;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.translation.ExportedTranslationDocumentFile;
import com.personalenglishai.backend.service.translation.StoredTranslationDocumentFile;
import com.personalenglishai.backend.service.translation.TranslationDocumentBookmarkExportService;
import com.personalenglishai.backend.service.translation.TranslationDocumentFileStorage;
import com.personalenglishai.backend.service.translation.TranslationDocumentImportService;
import com.personalenglishai.backend.service.translation.TranslationDocumentKnowledgeStore;
import com.personalenglishai.backend.service.translation.TranslationDocumentParseService;
import com.personalenglishai.backend.service.translation.TranslationDocumentSourceAnswerService;
import com.personalenglishai.backend.service.translation.TranslationDocumentWorkspaceStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TranslationDocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class TranslationDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TranslationDocumentParseService parseService;

    @MockBean
    private TranslationDocumentImportService importService;

    @MockBean
    private TranslationDocumentKnowledgeStore knowledgeStore;

    @MockBean
    private TranslationDocumentFileStorage fileStorage;

    @MockBean
    private TranslationDocumentSourceAnswerService sourceAnswerService;

    @MockBean
    private TranslationDocumentWorkspaceStateService workspaceStateService;

    @MockBean
    private TranslationDocumentBookmarkExportService bookmarkExportService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @TempDir
    Path tempDir;

    @Test
    void returnsPersistedPdfFileForWorkspaceRefresh() throws Exception {
        byte[] pdfBytes = "%PDF-1.7\nstable source".getBytes(StandardCharsets.UTF_8);
        Path pdfPath = tempDir.resolve("source.pdf");
        Files.write(pdfPath, pdfBytes);
        when(fileStorage.findByDocumentId("doc-001")).thenReturn(Optional.of(new StoredTranslationDocumentFile(
                "doc-001",
                "source.pdf",
                "application/pdf",
                pdfBytes.length,
                "f".repeat(64),
                "local",
                "doc-001/source.pdf",
                "/api/translation/documents/doc-001/file",
                pdfPath
        )));

        mockMvc.perform(get("/api/translation/documents/doc-001/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''source.pdf")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, pdfBytes.length))
                .andExpect(content().bytes(pdfBytes));
    }

    @Test
    void returnsNotFoundWhenPersistedPdfMetadataPointsToMissingFile() throws Exception {
        Path missingPath = tempDir.resolve("missing.pdf");
        when(fileStorage.findByDocumentId("doc-missing")).thenReturn(Optional.of(new StoredTranslationDocumentFile(
                "doc-missing",
                "missing.pdf",
                "application/pdf",
                0,
                "f".repeat(64),
                "local",
                "doc-missing/missing.pdf",
                "/api/translation/documents/doc-missing/file",
                missingPath
        )));

        mockMvc.perform(get("/api/translation/documents/doc-missing/file"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("翻译文档原文件不存在"));
    }

    @Test
    void returnsSourceGroundedAgentAnswerWithCitations() throws Exception {
        TranslationSourceCitationDto citation = new TranslationSourceCitationDto();
        citation.setDocumentId("doc-001");
        citation.setChunkId("doc-001-c2");
        citation.setPageNumber(55);
        citation.setElementId("p55-e2");
        citation.setBbox("[[100,200],[500,200],[500,260],[100,260]]");
        citation.setQuote("动态空间扩容会在容量不足时申请更大的数组。");

        TranslationDocumentAgentAnswerResponse response = new TranslationDocumentAgentAnswerResponse();
        response.setAnswer("基于当前文档的 source chunks 回答。");
        response.setCitations(List.of(citation));
        when(sourceAnswerService.answer(eq("doc-001"), any())).thenReturn(response);

        mockMvc.perform(post("/api/translation/documents/doc-001/agent-answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "解释动态空间扩容",
                                  "selectedText": "动态空间扩容",
                                  "pageNumber": 55,
                                  "elementId": "p55-e2",
                                  "bbox": "[[100,200],[500,200],[500,260],[100,260]]"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("基于当前文档的 source chunks 回答。"))
                .andExpect(jsonPath("$.citations[0].documentId").value("doc-001"))
                .andExpect(jsonPath("$.citations[0].pageNumber").value(55))
                .andExpect(jsonPath("$.citations[0].elementId").value("p55-e2"))
                .andExpect(jsonPath("$.citations[0].bbox").value("[[100,200],[500,200],[500,260],[100,260]]"));
    }

    @Test
    void savesWorkspaceStateForNotesBookmarksAndResumeContext() throws Exception {
        TranslationDocumentUserBookmarkDto bookmark = new TranslationDocumentUserBookmarkDto();
        bookmark.setId("bookmark-1");
        bookmark.setTitle("复杂度公式");
        bookmark.setPageNumber(28);
        bookmark.setLevel(2);

        TranslationDocumentWorkspaceStateDto response = new TranslationDocumentWorkspaceStateDto();
        response.setCurrentPage(28);
        response.setActiveBlockId("p28-e1");
        response.setCollapsedOutlineItemIds(List.of("chapter-1"));
        response.setUserBookmarks(List.of(bookmark));
        when(workspaceStateService.save(eq("doc-001"), any())).thenReturn(response);

        mockMvc.perform(put("/api/translation/documents/doc-001/workspace-state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPage": 28,
                                  "activeBlockId": "p28-e1",
                                  "collapsedOutlineItemIds": ["chapter-1"],
                                  "userBookmarks": [
                                    {"id": "bookmark-1", "title": "复杂度公式", "pageNumber": 28, "level": 2}
                                  ],
                                  "studyNotes": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(28))
                .andExpect(jsonPath("$.activeBlockId").value("p28-e1"))
                .andExpect(jsonPath("$.collapsedOutlineItemIds[0]").value("chapter-1"))
                .andExpect(jsonPath("$.userBookmarks[0].title").value("复杂度公式"));
    }

    @Test
    void downloadsPdfCopyWithWorkspaceBookmarks() throws Exception {
        byte[] pdfBytes = "%PDF-1.7\nbookmarked".getBytes(StandardCharsets.UTF_8);
        when(bookmarkExportService.exportWithBookmarks("doc-001")).thenReturn(new ExportedTranslationDocumentFile(
                "source-bookmarks.pdf",
                "application/pdf",
                pdfBytes
        ));

        mockMvc.perform(get("/api/translation/documents/doc-001/file-with-bookmarks"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''source-bookmarks.pdf")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, pdfBytes.length))
                .andExpect(content().bytes(pdfBytes));
    }
}
