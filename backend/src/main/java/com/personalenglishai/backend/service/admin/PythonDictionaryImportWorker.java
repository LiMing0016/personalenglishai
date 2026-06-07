package com.personalenglishai.backend.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.admin.DataCleaningSource;
import com.personalenglishai.backend.entity.admin.DictionaryLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PythonDictionaryImportWorker implements DictionaryImportWorker {
    private final ObjectMapper objectMapper;
    private final String pythonExecutable;
    private final Path projectRoot;
    private final Path workerTempRoot;

    public PythonDictionaryImportWorker(ObjectMapper objectMapper,
                                        @Value("${app.data-cleaning.python.executable:python}") String pythonExecutable,
                                        @Value("${app.data-cleaning.python.project-root:..}") String projectRoot,
                                        @Value("${app.data-cleaning.python.temp-dir:storage/data-cleaning/python-worker}") String workerTempRoot) {
        this.objectMapper = objectMapper;
        this.projectRoot = Path.of(projectRoot).toAbsolutePath().normalize();
        this.pythonExecutable = resolvePythonExecutable(pythonExecutable, this.projectRoot);
        this.workerTempRoot = Path.of(workerTempRoot).toAbsolutePath().normalize();
    }

    @Override
    public Map<String, Object> importDictionary(DictionaryLibrary library, DataCleaningSource source, int importLimit) {
        try {
            Files.createDirectories(workerTempRoot);
            Path requestPath = Files.createTempFile(workerTempRoot, "dictionary-import-", ".request.json");
            Path outputPath = workerTempRoot.resolve(requestPath.getFileName().toString().replace(".request.json", ".result.json"));
            objectMapper.writeValue(requestPath.toFile(), requestPayload(library, source, importLimit));

            ProcessBuilder builder = new ProcessBuilder(
                    pythonExecutable,
                    "-m",
                    "python.ai_orchestrator.workflows.dictionary_cleaning.cli",
                    "--input",
                    requestPath.toString(),
                    "--output",
                    outputPath.toString()
            );
            builder.directory(projectRoot.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "Python 词典入库 worker 失败：" + output.trim());
            }
            return objectMapper.readValue(outputPath.toFile(), new TypeReference<>() {});
        } catch (BizException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "Python 词典入库 worker 文件处理失败：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "Python 词典入库 worker 被中断");
        }
    }

    private Map<String, Object> requestPayload(DictionaryLibrary library, DataCleaningSource source, int importLimit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dictionaryUid", library.getDictionaryUid());
        payload.put("sourceUid", source.getSourceUid());
        payload.put("sourceCode", source.getSourceCode());
        payload.put("displayName", library.getDisplayName());
        payload.put("limit", importLimit);
        payload.put("entryBatchSize", 500);
        payload.put("mdxPath", source.getMdxPath());
        payload.put("mddPath", source.getMddPath());
        payload.put("examplesPath", source.getExamplesPath());
        payload.put("metadata", List.of("generatedBy", "PythonDictionaryImportWorker"));
        return payload;
    }

    private static String resolvePythonExecutable(String configuredExecutable, Path projectRoot) {
        String configured = configuredExecutable == null ? "" : configuredExecutable.trim();
        if (!configured.isBlank() && !"python".equalsIgnoreCase(configured)) {
            Path configuredPath = Path.of(configured);
            if (configuredPath.isAbsolute()) {
                return configuredPath.toString();
            }
            Path projectRelativePath = projectRoot.resolve(configuredPath).normalize();
            return Files.exists(projectRelativePath) ? projectRelativePath.toString() : configured;
        }

        for (String candidate : List.of("python/.venv/Scripts/python.exe", "python/.venv/bin/python")) {
            Path candidatePath = projectRoot.resolve(candidate).normalize();
            if (Files.exists(candidatePath)) {
                return candidatePath.toString();
            }
        }
        return configured.isBlank() ? "python" : configured;
    }
}
