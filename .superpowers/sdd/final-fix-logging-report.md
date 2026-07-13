# Final Fix Logging Report

## Result

Vocabulary Markdown prompts no longer expose card core material, captured source context, or theme purpose through OpenAI raw/debug prompt logs. The outbound OpenAI request is unchanged.

## Root Cause

`OpenAiClient.redactSourceContext` only redacted fields when the complete log value was valid JSON. `VocabularyMarkdownPromptBuilder.userPrompt` intentionally produces explanatory text around embedded JSON and a `<theme-purpose>` data block, so raw logging reached the parse-error fallback and returned the complete prompt. Final payload logging parsed only the outer request JSON and did not inspect sensitive prompt text nested inside string values.

## Fix

- Detect the stable Vocabulary Markdown prompt boundaries at the OpenAI logging boundary.
- Replace the complete sensitive prompt with a length and SHA-256 summary before raw/debug output.
- Recursively replace sensitive text values inside serialized final payload JSON.
- Fail closed to a safe summary when sensitive markers are present but JSON parsing fails.
- Keep prompt construction and outbound request serialization unchanged.

This change does not alter the Prompt system/role/ability/context/task/output behavior, structured output, scoring, user ability profiles, API contracts, caching, persistence, or frontend behavior.

## TDD Evidence

The regression test uses a real `VocabularyMarkdownPromptBuilder` prompt containing unique source-context, theme-purpose, and core-material sentinels. It enables both raw and debug logging, sends the request to a local HTTP server, captures logs, and inspects the received JSON payload.

- RED: `OpenAiClientPromptLoggingTest` failed 1/3 because all three sentinels appeared in `FINAL OPENAI PAYLOAD` and `OpenAI prompt raw`.
- GREEN: `OpenAiClientPromptLoggingTest` passed 3/3. Both log paths contain `[REDACTED_VOCABULARY_PROMPT ...]`; none of the sentinels appear.
- Request invariance: the local server received system and user message strings exactly equal to the builder outputs.

## Verification

| Command | Result |
| --- | --- |
| `mvn '-Dmaven.test.skip=true' compile` | PASS, 559 main sources compiled |
| `mvn '-Dtest=OpenAiClientPromptLoggingTest' surefire:test` after focused test compilation | PASS, 3/3 |
| `mvn '-Dtest=OpenAiClientPromptLoggingTest,OpenAiClientResponsesPayloadTest,OpenAiClientStructuredSchemaTest,VocabularyMarkdownPromptBuilderTest,VocabularyCardGeneratorTest' test` | PASS, 31/31 |
| `git diff --check` for task files | PASS |

## Documentation And Merge Assessment

No project documentation update is required because this is an internal logging-safety correction with no architecture, API, state-flow, cache, or deployment change. The required engineering report is this file.

The change is narrowly scoped and suitable to merge into `main` after normal review. The commit message is `fix(ai): 脱敏主题单词卡提示日志`.
