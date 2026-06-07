package com.personalenglishai.backend.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.dto.admin.AdminDataCleaningJobResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCleaningSourceResponse;
import com.personalenglishai.backend.dto.admin.AdminDictionaryLibraryResponse;
import com.personalenglishai.backend.dto.admin.AdminDictionaryImportJobResponse;
import com.personalenglishai.backend.dto.admin.CreateDictionaryDataCleaningSourceRequest;
import com.personalenglishai.backend.entity.admin.DataCleaningJob;
import com.personalenglishai.backend.entity.admin.DataCleaningSource;
import com.personalenglishai.backend.entity.admin.DictionaryImportJob;
import com.personalenglishai.backend.entity.admin.DictionaryLibrary;
import com.personalenglishai.backend.mapper.admin.AdminDataCleaningMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminDataCleaningServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void dictionaryProbeReadsMdictHeadersAndExampleSheetMetadata() throws Exception {
        Path mdx = tempDir.resolve("牛津高阶英汉双解词典（第9版）.mdx");
        Path mdd = tempDir.resolve("牛津高阶英汉双解词典（第9版）.mdd");
        Path examples = tempDir.resolve("牛津高阶9中英文对照例句.xlsx");
        writeMdictHeader(mdx, """
                <Dictionary GeneratedByEngineVersion="2.0" RequiredEngineVersion="2.0" Format="Html" KeyCaseSensitive="No" StripKey="Yes" Encrypted="0" Description="Oxford Advanced Learner's English-Chinese Dictionary Number of Entries: 205,027" Title="牛津高阶英汉双解词典（第9版）" Encoding="UTF-8" CreationDate="2019-8-19"/>
                """);
        writeMdictHeader(mdd, """
                <Library_Data GeneratedByEngineVersion="2.0" RequiredEngineVersion="2.0" Format="" KeyCaseSensitive="No" StripKey="No" Encrypted="0" Title="牛津高阶英汉双解词典（第9版）" CreationDate="2019-8-19"/>
                """);
        writeExampleWorkbook(examples);

        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper());
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("oald9");
        request.setDisplayName("牛津高阶英汉双解词典（第9版）");
        request.setLicenseStatus("internal_only");
        request.setMdxPath(mdx.toString());
        request.setMddPath(mdd.toString());
        request.setExamplesPath(examples.toString());

        AdminDataCleaningSourceResponse source = service.createDictionarySource(7L, request);
        AdminDataCleaningJobResponse job = service.createDictionaryProbeJob(7L, source.getSourceUid());

        assertThat(job.getStatus()).isEqualTo("completed");
        assertThat(job.getProgressTotal()).isEqualTo(3);
        assertThat(job.getProgressDone()).isEqualTo(3);
        assertThat(job.getResult()).containsKeys("mdx", "mdd", "examples");

        Map<String, Object> mdxResult = objectMap(job.getResult().get("mdx"));
        assertThat(mdxResult.get("title")).isEqualTo("牛津高阶英汉双解词典（第9版）");
        assertThat(mdxResult.get("format")).isEqualTo("Html");
        assertThat(mdxResult.get("encoding")).isEqualTo("UTF-8");
        assertThat(mdxResult.get("entryCount")).isEqualTo(205027);

        Map<String, Object> examplesResult = objectMap(job.getResult().get("examples"));
        assertThat(examplesResult.get("rowCount")).isEqualTo(88974);
        assertThat(examplesResult.get("columnCount")).isEqualTo(3);
        assertThat(examplesResult.get("headers")).isEqualTo(List.of("序号", "英文", "中文"));

        AdminDataCleaningSourceResponse updatedSource = service.listSources("dictionary").get(0);
        assertThat(updatedSource.getStatus()).isEqualTo("probed");
        assertThat(updatedSource.getMetadata()).isEmpty();
    }

    @Test
    void dictionaryUploadStoresAllowedFilesCreatesSourceAndProbeJob() throws Exception {
        byte[] mdxBytes = mdictBytes("""
                <Dictionary Format="Html" Description="Oxford Number of Entries: 12" Title="Upload Dict" Encoding="UTF-8"/>
                """);
        byte[] mddBytes = mdictBytes("""
                <Library_Data Format="" Title="Upload Dict Resources"/>
                """);
        byte[] examplesBytes = workbookBytes();
        MockMultipartFile mdx = new MockMultipartFile("files", "upload.mdx", "application/octet-stream", mdxBytes);
        MockMultipartFile mdd = new MockMultipartFile("files", "upload.mdd", "application/octet-stream", mddBytes);
        MockMultipartFile examples = new MockMultipartFile("files", "examples.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", examplesBytes);

        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper(), tempDir.resolve("uploads"));
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("upload-oald9");
        request.setDisplayName("上传词典");
        request.setLicenseStatus("internal_only");

        AdminDataCleaningJobResponse job = service.uploadDictionarySourceAndProbe(9L, request, List.of(mdx, mdd, examples));

        assertThat(job.getStatus()).isEqualTo("completed");
        AdminDataCleaningSourceResponse source = service.listSources("dictionary").get(0);
        assertThat(source.getMdxPath()).endsWith("upload.mdx");
        assertThat(source.getMddPath()).endsWith("upload.mdd");
        assertThat(source.getExamplesPath()).endsWith("examples.xlsx");
        assertThat(Files.exists(Path.of(source.getMdxPath()))).isTrue();
        assertThat(source.getMetadata()).isEmpty();
        assertThat(objectMap(job.getResult().get("mdx")).get("entryCount")).isEqualTo(12);
    }

    @Test
    void dictionaryUploadCreatesInstalledDictionaryLibraryRecord() throws Exception {
        byte[] mdxBytes = mdictBytes("""
                <Dictionary GeneratedByEngineVersion="2.0" RequiredEngineVersion="2.0" Format="Html" Description="Oxford Primary Number of Entries: 800" Title="Oxford Primary" Encoding="UTF-8"/>
                """);
        byte[] mddBytes = mdictBytes("""
                <Library_Data Format="" Title="Oxford Primary Resources"/>
                """);
        MockMultipartFile mdx = new MockMultipartFile("files", "oxfordPrimary.mdx", "application/octet-stream", mdxBytes);
        MockMultipartFile mdd = new MockMultipartFile("files", "oxfordPrimary.mdd", "application/octet-stream", mddBytes);

        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper(), tempDir.resolve("uploads"));
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("oxfordPrimary");
        request.setDisplayName("Oxford Primary");
        request.setLicenseStatus("licensed");

        AdminDataCleaningJobResponse job = service.uploadDictionarySourceAndProbe(9L, request, List.of(mdx, mdd));

        assertThat(job.getStatus()).isEqualTo("completed");
        AdminDictionaryLibraryResponse library = service.listDictionaryLibraries().get(0);
        assertThat(library.getDictionaryCode()).isEqualTo("oxfordPrimary");
        assertThat(library.getSourceUid()).isEqualTo(job.getSourceUid());
        assertThat(library.getDisplayName()).isEqualTo("Oxford Primary");
        assertThat(library.getFormat()).isEqualTo("Mdict");
        assertThat(library.getEncoding()).isEqualTo("UTF-8");
        assertThat(library.getEntryCount()).isEqualTo(800L);
        assertThat(library.getMdxFileName()).isEqualTo("oxfordPrimary.mdx");
        assertThat(library.getMddFileName()).isEqualTo("oxfordPrimary.mdd");
        assertThat(library.getMdxSizeBytes()).isEqualTo((long) mdxBytes.length);
        assertThat(library.getMddSizeBytes()).isEqualTo((long) mddBytes.length);
        assertThat(library.getStorageType()).isEqualTo("local");
        assertThat(library.getStatus()).isEqualTo("installed");
        assertThat(library.isEnabled()).isTrue();
    }

    @Test
    void dictionaryImportJobCanBeCreatedForInstalledDictionary() throws Exception {
        byte[] mdxBytes = mdictBytes("""
                <Dictionary Format="Html" Description="Oxford Primary Number of Entries: 800" Title="Oxford Primary" Encoding="UTF-8"/>
                """);
        MockMultipartFile mdx = new MockMultipartFile("files", "oxfordPrimary.mdx", "application/octet-stream", mdxBytes);
        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        CapturingExecutor executor = new CapturingExecutor();
        AdminDataCleaningService service = new AdminDataCleaningService(
                mapper,
                new ObjectMapper(),
                tempDir.resolve("uploads"),
                (library, source, importLimit) -> Map.of("status", "completed", "entries", List.of()),
                executor
        );
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("oxfordPrimary");
        request.setDisplayName("Oxford Primary");
        service.uploadDictionarySourceAndProbe(9L, request, List.of(mdx));
        AdminDictionaryLibraryResponse library = service.listDictionaryLibraries().get(0);

        AdminDictionaryImportJobResponse importJob = service.createDictionaryImportJob(9L, library.getDictionaryUid(), 100);

        assertThat(importJob.getDictionaryUid()).isEqualTo(library.getDictionaryUid());
        assertThat(importJob.getSourceUid()).isEqualTo(library.getSourceUid());
        assertThat(importJob.getStatus()).isEqualTo("queued");
        assertThat(importJob.getImportLimit()).isEqualTo(100);
        assertThat(importJob.getProcessedEntries()).isZero();
        assertThat(service.listDictionaryImportJobs(library.getDictionaryUid())).hasSize(1);
    }

    @Test
    void dictionaryImportJobReturnsQueuedBeforeAsyncWorkerRuns() throws Exception {
        byte[] mdxBytes = mdictBytes("""
                <Dictionary Format="Html" Description="Oxford Primary Number of Entries: 800" Title="Oxford Primary" Encoding="UTF-8"/>
                """);
        MockMultipartFile mdx = new MockMultipartFile("files", "oxfordPrimary.mdx", "application/octet-stream", mdxBytes);
        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        CapturingExecutor executor = new CapturingExecutor();
        DictionaryImportWorker worker = (library, source, importLimit) -> Map.of(
                "status", "completed",
                "summary", Map.of("entry_count", 0),
                "entries", List.of()
        );
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper(), tempDir.resolve("uploads"), worker, executor);
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("oxfordPrimary");
        request.setDisplayName("Oxford Primary");
        service.uploadDictionarySourceAndProbe(9L, request, List.of(mdx));
        AdminDictionaryLibraryResponse library = service.listDictionaryLibraries().get(0);

        AdminDictionaryImportJobResponse importJob = service.createDictionaryImportJob(9L, library.getDictionaryUid(), 0);

        assertThat(importJob.getStatus()).isEqualTo("queued");
        assertThat(importJob.getImportLimit()).isZero();
        assertThat(importJob.getProcessedEntries()).isZero();
        assertThat(service.listDictionaryImportJobs(library.getDictionaryUid()).get(0).getStatus()).isEqualTo("queued");

        executor.runNext();

        AdminDictionaryImportJobResponse completed = service.listDictionaryImportJobs(library.getDictionaryUid()).get(0);
        assertThat(completed.getStatus()).isEqualTo("failed");
        assertThat(completed.getErrorMessage()).contains("未导入任何词条");
    }

    @Test
    void dictionaryImportJobExecutesWorkerAndPersistsStructuredEntries() throws Exception {
        byte[] mdxBytes = mdictBytes("""
                <Dictionary Format="Html" Description="Oxford Primary Number of Entries: 800" Title="Oxford Primary" Encoding="UTF-8"/>
                """);
        MockMultipartFile mdx = new MockMultipartFile("files", "oxfordPrimary.mdx", "application/octet-stream", mdxBytes);
        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        DictionaryImportWorker worker = (library, source, importLimit) -> Map.of(
                "status", "completed",
                "summary", Map.of("entry_count", 1, "sense_count", 1, "example_count", 1, "phrase_count", 1, "warning_count", 1),
                "warnings", List.of("skipped malformed entry broken"),
                "resources", List.of(Map.of("resource_key", "\\pic\\home.png", "resource_type", "image", "file_name", "home.png", "storage_path", "\\pic\\home.png", "size_bytes", 11)),
                "entries", List.of(Map.of(
                        "word", "home",
                        "source_entry_id", "home",
                        "part_of_speech", "noun",
                        "clean_text", "home noun the house you live in",
                        "phonetics", List.of(Map.of("text", "BrE /həʊm/", "region", "BrE")),
                        "senses", List.of(Map.of(
                                "definition_en", "the house you live in",
                                "definition_zh", "家",
                                "examples", List.of(Map.of("text_en", "We are not far from home now.", "text_zh", "我们现在离家不远了。", "source", "entry_html"))
                        )),
                        "phrases", List.of(Map.of("text", "at home", "definition_en", "comfortable", "definition_zh", "自在"))
                ))
        );
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper(), tempDir.resolve("uploads"), worker);
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("oxfordPrimary");
        request.setDisplayName("Oxford Primary");
        service.uploadDictionarySourceAndProbe(9L, request, List.of(mdx));
        AdminDictionaryLibraryResponse library = service.listDictionaryLibraries().get(0);

        AdminDictionaryImportJobResponse importJob = service.createDictionaryImportJob(9L, library.getDictionaryUid(), 100);

        assertThat(importJob.getStatus()).isEqualTo("completed_with_warnings");
        assertThat(importJob.getProcessedEntries()).isEqualTo(1);
        assertThat(importJob.getImportedEntries()).isEqualTo(1);
        assertThat(importJob.getImportedExamples()).isEqualTo(1);
        assertThat(importJob.getImportedPhrases()).isEqualTo(1);
        assertThat(importJob.getResult()).containsKeys("summary", "samples", "failures", "warnings");
        assertThat((List<?>) importJob.getResult().get("samples")).hasSize(1);
        assertThat((List<?>) importJob.getResult().get("failures")).hasSize(1);
        assertThat(mapper.entryRows).hasSize(1);
        assertThat(mapper.senseRows).hasSize(1);
        assertThat(mapper.exampleRows).hasSize(1);
        assertThat(mapper.phraseRows).hasSize(1);
        assertThat(mapper.resourceRows).hasSize(1);
        assertThat(service.listDictionaryEntrySamples(library.getDictionaryUid(), 5)).hasSize(1);
        assertThat(service.listDictionaryImportFailureSamples(importJob.getImportJobUid())).hasSize(1);
    }

    @Test
    void dictionaryImportJobFailsWhenWorkerOnlyReportsWarningsWithoutEntries() throws Exception {
        byte[] mdxBytes = mdictBytes("""
                <Dictionary Format="Html" Description="Oxford Primary Number of Entries: 800" Title="Oxford Primary" Encoding="UTF-8"/>
                """);
        MockMultipartFile mdx = new MockMultipartFile("files", "oxfordPrimary.mdx", "application/octet-stream", mdxBytes);
        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        DictionaryImportWorker worker = (library, source, importLimit) -> Map.of(
                "status", "completed_with_warnings",
                "summary", Map.of("entry_count", 0, "warning_count", 1),
                "warnings", List.of("failed to read mdx entries: missing readmdict"),
                "entries", List.of()
        );
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper(), tempDir.resolve("uploads"), worker);
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("oxfordPrimary");
        request.setDisplayName("Oxford Primary");
        service.uploadDictionarySourceAndProbe(9L, request, List.of(mdx));
        AdminDictionaryLibraryResponse library = service.listDictionaryLibraries().get(0);

        AdminDictionaryImportJobResponse importJob = service.createDictionaryImportJob(9L, library.getDictionaryUid(), 0);

        assertThat(importJob.getStatus()).isEqualTo("failed");
        assertThat(importJob.getImportedEntries()).isZero();
        assertThat(importJob.getFailedEntries()).isEqualTo(1);
        assertThat(importJob.getErrorMessage()).contains("failed to read mdx entries");
    }

    @Test
    void dictionaryImportJobRecordsPersistenceExceptionsWithoutMaskingBatchRead() throws Exception {
        byte[] mdxBytes = mdictBytes("""
                <Dictionary Format="Html" Description="Oxford Primary Number of Entries: 800" Title="Oxford Primary" Encoding="UTF-8"/>
                """);
        MockMultipartFile mdx = new MockMultipartFile("files", "oxfordPrimary.mdx", "application/octet-stream", mdxBytes);
        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        mapper.failNullMessageForSourceEntryId = "broken";
        DictionaryImportWorker worker = (library, source, importLimit) -> Map.of(
                "status", "completed_with_warnings",
                "summary", Map.of("entry_count", 2, "warning_count", 0),
                "entries", List.of(
                        Map.of("word", "home", "source_entry_id", "home", "clean_text", "home noun"),
                        Map.of("word", "broken", "source_entry_id", "broken", "clean_text", "broken noun")
                )
        );
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper(), tempDir.resolve("uploads"), worker);
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("oxfordPrimary");
        request.setDisplayName("Oxford Primary");
        service.uploadDictionarySourceAndProbe(9L, request, List.of(mdx));
        AdminDictionaryLibraryResponse library = service.listDictionaryLibraries().get(0);

        AdminDictionaryImportJobResponse importJob = service.createDictionaryImportJob(9L, library.getDictionaryUid(), 0);

        assertThat(importJob.getStatus()).isEqualTo("completed_with_warnings");
        assertThat(importJob.getImportedEntries()).isEqualTo(1);
        List<Map<String, Object>> failures = service.listDictionaryImportFailureSamples(importJob.getImportJobUid());
        assertThat(failures)
                .extracting((row) -> row.get("message"))
                .contains("NullPointerException")
                .noneMatch((message) -> String.valueOf(message).contains("读取词条批次失败"));
    }

    @Test
    void repeatedDictionaryImportReusesExistingEntryUidAndDoesNotDuplicateRows() throws Exception {
        byte[] mdxBytes = mdictBytes("""
                <Dictionary Format="Html" Description="Oxford Primary Number of Entries: 800" Title="Oxford Primary" Encoding="UTF-8"/>
                """);
        MockMultipartFile mdx = new MockMultipartFile("files", "oxfordPrimary.mdx", "application/octet-stream", mdxBytes);
        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        DictionaryImportWorker worker = (library, source, importLimit) -> Map.of(
                "status", "completed",
                "summary", Map.of("entry_count", 1, "sense_count", 1),
                "entries", List.of(Map.of(
                        "word", "home",
                        "source_entry_id", "home",
                        "clean_text", "home noun",
                        "senses", List.of(Map.of("definition_en", "the house you live in"))
                ))
        );
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper(), tempDir.resolve("uploads"), worker);
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("oxfordPrimary");
        request.setDisplayName("Oxford Primary");
        service.uploadDictionarySourceAndProbe(9L, request, List.of(mdx));
        AdminDictionaryLibraryResponse library = service.listDictionaryLibraries().get(0);

        service.createDictionaryImportJob(9L, library.getDictionaryUid(), 0);
        service.createDictionaryImportJob(9L, library.getDictionaryUid(), 0);

        assertThat(mapper.entryRows).hasSize(1);
        assertThat(mapper.senseRows).hasSize(1);
    }

    @Test
    void dictionaryListResponsesOmitHeavyProbeJsonButKeepSummaryFields() throws Exception {
        byte[] mdxBytes = mdictBytes("""
                <Dictionary Format="Html" Description="Oxford Number of Entries: 12" Title="Upload Dict" Encoding="UTF-8" StyleSheet="very-heavy-style"/>
                """);
        MockMultipartFile mdx = new MockMultipartFile("files", "upload.mdx", "application/octet-stream", mdxBytes);

        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper(), tempDir.resolve("uploads"));
        CreateDictionaryDataCleaningSourceRequest request = new CreateDictionaryDataCleaningSourceRequest();
        request.setSourceCode("summary-oald9");
        request.setDisplayName("摘要词典");

        AdminDataCleaningJobResponse uploadJob = service.uploadDictionarySourceAndProbe(9L, request, List.of(mdx));

        AdminDataCleaningSourceResponse source = service.listSources("dictionary").get(0);
        assertThat(source.getMetadata()).isEmpty();

        AdminDataCleaningJobResponse listJob = service.listJobs(null, "dictionary_probe").get(0);
        Map<String, Object> mdxSummary = objectMap(listJob.getResult().get("mdx"));
        assertThat(mdxSummary.get("entryCount")).isEqualTo(12);
        assertThat(mdxSummary).containsKeys("kind", "fileName", "fileSizeBytes", "title", "format", "encoding");
        assertThat(mdxSummary).doesNotContainKeys("attributes", "description");

        AdminDataCleaningJobResponse detailJob = service.getJob(uploadJob.getJobUid());
        assertThat(objectMap(detailJob.getResult().get("mdx"))).containsKey("attributes");
    }

    @Test
    void dictionaryUploadRejectsDuplicateSourceCodeBeforeStoringFiles() throws Exception {
        Path existingMdx = tempDir.resolve("existing.mdx");
        writeMdictHeader(existingMdx, """
                <Dictionary Format="Html" Description="Oxford Number of Entries: 1" Title="Existing" Encoding="UTF-8"/>
                """);
        FakeAdminDataCleaningMapper mapper = new FakeAdminDataCleaningMapper();
        Path uploadRoot = tempDir.resolve("uploads");
        AdminDataCleaningService service = new AdminDataCleaningService(mapper, new ObjectMapper(), uploadRoot);

        CreateDictionaryDataCleaningSourceRequest existing = new CreateDictionaryDataCleaningSourceRequest();
        existing.setSourceCode("oald9");
        existing.setDisplayName("已存在词典");
        existing.setMdxPath(existingMdx.toString());
        service.createDictionarySource(1L, existing);

        CreateDictionaryDataCleaningSourceRequest duplicate = new CreateDictionaryDataCleaningSourceRequest();
        duplicate.setSourceCode("oald9");
        duplicate.setDisplayName("重复词典");
        MockMultipartFile mdx = new MockMultipartFile("files", "duplicate.mdx", "application/octet-stream", mdictBytes("""
                <Dictionary Format="Html" Description="Oxford Number of Entries: 2" Title="Duplicate" Encoding="UTF-8"/>
                """));

        BizException error = assertThrows(BizException.class,
                () -> service.uploadDictionarySourceAndProbe(2L, duplicate, List.of(mdx)));

        assertThat(error.getMessage()).isEqualTo("词典源编码已存在：oald9");
        assertThat(Files.exists(uploadRoot)).isFalse();
    }

    private static Map<String, Object> objectMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private static void writeMdictHeader(Path path, String header) throws IOException {
        Files.write(path, mdictBytes(header));
    }

    private static byte[] mdictBytes(String header) {
        byte[] headerBytes = header.trim().getBytes(StandardCharsets.UTF_16BE);
        byte[] length = ByteBuffer.allocate(4).putInt(headerBytes.length).array();
        byte[] bytes = new byte[length.length + headerBytes.length + 4];
        System.arraycopy(length, 0, bytes, 0, length.length);
        System.arraycopy(headerBytes, 0, bytes, 4, headerBytes.length);
        return bytes;
    }

    private static void writeExampleWorkbook(Path path) throws IOException {
        Files.write(path, workbookBytes());
    }

    private static byte[] workbookBytes() throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            putEntry(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <workbook><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>
                    """);
            putEntry(zip, "xl/sharedStrings.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <sst><si><t>序号</t></si><si><t>英文</t></si><si><t>中文</t></si></sst>
                    """);
            putEntry(zip, "xl/worksheets/sheet1.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <worksheet><dimension ref="A1:C88974"/><sheetData><row r="1">
                    <c r="A1" t="s"><v>0</v></c>
                    <c r="B1" t="s"><v>1</v></c>
                    <c r="C1" t="s"><v>2</v></c>
                    </row></sheetData></worksheet>
                    """);
        }
        return output.toByteArray();
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final class FakeAdminDataCleaningMapper implements AdminDataCleaningMapper {
        private final Map<String, DataCleaningSource> sources = new LinkedHashMap<>();
        private final Map<String, DataCleaningJob> jobs = new LinkedHashMap<>();
        private final Map<String, DictionaryLibrary> dictionaries = new LinkedHashMap<>();
        private final Map<String, DictionaryImportJob> importJobs = new LinkedHashMap<>();
        private final List<Map<String, Object>> entryRows = new java.util.ArrayList<>();
        private final List<Map<String, Object>> pronunciationRows = new java.util.ArrayList<>();
        private final List<Map<String, Object>> senseRows = new java.util.ArrayList<>();
        private final List<Map<String, Object>> exampleRows = new java.util.ArrayList<>();
        private final List<Map<String, Object>> phraseRows = new java.util.ArrayList<>();
        private final List<Map<String, Object>> resourceRows = new java.util.ArrayList<>();
        private String failNullMessageForSourceEntryId;

        @Override
        public int insertSource(DataCleaningSource source) {
            source.setId((long) sources.size() + 1);
            sources.put(source.getSourceUid(), source);
            return 1;
        }

        @Override
        public int updateSourceMetadata(String sourceUid, String metadataJson, String status) {
            DataCleaningSource source = sources.get(sourceUid);
            source.setMetadataJson(metadataJson);
            source.setStatus(status);
            return 1;
        }

        @Override
        public DataCleaningSource selectSourceByUid(String sourceUid) {
            return sources.get(sourceUid);
        }

        @Override
        public DataCleaningSource selectSourceByCode(String sourceCode) {
            return sources.values().stream()
                    .filter(source -> sourceCode.equals(source.getSourceCode()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<DataCleaningSource> selectSources(String sourceType) {
            return sources.values().stream()
                    .filter(source -> sourceType == null || sourceType.isBlank() || sourceType.equals(source.getSourceType()))
                    .toList();
        }

        @Override
        public long countSources() {
            return sources.size();
        }

        @Override
        public int insertJob(DataCleaningJob job) {
            job.setId((long) jobs.size() + 1);
            jobs.put(job.getJobUid(), job);
            return 1;
        }

        @Override
        public int updateJob(DataCleaningJob job) {
            jobs.put(job.getJobUid(), job);
            return 1;
        }

        @Override
        public DataCleaningJob selectJobByUid(String jobUid) {
            return jobs.get(jobUid);
        }

        @Override
        public List<DataCleaningJob> selectJobs(String sourceUid, String jobType) {
            return jobs.values().stream()
                    .filter(job -> sourceUid == null || sourceUid.isBlank() || sourceUid.equals(job.getSourceUid()))
                    .filter(job -> jobType == null || jobType.isBlank() || jobType.equals(job.getJobType()))
                    .toList();
        }

        @Override
        public List<Map<String, Object>> selectJobStatusCounts() {
            return List.of(Map.of("status", "completed", "count", jobs.values().stream().filter(job -> "completed".equals(job.getStatus())).count()));
        }

        @Override
        public int upsertDictionaryLibrary(DictionaryLibrary library) {
            DictionaryLibrary existing = dictionaries.get(library.getDictionaryCode());
            if (existing == null) {
                library.setId((long) dictionaries.size() + 1);
                dictionaries.put(library.getDictionaryCode(), library);
            } else {
                library.setId(existing.getId());
                dictionaries.put(library.getDictionaryCode(), library);
            }
            return 1;
        }

        @Override
        public List<DictionaryLibrary> selectDictionaryLibraries() {
            return dictionaries.values().stream().toList();
        }

        @Override
        public DictionaryLibrary selectDictionaryLibraryByUid(String dictionaryUid) {
            return dictionaries.values().stream()
                    .filter(dictionary -> dictionaryUid.equals(dictionary.getDictionaryUid()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int insertDictionaryImportJob(DictionaryImportJob job) {
            job.setId((long) importJobs.size() + 1);
            importJobs.put(job.getImportJobUid(), job);
            return 1;
        }

        @Override
        public int updateDictionaryImportJob(DictionaryImportJob job) {
            importJobs.put(job.getImportJobUid(), job);
            return 1;
        }

        @Override
        public DictionaryImportJob selectDictionaryImportJobByUid(String importJobUid) {
            return importJobs.get(importJobUid);
        }

        @Override
        public List<DictionaryImportJob> selectDictionaryImportJobs(String dictionaryUid) {
            return importJobs.values().stream()
                    .filter(job -> dictionaryUid == null || dictionaryUid.isBlank() || dictionaryUid.equals(job.getDictionaryUid()))
                    .toList();
        }

        @Override
        public int updateDictionaryLibraryStatus(String dictionaryUid, String status) {
            DictionaryLibrary library = selectDictionaryLibraryByUid(dictionaryUid);
            if (library != null) {
                library.setStatus(status);
            }
            return library == null ? 0 : 1;
        }

        @Override
        public String selectDictionaryEntryUidBySource(String dictionaryUid, String sourceEntryId) {
            return entryRows.stream()
                    .filter(row -> dictionaryUid.equals(row.get("dictionaryUid")))
                    .filter(row -> sourceEntryId.equals(row.get("sourceEntryId")))
                    .map(row -> String.valueOf(row.get("entryUid")))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int upsertDictionaryEntry(Map<String, Object> entry) {
            if (entry.get("sourceEntryId").equals(failNullMessageForSourceEntryId)) {
                throw new NullPointerException();
            }
            entryRows.removeIf(row -> entry.get("dictionaryUid").equals(row.get("dictionaryUid"))
                    && entry.get("sourceEntryId").equals(row.get("sourceEntryId")));
            entryRows.add(new LinkedHashMap<>(entry));
            return 1;
        }

        @Override
        public int deleteDictionaryEntryChildren(String entryUid) {
            pronunciationRows.removeIf(row -> entryUid.equals(row.get("entryUid")));
            senseRows.removeIf(row -> entryUid.equals(row.get("entryUid")));
            exampleRows.removeIf(row -> entryUid.equals(row.get("entryUid")));
            phraseRows.removeIf(row -> entryUid.equals(row.get("entryUid")));
            return 1;
        }

        @Override
        public int insertDictionaryPronunciation(Map<String, Object> row) {
            pronunciationRows.add(new LinkedHashMap<>(row));
            return 1;
        }

        @Override
        public int insertDictionarySense(Map<String, Object> row) {
            senseRows.add(new LinkedHashMap<>(row));
            return 1;
        }

        @Override
        public int insertDictionaryExample(Map<String, Object> row) {
            exampleRows.add(new LinkedHashMap<>(row));
            return 1;
        }

        @Override
        public int insertDictionaryPhrase(Map<String, Object> row) {
            phraseRows.add(new LinkedHashMap<>(row));
            return 1;
        }

        @Override
        public int upsertDictionaryResource(Map<String, Object> row) {
            resourceRows.add(new LinkedHashMap<>(row));
            return 1;
        }

        @Override
        public List<Map<String, Object>> selectDictionaryEntrySamples(String dictionaryUid, int limit) {
            return entryRows.stream()
                    .filter(row -> dictionaryUid.equals(row.get("dictionaryUid")))
                    .limit(limit)
                    .map(row -> {
                        Map<String, Object> sample = new LinkedHashMap<>();
                        sample.put("entryUid", row.get("entryUid"));
                        sample.put("headword", row.get("headword"));
                        sample.put("partOfSpeech", row.get("partOfSpeech"));
                        sample.put("cleanText", row.get("cleanText"));
                        sample.put("qualityScore", row.get("qualityScore"));
                        return sample;
                    })
                    .toList();
        }
    }

    private static final class CapturingExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            Runnable task = tasks.poll();
            if (task != null) {
                task.run();
            }
        }
    }
}
