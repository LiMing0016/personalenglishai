package com.personalenglishai.backend.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.admin.AdminDataCleaningJobResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCleaningOverviewResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCleaningSourceResponse;
import com.personalenglishai.backend.dto.admin.AdminDictionaryImportJobResponse;
import com.personalenglishai.backend.dto.admin.AdminDictionaryLibraryResponse;
import com.personalenglishai.backend.dto.admin.CreateDictionaryDataCleaningSourceRequest;
import com.personalenglishai.backend.entity.admin.DataCleaningJob;
import com.personalenglishai.backend.entity.admin.DataCleaningSource;
import com.personalenglishai.backend.entity.admin.DictionaryImportJob;
import com.personalenglishai.backend.entity.admin.DictionaryLibrary;
import com.personalenglishai.backend.mapper.admin.AdminDataCleaningMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@Service
public class AdminDataCleaningService {
    private static final int MAX_MDICT_HEADER_BYTES = 1024 * 1024;
    private static final Pattern XML_ATTR = Pattern.compile("([A-Za-z][A-Za-z0-9_]*)=\"([^\"]*)\"");
    private static final Pattern ENTRY_COUNT = Pattern.compile("Number of Entries:\\s*([0-9,]+)");
    private static final Pattern XLSX_DIMENSION = Pattern.compile("<dimension[^>]*ref=\"(?:[A-Z]+\\d+:)?([A-Z]+)(\\d+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern XLSX_ROW1_CELL = Pattern.compile("<c\\s+([^>]*)>\\s*<v>(.*?)</v>\\s*</c>", Pattern.DOTALL);
    private static final Pattern XLSX_SHEET_NAME = Pattern.compile("<sheet[^>]*name=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern XLSX_SHARED_ITEM = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL);
    private static final Pattern XLSX_TEXT_NODE = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL);
    private static final List<String> ALLOWED_UPLOAD_EXTENSIONS = List.of(".mdx", ".mdd", ".xlsx", ".jpg", ".jpeg", ".png", ".zip");

    private final AdminDataCleaningMapper mapper;
    private final ObjectMapper objectMapper;
    private final Path dictionaryUploadRoot;
    private final DictionaryImportWorker dictionaryImportWorker;
    private final Executor dictionaryImportExecutor;

    @Autowired
    public AdminDataCleaningService(AdminDataCleaningMapper mapper,
                                    ObjectMapper objectMapper,
                                    DictionaryImportWorker dictionaryImportWorker,
                                    @Qualifier("dictionaryImportExecutor") Executor dictionaryImportExecutor,
                                    @Value("${app.data-cleaning.dictionary-upload-dir:storage/data-cleaning/dictionaries}") String dictionaryUploadDir) {
        this(mapper, objectMapper, Path.of(dictionaryUploadDir), dictionaryImportWorker, dictionaryImportExecutor);
    }

    AdminDataCleaningService(AdminDataCleaningMapper mapper, ObjectMapper objectMapper) {
        this(mapper, objectMapper, Path.of("storage/data-cleaning/dictionaries"), (library, source, limit) -> Map.of("status", "pending", "entries", List.of()), Runnable::run);
    }

    AdminDataCleaningService(AdminDataCleaningMapper mapper, ObjectMapper objectMapper, Path dictionaryUploadRoot) {
        this(mapper, objectMapper, dictionaryUploadRoot, (library, source, limit) -> Map.of("status", "pending", "entries", List.of()), Runnable::run);
    }

    AdminDataCleaningService(AdminDataCleaningMapper mapper,
                             ObjectMapper objectMapper,
                             Path dictionaryUploadRoot,
                             DictionaryImportWorker dictionaryImportWorker) {
        this(mapper, objectMapper, dictionaryUploadRoot, dictionaryImportWorker, Runnable::run);
    }

    AdminDataCleaningService(AdminDataCleaningMapper mapper,
                             ObjectMapper objectMapper,
                             Path dictionaryUploadRoot,
                             DictionaryImportWorker dictionaryImportWorker,
                             Executor dictionaryImportExecutor) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.dictionaryUploadRoot = dictionaryUploadRoot.toAbsolutePath().normalize();
        this.dictionaryImportWorker = dictionaryImportWorker;
        this.dictionaryImportExecutor = dictionaryImportExecutor;
    }

    public AdminDataCleaningOverviewResponse getOverview() {
        AdminDataCleaningOverviewResponse response = new AdminDataCleaningOverviewResponse();
        response.setSourceCount(mapper.countSources());
        long jobCount = 0;
        for (Map<String, Object> row : mapper.selectJobStatusCounts()) {
            long count = longValue(row.get("count"));
            jobCount += count;
            String status = stringValue(row.get("status"));
            if ("completed".equals(status)) {
                response.setCompletedJobCount(count);
            } else if ("failed".equals(status)) {
                response.setFailedJobCount(count);
            } else if ("pending".equals(status) || "running".equals(status)) {
                response.setRunningJobCount(response.getRunningJobCount() + count);
            }
        }
        response.setJobCount(jobCount);
        return response;
    }

    public List<AdminDataCleaningSourceResponse> listSources(String sourceType) {
        return mapper.selectSources(sourceType).stream().map(this::toSourceListResponse).toList();
    }

    public AdminDataCleaningSourceResponse createDictionarySource(Long adminUserId, CreateDictionaryDataCleaningSourceRequest request) {
        validateSourceRequest(request);
        requireUniqueSourceCode(request.getSourceCode());
        DataCleaningSource source = new DataCleaningSource();
        source.setSourceUid(newUid("dcs"));
        source.setSourceType("dictionary");
        source.setSourceCode(request.getSourceCode().trim());
        source.setDisplayName(request.getDisplayName().trim());
        source.setLicenseStatus(firstNonBlank(request.getLicenseStatus(), "unknown"));
        source.setMdxPath(blankToNull(request.getMdxPath()));
        source.setMddPath(blankToNull(request.getMddPath()));
        source.setExamplesPath(blankToNull(request.getExamplesPath()));
        source.setCoverImagePath(blankToNull(request.getCoverImagePath()));
        source.setMetadataJson(toJson(Map.of("createdFrom", "admin_data_cleaning_center")));
        source.setStatus("registered");
        source.setCreatedBy(adminUserId);
        mapper.insertSource(source);
        return toSourceResponse(source);
    }

    public AdminDataCleaningJobResponse uploadDictionarySourceAndProbe(Long adminUserId,
                                                                       CreateDictionaryDataCleaningSourceRequest request,
                                                                       List<MultipartFile> files) {
        validateUploadRequest(request, files);
        requireUniqueSourceCode(request.getSourceCode());
        DataCleaningSource source = new DataCleaningSource();
        source.setSourceUid(newUid("dcs"));
        source.setSourceType("dictionary");
        source.setSourceCode(request.getSourceCode().trim());
        source.setDisplayName(request.getDisplayName().trim());
        source.setLicenseStatus(firstNonBlank(request.getLicenseStatus(), "unknown"));
        source.setStatus("registered");
        source.setCreatedBy(adminUserId);

        try {
            StoredDictionaryFiles stored = storeDictionaryFiles(source, files);
            source.setMdxPath(stored.mdxPath());
            source.setMddPath(stored.mddPath());
            source.setExamplesPath(stored.examplesPath());
            source.setCoverImagePath(stored.coverImagePath());
            source.setMetadataJson(toJson(Map.of(
                    "createdFrom", "admin_dictionary_upload",
                    "uploadDir", stored.uploadDir(),
                    "uploadedFiles", stored.uploadedFiles()
            )));
            mapper.insertSource(source);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "failed to store dictionary files: " + ex.getMessage());
        }
        return createDictionaryProbeJob(adminUserId, source.getSourceUid());
    }

    public List<AdminDataCleaningJobResponse> listJobs(String sourceUid, String jobType) {
        return mapper.selectJobs(sourceUid, jobType).stream().map(this::toJobListResponse).toList();
    }

    public List<AdminDictionaryLibraryResponse> listDictionaryLibraries() {
        return mapper.selectDictionaryLibraries().stream().map(this::toDictionaryLibraryResponse).toList();
    }

    public AdminDictionaryLibraryResponse getDictionaryLibrary(String dictionaryUid) {
        DictionaryLibrary library = mapper.selectDictionaryLibraryByUid(dictionaryUid);
        if (library == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "dictionary library not found");
        }
        return toDictionaryLibraryResponse(library);
    }

    public AdminDictionaryImportJobResponse createDictionaryImportJob(Long adminUserId, String dictionaryUid, Integer importLimit) {
        DictionaryLibrary library = mapper.selectDictionaryLibraryByUid(dictionaryUid);
        if (library == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "dictionary library not found");
        }
        DataCleaningSource source = mapper.selectSourceByUid(library.getSourceUid());
        if (source == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "dictionary source not found");
        }
        DictionaryImportJob job = new DictionaryImportJob();
        job.setImportJobUid(newUid("dij"));
        job.setDictionaryUid(library.getDictionaryUid());
        job.setSourceUid(library.getSourceUid());
        job.setStatus("queued");
        job.setImportLimit(normalizeImportLimit(importLimit));
        job.setProcessedEntries(0);
        job.setImportedEntries(0);
        job.setFailedEntries(0);
        job.setImportedExamples(0);
        job.setImportedPhrases(0);
        job.setResultJson(toJson(Map.of("message", "词典正文入库任务已排队")));
        job.setCreatedBy(adminUserId);
        mapper.insertDictionaryImportJob(job);
        mapper.updateDictionaryLibraryStatus(library.getDictionaryUid(), "importing");
        dictionaryImportExecutor.execute(() -> executeDictionaryImportJob(job.getImportJobUid()));
        return toDictionaryImportJobResponse(job);
    }

    public List<AdminDictionaryImportJobResponse> listDictionaryImportJobs(String dictionaryUid) {
        return mapper.selectDictionaryImportJobs(dictionaryUid).stream().map(this::toDictionaryImportJobResponse).toList();
    }

    public List<Map<String, Object>> listDictionaryEntrySamples(String dictionaryUid, Integer limit) {
        return mapper.selectDictionaryEntrySamples(dictionaryUid, Math.min(Math.max(limit == null ? 10 : limit, 1), 50));
    }

    public List<Map<String, Object>> listDictionaryImportFailureSamples(String importJobUid) {
        DictionaryImportJob job = mapper.selectDictionaryImportJobByUid(importJobUid);
        if (job == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "dictionary import job not found");
        }
        Object failures = readJson(job.getResultJson()).get("failures");
        if (!(failures instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> row = asMap(item);
            if (!row.isEmpty()) {
                result.add(row);
            }
        }
        return result;
    }

    public AdminDataCleaningJobResponse getJob(String jobUid) {
        DataCleaningJob job = mapper.selectJobByUid(jobUid);
        if (job == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "job not found");
        }
        return toJobResponse(job);
    }

    public AdminDataCleaningJobResponse createDictionaryProbeJob(Long adminUserId, String sourceUid) {
        if (isBlank(sourceUid)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "sourceUid is required");
        }
        DataCleaningSource source = mapper.selectSourceByUid(sourceUid);
        if (source == null || !"dictionary".equals(source.getSourceType())) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "dictionary source not found");
        }

        DataCleaningJob job = new DataCleaningJob();
        job.setJobUid(newUid("dcj"));
        job.setSourceUid(source.getSourceUid());
        job.setJobType("dictionary_probe");
        job.setStatus("running");
        job.setProgressTotal(countProbeFiles(source));
        job.setProgressDone(0);
        job.setCreatedBy(adminUserId);
        job.setStartedAt(LocalDateTime.now());
        mapper.insertJob(job);

        try {
            Map<String, Object> result = probeDictionarySource(source);
            job.setStatus("completed");
            job.setProgressDone(job.getProgressTotal());
            job.setResultJson(toJson(result));
            job.setFinishedAt(LocalDateTime.now());
            mapper.updateJob(job);
            Map<String, Object> mergedMetadata = new LinkedHashMap<>(readJson(source.getMetadataJson()));
            mergedMetadata.putAll(result);
            mapper.updateSourceMetadata(source.getSourceUid(), toJson(mergedMetadata), "probed");
            upsertInstalledDictionaryLibrary(source, result);
        } catch (Exception ex) {
            job.setStatus("failed");
            job.setErrorMessage(ex.getMessage());
            job.setFinishedAt(LocalDateTime.now());
            mapper.updateJob(job);
        }
        return toJobResponse(job);
    }

    private void upsertInstalledDictionaryLibrary(DataCleaningSource source, Map<String, Object> result) {
        Map<String, Object> mdx = asMap(result.get("mdx"));
        Map<String, Object> mdd = asMap(result.get("mdd"));
        Map<String, Object> examples = asMap(result.get("examples"));
        Map<String, Object> mdxAttributes = asMap(mdx.get("attributes"));

        DictionaryLibrary library = new DictionaryLibrary();
        library.setDictionaryUid(newUid("dict"));
        library.setSourceUid(source.getSourceUid());
        library.setDictionaryCode(source.getSourceCode());
        library.setDisplayName(firstNonBlank(stringValue(mdx.get("title")), source.getDisplayName()));
        library.setDescription(stringValue(mdxAttributes.get("Description")));
        library.setFormat("Mdict");
        library.setEngineVersion(stringValue(mdxAttributes.get("GeneratedByEngineVersion")));
        library.setRequiredEngineVersion(stringValue(mdxAttributes.get("RequiredEngineVersion")));
        library.setEncoding(stringValue(mdx.get("encoding")));
        library.setEntryCount(nullableLong(mdx.get("entryCount")));
        library.setResourceCount(nullableLong(mdd.get("entryCount")));
        library.setMdxFileName(stringValue(mdx.get("fileName")));
        library.setMddFileName(stringValue(mdd.get("fileName")));
        library.setCoverImagePath(source.getCoverImagePath());
        library.setMdxSizeBytes(nullableLong(mdx.get("fileSizeBytes")));
        library.setMddSizeBytes(nullableLong(mdd.get("fileSizeBytes")));
        library.setExamplesCount(exampleDataCount(examples));
        library.setLicenseStatus(source.getLicenseStatus());
        library.setStorageType("local");
        library.setEnabled(true);
        library.setSortOrder(100);
        library.setStatus("installed");
        library.setMetadataJson(toJson(result));
        library.setCreatedBy(source.getCreatedBy());
        mapper.upsertDictionaryLibrary(library);
    }

    private void executeDictionaryImportJob(String importJobUid) {
        DictionaryImportJob job = mapper.selectDictionaryImportJobByUid(importJobUid);
        if (job == null) {
            return;
        }
        DictionaryLibrary library = mapper.selectDictionaryLibraryByUid(job.getDictionaryUid());
        DataCleaningSource source = mapper.selectSourceByUid(job.getSourceUid());
        if (library == null || source == null) {
            job.setStatus("failed");
            job.setErrorMessage("dictionary library or source not found");
            job.setFinishedAt(LocalDateTime.now());
            mapper.updateDictionaryImportJob(job);
            return;
        }
        executeDictionaryImportJob(job, library, source);
    }

    private AdminDictionaryImportJobResponse executeDictionaryImportJob(DictionaryImportJob job,
                                                                        DictionaryLibrary library,
                                                                        DataCleaningSource source) {
        job.setStatus("running");
        job.setStartedAt(LocalDateTime.now());
        job.setResultJson(toJson(Map.of("message", "Python 词典正文入库 worker 处理中")));
        mapper.updateDictionaryImportJob(job);
        try {
            Map<String, Object> workerResult = dictionaryImportWorker.importDictionary(library, source, job.getImportLimit());
            List<Map<String, Object>> failures = new ArrayList<>();
            ImportCounters counters = persistDictionaryImportEntries(library, workerResult, failures);
            int importedResources = persistDictionaryResources(library, workerResult);
            failures.addAll(warningsAsFailures(workerResult.get("warnings")));

            job.setProcessedEntries(counters.processedEntries());
            job.setImportedEntries(counters.importedEntries());
            job.setFailedEntries(failures.size());
            job.setImportedExamples(counters.importedExamples());
            job.setImportedPhrases(counters.importedPhrases());
            job.setStatus(resolveImportStatus(workerResult, counters, failures));
            job.setErrorMessage(resolveImportErrorMessage(job.getStatus(), failures));
            job.setFinishedAt(LocalDateTime.now());
            job.setResultJson(toJson(importResultPayload(workerResult, counters, importedResources, failures)));
            mapper.updateDictionaryImportJob(job);
            mapper.updateDictionaryLibraryStatus(library.getDictionaryUid(), counters.importedEntries() > 0 ? "imported" : "failed");
        } catch (Exception ex) {
            job.setStatus("failed");
            job.setErrorMessage(ex.getMessage());
            job.setFinishedAt(LocalDateTime.now());
            job.setResultJson(toJson(Map.of(
                    "summary", Map.of("entry_count", 0, "sense_count", 0, "example_count", 0, "phrase_count", 0),
                    "samples", List.of(),
                    "failures", List.of(Map.of("message", firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName())))
            )));
            mapper.updateDictionaryImportJob(job);
            mapper.updateDictionaryLibraryStatus(library.getDictionaryUid(), "installed");
        }
        return toDictionaryImportJobResponse(job);
    }

    private ImportCounters persistDictionaryImportEntries(DictionaryLibrary library,
                                                          Map<String, Object> workerResult,
                                                          List<Map<String, Object>> failures) {
        ImportAccumulator accumulator = new ImportAccumulator();
        persistDictionaryImportEntryValues(library, workerResult.get("entries"), failures, accumulator);
        Object batchPathsValue = workerResult.get("entryBatchPaths");
        if (batchPathsValue instanceof List<?> batchPaths) {
            for (Object batchPathValue : batchPaths) {
                String batchPath = stringValue(batchPathValue);
                if (isBlank(batchPath)) {
                    continue;
                }
                try {
                    List<Object> batchEntries = objectMapper.readValue(Path.of(batchPath).toFile(), new TypeReference<>() {});
                    persistDictionaryImportEntryValues(library, batchEntries, failures, accumulator);
                } catch (Exception ex) {
                    failures.add(Map.of("message", "读取词条批次失败: " + batchPath + " - " + ex.getMessage()));
                }
            }
        }
        return accumulator.toCounters();
    }

    private void persistDictionaryImportEntryValues(DictionaryLibrary library,
                                                    Object entriesValue,
                                                    List<Map<String, Object>> failures,
                                                    ImportAccumulator accumulator) {
        if (!(entriesValue instanceof List<?> entries)) {
            return;
        }
        for (Object value : entries) {
            accumulator.processedEntries++;
            Map<String, Object> entry = asMap(value);
            String word = firstNonBlank(stringValue(entry.get("word")), stringValue(entry.get("headword")));
            if (isBlank(word)) {
                failures.add(Map.of("message", "词条缺少 headword", "entry", entry));
                continue;
            }
            try {
                String sourceEntryId = firstNonBlank(stringValue(entry.get("source_entry_id")), stringValue(entry.get("sourceEntryId")), word);
                String existingEntryUid = mapper.selectDictionaryEntryUidBySource(library.getDictionaryUid(), sourceEntryId);
                String entryUid = firstNonBlank(existingEntryUid, newUid("de"));
                String cleanText = firstNonBlank(stringValue(entry.get("clean_text")), stringValue(entry.get("cleanText")), word);
                Map<String, Object> entryRow = new LinkedHashMap<>();
                entryRow.put("entryUid", entryUid);
                entryRow.put("dictionaryUid", library.getDictionaryUid());
                entryRow.put("sourceEntryId", sourceEntryId);
                entryRow.put("headword", word);
                entryRow.put("normalizedHeadword", normalizeHeadword(word));
                entryRow.put("partOfSpeech", firstNonBlank(stringValue(entry.get("part_of_speech")), stringValue(entry.get("partOfSpeech"))));
                entryRow.put("cleanText", cleanText);
                entryRow.put("rawHtml", stringValue(entry.get("raw_html")));
                entryRow.put("qualityScore", qualityScore(entry));
                entryRow.put("sortOrder", accumulator.sortOrder++);
                entryRow.put("metadataJson", toJson(entry));
                mapper.upsertDictionaryEntry(entryRow);
                mapper.deleteDictionaryEntryChildren(entryUid);

                persistPhonetics(entryUid, entry);
                SenseCounters senseCounters = persistSenses(entryUid, entry);
                int phraseCount = persistPhrases(entryUid, entry);
                accumulator.importedExamples += senseCounters.exampleCount();
                accumulator.importedPhrases += phraseCount;
                accumulator.importedEntries++;
                if (accumulator.samples.size() < 5) {
                    accumulator.samples.add(Map.of(
                            "entryUid", entryUid,
                            "headword", word,
                            "partOfSpeech", stringOrEmpty(firstNonBlank(stringValue(entryRow.get("partOfSpeech")))),
                            "definitionEn", stringOrEmpty(firstNonBlank(senseCounters.firstDefinitionEn())),
                            "definitionZh", stringOrEmpty(firstNonBlank(senseCounters.firstDefinitionZh())),
                            "exampleCount", senseCounters.exampleCount(),
                            "phraseCount", phraseCount
                    ));
                }
            } catch (Exception ex) {
                failures.add(Map.of("headword", word, "message", firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName())));
            }
        }
    }

    private void persistPhonetics(String entryUid, Map<String, Object> entry) {
        Object value = entry.get("phonetics");
        if (!(value instanceof List<?> phonetics)) {
            return;
        }
        int sortOrder = 0;
        for (Object item : phonetics) {
            Map<String, Object> phonetic = asMap(item);
            String text = stringValue(phonetic.get("text"));
            if (isBlank(text)) {
                continue;
            }
            mapper.insertDictionaryPronunciation(Map.of(
                    "pronunciationUid", newUid("dp"),
                    "entryUid", entryUid,
                    "region", stringOrEmpty(firstNonBlank(stringValue(phonetic.get("region")))),
                    "phoneticText", text,
                    "audioResourceUid", stringOrEmpty(null),
                    "sortOrder", sortOrder++
            ));
        }
    }

    private SenseCounters persistSenses(String entryUid, Map<String, Object> entry) {
        Object value = entry.get("senses");
        if (!(value instanceof List<?> senses)) {
            return new SenseCounters(0, 0, null, null);
        }
        int senseCount = 0;
        int exampleCount = 0;
        String firstDefinitionEn = null;
        String firstDefinitionZh = null;
        int sortOrder = 0;
        for (Object item : senses) {
            Map<String, Object> sense = asMap(item);
            String senseUid = newUid("ds");
            String definitionEn = firstNonBlank(stringValue(sense.get("definition_en")), stringValue(sense.get("definitionEn")));
            String definitionZh = firstNonBlank(stringValue(sense.get("definition_zh")), stringValue(sense.get("definitionZh")));
            if (firstDefinitionEn == null) {
                firstDefinitionEn = definitionEn;
            }
            if (firstDefinitionZh == null) {
                firstDefinitionZh = definitionZh;
            }
            mapper.insertDictionarySense(Map.of(
                    "senseUid", senseUid,
                    "entryUid", entryUid,
                    "definitionEn", stringOrEmpty(definitionEn),
                    "definitionZh", stringOrEmpty(definitionZh),
                    "sortOrder", sortOrder++,
                    "metadataJson", toJson(sense)
            ));
            senseCount++;
            Object examplesValue = sense.get("examples");
            if (examplesValue instanceof List<?> examples) {
                int exampleSortOrder = 0;
                for (Object exampleValue : examples) {
                    Map<String, Object> example = asMap(exampleValue);
                    String textEn = firstNonBlank(stringValue(example.get("text_en")), stringValue(example.get("textEn")));
                    if (isBlank(textEn)) {
                        continue;
                    }
                    mapper.insertDictionaryExample(Map.of(
                            "exampleUid", newUid("dex"),
                            "senseUid", senseUid,
                            "phraseUid", "",
                            "entryUid", entryUid,
                            "textEn", textEn,
                            "textZh", stringOrEmpty(firstNonBlank(stringValue(example.get("text_zh")), stringValue(example.get("textZh")))),
                            "source", firstNonBlank(stringValue(example.get("source")), "entry_html"),
                            "sortOrder", exampleSortOrder++,
                            "metadataJson", toJson(example)
                    ));
                    exampleCount++;
                }
            }
        }
        return new SenseCounters(senseCount, exampleCount, firstDefinitionEn, firstDefinitionZh);
    }

    private int persistPhrases(String entryUid, Map<String, Object> entry) {
        Object value = entry.get("phrases");
        if (!(value instanceof List<?> phrases)) {
            return 0;
        }
        int imported = 0;
        int sortOrder = 0;
        for (Object item : phrases) {
            Map<String, Object> phrase = asMap(item);
            String text = stringValue(phrase.get("text"));
            if (isBlank(text)) {
                continue;
            }
            mapper.insertDictionaryPhrase(Map.of(
                    "phraseUid", newUid("dph"),
                    "entryUid", entryUid,
                    "phraseText", text,
                    "definitionEn", stringOrEmpty(firstNonBlank(stringValue(phrase.get("definition_en")), stringValue(phrase.get("definitionEn")))),
                    "definitionZh", stringOrEmpty(firstNonBlank(stringValue(phrase.get("definition_zh")), stringValue(phrase.get("definitionZh")))),
                    "sortOrder", sortOrder++,
                    "metadataJson", toJson(phrase)
            ));
            imported++;
        }
        return imported;
    }

    private int persistDictionaryResources(DictionaryLibrary library, Map<String, Object> workerResult) {
        Object value = workerResult.get("resources");
        if (!(value instanceof List<?> resources)) {
            return 0;
        }
        int imported = 0;
        for (Object item : resources) {
            Map<String, Object> resource = asMap(item);
            String resourceKey = firstNonBlank(stringValue(resource.get("resource_key")), stringValue(resource.get("resourceKey")));
            if (isBlank(resourceKey)) {
                continue;
            }
            mapper.upsertDictionaryResource(Map.of(
                    "resourceUid", newUid("dr"),
                    "dictionaryUid", library.getDictionaryUid(),
                    "resourceKey", resourceKey,
                    "resourceType", firstNonBlank(stringValue(resource.get("resource_type")), stringValue(resource.get("resourceType")), "other"),
                    "fileName", firstNonBlank(stringValue(resource.get("file_name")), stringValue(resource.get("fileName")), resourceKey),
                    "mimeType", stringOrEmpty(firstNonBlank(stringValue(resource.get("mime_type")), stringValue(resource.get("mimeType")))),
                    "storagePath", firstNonBlank(stringValue(resource.get("storage_path")), stringValue(resource.get("storagePath")), resourceKey),
                    "sizeBytes", longValue(resource.get("size_bytes"))
            ));
            imported++;
        }
        return imported;
    }

    private Map<String, Object> importResultPayload(Map<String, Object> workerResult,
                                                    ImportCounters counters,
                                                    int importedResources,
                                                    List<Map<String, Object>> failures) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", workerResult.getOrDefault("summary", Map.of(
                "entry_count", counters.processedEntries(),
                "example_count", counters.importedExamples(),
                "phrase_count", counters.importedPhrases()
        )));
        result.put("samples", counters.samples());
        result.put("failures", failures.stream().limit(20).toList());
        result.put("warnings", workerResult.getOrDefault("warnings", List.of()));
        result.put("resourceCount", importedResources);
        return result;
    }

    private List<Map<String, Object>> warningsAsFailures(Object warningsValue) {
        if (!(warningsValue instanceof List<?> warnings)) {
            return List.of();
        }
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Object warning : warnings) {
            String message = stringValue(warning);
            if (!isBlank(message)) {
                failures.add(Map.of("message", message));
            }
        }
        return failures;
    }

    private String resolveImportStatus(Map<String, Object> workerResult,
                                       ImportCounters counters,
                                       List<Map<String, Object>> failures) {
        String status = stringValue(workerResult.get("status"));
        if (counters.importedEntries() == 0) {
            return "failed";
        }
        if ("failed".equals(status) || "completed_with_warnings".equals(status) || !failures.isEmpty()) {
            return "completed_with_warnings";
        }
        return "completed";
    }

    private String resolveImportErrorMessage(String status, List<Map<String, Object>> failures) {
        if (!"failed".equals(status)) {
            return null;
        }
        if (!failures.isEmpty()) {
            String message = stringValue(failures.get(0).get("message"));
            if (!isBlank(message)) {
                return message;
            }
        }
        return "未导入任何词条，请检查 Python 解析依赖、MDX 文件和字段映射。";
    }

    Map<String, Object> probeDictionarySource(DataCleaningSource source) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceUid", source.getSourceUid());
        result.put("sourceCode", source.getSourceCode());
        result.put("displayName", source.getDisplayName());
        result.put("licenseStatus", source.getLicenseStatus());
        result.put("probedAt", LocalDateTime.now().toString());

        if (!isBlank(source.getMdxPath())) {
            result.put("mdx", probeMdictHeader(Path.of(source.getMdxPath()), "mdx"));
        }
        if (!isBlank(source.getMddPath())) {
            result.put("mdd", probeMdictHeader(Path.of(source.getMddPath()), "mdd"));
        }
        if (!isBlank(source.getExamplesPath())) {
            result.put("examples", probeXlsx(Path.of(source.getExamplesPath())));
        }
        return result;
    }

    Map<String, Object> probeMdictHeader(Path path, String kind) throws IOException {
        requireReadableFile(path, "." + kind);
        byte[] headerBytes;
        int headerLength;
        try (InputStream input = Files.newInputStream(path)) {
            byte[] lengthBytes = input.readNBytes(4);
            if (lengthBytes.length < 4) {
                throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, kind + " header is too short");
            }
            headerLength = ByteBuffer.wrap(lengthBytes).getInt();
            if (headerLength <= 0 || headerLength > MAX_MDICT_HEADER_BYTES) {
                throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, kind + " header length is invalid");
            }
            headerBytes = input.readNBytes(headerLength);
            if (headerBytes.length < headerLength) {
                throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, kind + " file is incomplete");
            }
        }
        String header = decodeUtf16(headerBytes).trim();
        Map<String, String> attrs = parseAttributes(header);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        out.put("path", path.toString());
        out.put("fileName", path.getFileName().toString());
        out.put("fileSizeBytes", Files.size(path));
        out.put("headerLength", headerLength);
        out.put("root", parseRootName(header));
        out.put("title", firstNonBlank(attrs.get("Title"), attrs.get("Description")));
        out.put("format", attrs.get("Format"));
        out.put("encoding", attrs.get("Encoding"));
        out.put("encrypted", attrs.get("Encrypted"));
        out.put("creationDate", attrs.get("CreationDate"));
        out.put("entryCount", parseEntryCount(attrs.get("Description")));
        out.put("attributes", attrs);
        return out;
    }

    Map<String, Object> probeXlsx(Path path) throws IOException {
        requireReadableFile(path, ".xlsx");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "xlsx");
        out.put("path", path.toString());
        out.put("fileName", path.getFileName().toString());
        out.put("fileSizeBytes", Files.size(path));

        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            String workbook = readZipEntry(zip, "xl/workbook.xml");
            List<String> sheets = matchAll(workbook, XLSX_SHEET_NAME);
            out.put("sheets", sheets);

            String sheetXml = readZipEntry(zip, "xl/worksheets/sheet1.xml");
            Matcher dimension = XLSX_DIMENSION.matcher(sheetXml);
            if (dimension.find()) {
                out.put("columnCount", columnToNumber(dimension.group(1)));
                out.put("rowCount", Integer.parseInt(dimension.group(2)));
            }
            List<CellValue> headerCells = parseHeaderCells(sheetXml);
            if (!headerCells.isEmpty()) {
                Map<Integer, String> shared = readSharedStrings(zip, headerCells);
                List<String> headers = new ArrayList<>();
                for (CellValue cell : headerCells) {
                    headers.add(cell.resolve(shared));
                }
                out.put("headers", headers);
                out.putIfAbsent("columnCount", headers.size());
            }
        }
        return out;
    }

    private void validateSourceRequest(CreateDictionaryDataCleaningSourceRequest request) {
        if (request == null || isBlank(request.getSourceCode()) || isBlank(request.getDisplayName())) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "sourceCode and displayName are required");
        }
        if (isBlank(request.getMdxPath()) && isBlank(request.getMddPath()) && isBlank(request.getExamplesPath())) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "at least one dictionary file path is required");
        }
    }

    private void validateUploadRequest(CreateDictionaryDataCleaningSourceRequest request, List<MultipartFile> files) {
        if (request == null || isBlank(request.getSourceCode()) || isBlank(request.getDisplayName())) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "sourceCode and displayName are required");
        }
        boolean hasFile = files != null && files.stream().anyMatch(file -> file != null && !file.isEmpty());
        if (!hasFile) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "at least one dictionary upload file is required");
        }
    }

    private void requireUniqueSourceCode(String sourceCode) {
        String normalized = sourceCode == null ? "" : sourceCode.trim();
        if (!normalized.isEmpty() && mapper.selectSourceByCode(normalized) != null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "词典源编码已存在：" + normalized);
        }
    }

    private StoredDictionaryFiles storeDictionaryFiles(DataCleaningSource source, List<MultipartFile> files) throws IOException {
        Path uploadDir = safeUploadDir(source);
        Files.createDirectories(uploadDir);
        StoredDictionaryFiles.Builder builder = new StoredDictionaryFiles.Builder(uploadDir.toString());
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = safeFileName(file.getOriginalFilename());
            String extension = extensionOf(originalName);
            requireAllowedUploadExtension(extension, originalName);
            if (".zip".equals(extension)) {
                storeZipEntries(file, uploadDir, builder);
            } else {
                Path storedPath = uniqueTarget(uploadDir, originalName);
                try (InputStream input = file.getInputStream()) {
                    Files.copy(input, storedPath, StandardCopyOption.REPLACE_EXISTING);
                }
                builder.accept(storedPath);
            }
        }
        StoredDictionaryFiles stored = builder.build();
        if (isBlank(stored.mdxPath()) && isBlank(stored.mddPath()) && isBlank(stored.examplesPath())) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "uploaded files must include mdx, mdd or xlsx");
        }
        return stored;
    }

    private void storeZipEntries(MultipartFile file, Path uploadDir, StoredDictionaryFiles.Builder builder) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = safeFileName(entry.getName());
                String extension = extensionOf(name);
                if (!ALLOWED_UPLOAD_EXTENSIONS.contains(extension) || ".zip".equals(extension)) {
                    continue;
                }
                Path storedPath = uniqueTarget(uploadDir, name);
                if (!storedPath.normalize().startsWith(uploadDir)) {
                    throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "invalid zip entry path");
                }
                try (OutputStream output = Files.newOutputStream(storedPath)) {
                    zip.transferTo(output);
                }
                builder.accept(storedPath);
            }
        }
    }

    private Path safeUploadDir(DataCleaningSource source) {
        String folder = source.getSourceCode().replaceAll("[^\\p{L}\\p{N}._-]+", "_") + "-" + source.getSourceUid();
        Path dir = dictionaryUploadRoot.resolve(folder).normalize();
        if (!dir.startsWith(dictionaryUploadRoot)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "invalid dictionary upload path");
        }
        return dir;
    }

    private Path uniqueTarget(Path dir, String fileName) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "invalid upload file path");
        }
        if (!Files.exists(target)) {
            return target;
        }
        String extension = extensionOf(fileName);
        String base = extension.isEmpty() ? fileName : fileName.substring(0, fileName.length() - extension.length());
        for (int i = 2; i < 1000; i++) {
            Path candidate = dir.resolve(base + "-" + i + extension).normalize();
            if (!candidate.startsWith(dir)) {
                throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "invalid upload file path");
            }
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("too many duplicate file names: " + fileName);
    }

    private void requireAllowedUploadExtension(String extension, String fileName) {
        if (!ALLOWED_UPLOAD_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "unsupported dictionary upload file: " + fileName);
        }
    }

    private static String safeFileName(String rawName) {
        String name = firstNonBlank(rawName, "dictionary-file");
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]+", "_").trim();
        return isBlank(name) ? "dictionary-file" : name;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private int countProbeFiles(DataCleaningSource source) {
        int count = 0;
        if (!isBlank(source.getMdxPath())) count++;
        if (!isBlank(source.getMddPath())) count++;
        if (!isBlank(source.getExamplesPath())) count++;
        return Math.max(count, 1);
    }

    private void requireReadableFile(Path path, String expectedExtension) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "file is not readable: " + path);
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(expectedExtension)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "file extension must be " + expectedExtension + ": " + path);
        }
    }

    private static String decodeUtf16(byte[] bytes) {
        if (bytes.length >= 2 && ((bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF)
                || (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE))) {
            return new String(bytes, StandardCharsets.UTF_16);
        }
        int evenZeros = 0;
        int oddZeros = 0;
        for (int i = 0; i < Math.min(bytes.length, 256); i++) {
            if (bytes[i] == 0) {
                if ((i & 1) == 0) evenZeros++;
                else oddZeros++;
            }
        }
        return new String(bytes, evenZeros > oddZeros ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE);
    }

    private static Map<String, String> parseAttributes(String header) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher matcher = XML_ATTR.matcher(header);
        while (matcher.find()) {
            attrs.put(matcher.group(1), unescapeXml(matcher.group(2)));
        }
        return attrs;
    }

    private static String parseRootName(String header) {
        int start = header.indexOf('<');
        if (start < 0) return null;
        int end = header.indexOf(' ', start + 1);
        int close = header.indexOf('>', start + 1);
        if (end < 0 || (close >= 0 && close < end)) end = close;
        return end > start ? header.substring(start + 1, end).replace("/", "") : null;
    }

    private static Long parseEntryCount(String description) {
        if (description == null) return null;
        Matcher matcher = ENTRY_COUNT.matcher(description);
        if (!matcher.find()) return null;
        return Long.parseLong(matcher.group(1).replace(",", ""));
    }

    private static String readZipEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return "";
        }
        try (var input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> matchAll(String text, Pattern pattern) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        while (matcher.find()) {
            values.add(unescapeXml(matcher.group(1)));
        }
        return values;
    }

    private static List<CellValue> parseHeaderCells(String sheetXml) {
        List<CellValue> cells = new ArrayList<>();
        Matcher matcher = XLSX_ROW1_CELL.matcher(sheetXml == null ? "" : sheetXml);
        while (matcher.find()) {
            Map<String, String> attrs = parseAttributes("<c " + matcher.group(1) + ">");
            String cellRef = attrs.get("r");
            if (cellRef == null || !cellRef.endsWith("1")) {
                continue;
            }
            String refColumn = cellRef.replaceAll("\\d+$", "");
            cells.add(new CellValue(refColumn, attrs.get("t"), matcher.group(2)));
        }
        cells.sort((a, b) -> Integer.compare(columnToNumber(a.refColumn()), columnToNumber(b.refColumn())));
        return cells;
    }

    private static Map<Integer, String> readSharedStrings(ZipFile zip, List<CellValue> cells) throws IOException {
        int maxIndex = cells.stream()
                .filter(CellValue::sharedString)
                .mapToInt(cell -> safeInt(cell.rawValue(), -1))
                .max()
                .orElse(-1);
        if (maxIndex < 0) {
            return Map.of();
        }
        String xml = readZipEntry(zip, "xl/sharedStrings.xml");
        Map<Integer, String> shared = new LinkedHashMap<>();
        Matcher itemMatcher = XLSX_SHARED_ITEM.matcher(xml);
        int index = 0;
        while (itemMatcher.find() && index <= maxIndex) {
            Matcher textMatcher = XLSX_TEXT_NODE.matcher(itemMatcher.group(1));
            StringBuilder value = new StringBuilder();
            while (textMatcher.find()) {
                value.append(unescapeXml(textMatcher.group(1)));
            }
            shared.put(index, value.toString());
            index++;
        }
        return shared;
    }

    private static int columnToNumber(String letters) {
        int value = 0;
        for (char ch : letters.toCharArray()) {
            value = value * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return value;
    }

    private AdminDataCleaningSourceResponse toSourceResponse(DataCleaningSource source) {
        AdminDataCleaningSourceResponse response = new AdminDataCleaningSourceResponse();
        response.setId(source.getId());
        response.setSourceUid(source.getSourceUid());
        response.setSourceType(source.getSourceType());
        response.setSourceCode(source.getSourceCode());
        response.setDisplayName(source.getDisplayName());
        response.setLicenseStatus(source.getLicenseStatus());
        response.setMdxPath(source.getMdxPath());
        response.setMddPath(source.getMddPath());
        response.setExamplesPath(source.getExamplesPath());
        response.setCoverImagePath(source.getCoverImagePath());
        response.setMetadata(readJson(source.getMetadataJson()));
        response.setStatus(source.getStatus());
        response.setCreatedBy(source.getCreatedBy());
        response.setCreatedAt(source.getCreatedAt());
        response.setUpdatedAt(source.getUpdatedAt());
        return response;
    }

    private AdminDataCleaningSourceResponse toSourceListResponse(DataCleaningSource source) {
        AdminDataCleaningSourceResponse response = toSourceResponse(source);
        response.setMetadata(Map.of());
        return response;
    }

    private AdminDataCleaningJobResponse toJobResponse(DataCleaningJob job) {
        AdminDataCleaningJobResponse response = new AdminDataCleaningJobResponse();
        response.setId(job.getId());
        response.setJobUid(job.getJobUid());
        response.setSourceUid(job.getSourceUid());
        response.setJobType(job.getJobType());
        response.setStatus(job.getStatus());
        response.setProgressTotal(job.getProgressTotal());
        response.setProgressDone(job.getProgressDone());
        response.setResult(readJson(job.getResultJson()));
        response.setErrorMessage(job.getErrorMessage());
        response.setCreatedBy(job.getCreatedBy());
        response.setStartedAt(job.getStartedAt());
        response.setFinishedAt(job.getFinishedAt());
        response.setCreatedAt(job.getCreatedAt());
        response.setUpdatedAt(job.getUpdatedAt());
        return response;
    }

    private AdminDataCleaningJobResponse toJobListResponse(DataCleaningJob job) {
        AdminDataCleaningJobResponse response = toJobResponse(job);
        response.setResult(summarizeProbeResult(response.getResult()));
        return response;
    }

    private AdminDictionaryLibraryResponse toDictionaryLibraryResponse(DictionaryLibrary library) {
        AdminDictionaryLibraryResponse response = new AdminDictionaryLibraryResponse();
        response.setId(library.getId());
        response.setDictionaryUid(library.getDictionaryUid());
        response.setSourceUid(library.getSourceUid());
        response.setDictionaryCode(library.getDictionaryCode());
        response.setDisplayName(library.getDisplayName());
        response.setDescription(library.getDescription());
        response.setFormat(library.getFormat());
        response.setEngineVersion(library.getEngineVersion());
        response.setRequiredEngineVersion(library.getRequiredEngineVersion());
        response.setEncoding(library.getEncoding());
        response.setEntryCount(library.getEntryCount());
        response.setResourceCount(library.getResourceCount());
        response.setMdxFileName(library.getMdxFileName());
        response.setMddFileName(library.getMddFileName());
        response.setCoverImagePath(library.getCoverImagePath());
        response.setMdxSizeBytes(library.getMdxSizeBytes());
        response.setMddSizeBytes(library.getMddSizeBytes());
        response.setExamplesCount(library.getExamplesCount());
        response.setLicenseStatus(library.getLicenseStatus());
        response.setStorageType(library.getStorageType());
        response.setEnabled(Boolean.TRUE.equals(library.getEnabled()));
        response.setSortOrder(library.getSortOrder());
        response.setStatus(library.getStatus());
        response.setMetadata(readJson(library.getMetadataJson()));
        response.setCreatedBy(library.getCreatedBy());
        response.setCreatedAt(library.getCreatedAt());
        response.setUpdatedAt(library.getUpdatedAt());
        return response;
    }

    private AdminDictionaryImportJobResponse toDictionaryImportJobResponse(DictionaryImportJob job) {
        AdminDictionaryImportJobResponse response = new AdminDictionaryImportJobResponse();
        response.setId(job.getId());
        response.setImportJobUid(job.getImportJobUid());
        response.setDictionaryUid(job.getDictionaryUid());
        response.setSourceUid(job.getSourceUid());
        response.setStatus(job.getStatus());
        response.setImportLimit(job.getImportLimit());
        response.setProcessedEntries(job.getProcessedEntries());
        response.setImportedEntries(job.getImportedEntries());
        response.setFailedEntries(job.getFailedEntries());
        response.setImportedExamples(job.getImportedExamples());
        response.setImportedPhrases(job.getImportedPhrases());
        response.setErrorMessage(job.getErrorMessage());
        response.setResult(readJson(job.getResultJson()));
        response.setCreatedBy(job.getCreatedBy());
        response.setStartedAt(job.getStartedAt());
        response.setFinishedAt(job.getFinishedAt());
        response.setCreatedAt(job.getCreatedAt());
        response.setUpdatedAt(job.getUpdatedAt());
        return response;
    }

    private Map<String, Object> summarizeProbeResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        putMdictSummary(summary, "mdx", result.get("mdx"));
        putMdictSummary(summary, "mdd", result.get("mdd"));
        putExamplesSummary(summary, result.get("examples"));
        return summary;
    }

    private void putMdictSummary(Map<String, Object> target, String key, Object value) {
        Map<String, Object> source = asMap(value);
        if (source.isEmpty()) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        copyIfPresent(summary, source, "kind");
        copyIfPresent(summary, source, "fileName");
        copyIfPresent(summary, source, "fileSizeBytes");
        copyIfPresent(summary, source, "title");
        copyIfPresent(summary, source, "format");
        copyIfPresent(summary, source, "encoding");
        copyIfPresent(summary, source, "entryCount");
        target.put(key, summary);
    }

    private void putExamplesSummary(Map<String, Object> target, Object value) {
        Map<String, Object> source = asMap(value);
        if (source.isEmpty()) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        copyIfPresent(summary, source, "kind");
        copyIfPresent(summary, source, "fileName");
        copyIfPresent(summary, source, "fileSizeBytes");
        copyIfPresent(summary, source, "rowCount");
        copyIfPresent(summary, source, "columnCount");
        copyIfPresent(summary, source, "headers");
        target.put("examples", summary);
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private Map<String, Object> readJson(String json) {
        if (isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return Map.of("raw", json);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize data cleaning json", ex);
        }
    }

    private static String newUid(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static Long nullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static Long exampleDataCount(Map<String, Object> examples) {
        Long rowCount = nullableLong(examples.get("rowCount"));
        if (rowCount == null) {
            return null;
        }
        return Math.max(0L, rowCount - 1L);
    }

    private static int normalizeImportLimit(Integer importLimit) {
        if (importLimit == null || importLimit <= 0) {
            return 0;
        }
        return importLimit;
    }

    private static String normalizeHeadword(String value) {
        return firstNonBlank(value, "").toLowerCase(Locale.ROOT).replace("·", "").trim();
    }

    private static int qualityScore(Map<String, Object> entry) {
        int score = 40;
        if (!isBlank(firstNonBlank(stringValue(entry.get("part_of_speech")), stringValue(entry.get("partOfSpeech"))))) {
            score += 15;
        }
        Object senses = entry.get("senses");
        if (senses instanceof List<?> list && !list.isEmpty()) {
            score += 25;
        }
        Object phonetics = entry.get("phonetics");
        if (phonetics instanceof List<?> list && !list.isEmpty()) {
            score += 10;
        }
        Object phrases = entry.get("phrases");
        if (phrases instanceof List<?> list && !list.isEmpty()) {
            score += 10;
        }
        return Math.min(score, 100);
    }

    private static int safeInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String unescapeXml(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private record CellValue(String refColumn, String type, String rawValue) {
        boolean sharedString() {
            return "s".equals(type);
        }

        String resolve(Map<Integer, String> shared) {
            if (sharedString()) {
                return shared.getOrDefault(safeInt(rawValue, -1), rawValue);
            }
            return unescapeXml(rawValue);
        }
    }

    private static final class ImportAccumulator {
        private int processedEntries;
        private int importedEntries;
        private int importedExamples;
        private int importedPhrases;
        private int sortOrder;
        private final List<Map<String, Object>> samples = new ArrayList<>();

        private ImportCounters toCounters() {
            return new ImportCounters(processedEntries, importedEntries, importedExamples, importedPhrases, samples);
        }
    }

    private record ImportCounters(int processedEntries,
                                  int importedEntries,
                                  int importedExamples,
                                  int importedPhrases,
                                  List<Map<String, Object>> samples) {
    }

    private record SenseCounters(int senseCount,
                                 int exampleCount,
                                 String firstDefinitionEn,
                                 String firstDefinitionZh) {
    }

    private record StoredDictionaryFiles(String uploadDir,
                                         String mdxPath,
                                         String mddPath,
                                         String examplesPath,
                                         String coverImagePath,
                                         List<String> uploadedFiles) {
        private static final class Builder {
            private final String uploadDir;
            private final List<String> uploadedFiles = new ArrayList<>();
            private String mdxPath;
            private String mddPath;
            private String examplesPath;
            private String coverImagePath;

            private Builder(String uploadDir) {
                this.uploadDir = uploadDir;
            }

            private void accept(Path path) {
                String storedPath = path.toString();
                uploadedFiles.add(storedPath);
                String extension = extensionOf(path.getFileName().toString());
                if (".mdx".equals(extension) && mdxPath == null) {
                    mdxPath = storedPath;
                } else if (".mdd".equals(extension) && mddPath == null) {
                    mddPath = storedPath;
                } else if (".xlsx".equals(extension) && examplesPath == null) {
                    examplesPath = storedPath;
                } else if ((".jpg".equals(extension) || ".jpeg".equals(extension) || ".png".equals(extension)) && coverImagePath == null) {
                    coverImagePath = storedPath;
                }
            }

            private StoredDictionaryFiles build() {
                return new StoredDictionaryFiles(uploadDir, mdxPath, mddPath, examplesPath, coverImagePath, List.copyOf(uploadedFiles));
            }
        }
    }
}
