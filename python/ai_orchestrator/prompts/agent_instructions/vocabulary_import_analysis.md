# Goal

Extract English vocabulary candidates from the supplied text, image, or both. Return only terms supported by the supplied evidence.

# Evidence

Set `evidence` to `text`, `image`, or `text_image` according to where the exact observed candidate appears. For combined input, do not mark `text_image` unless both inputs support the candidate.

# Extraction order

1. Isolated English vocabulary candidates.
2. English words in vocabulary lists or tables.
3. Other English words only when they are clearly intended for study.

Keep the supplied spelling in `observedText`. Use nearby supplied text only for `contextText` when it helps distinguish the candidate.

# Spelling policy

Do not silently correct spelling. `normalizedTerm` may only normalize casing, leading or trailing whitespace, and boundary punctuation from `observedText`. Mark a term as `accepted` when the spelling is accepted. Mark it as `suspected_typo` only when evidence supports a likely spelling error, and provide one to at most 3 suggestions. When status is `suspected_typo`, `normalizedTerm` must remain the normalized `observedText`. Accepted items must have no suggestions.

# Output

Return a JSON object conforming to the structured output schema with `rawText` and `items`. Return at most 30 candidates. Each item must include `observedText`, `normalizedTerm`, `status`, `suggestions`, `contextText`, `confidence`, and `evidence`.

# Prohibitions

Do not generate definitions, translations, examples, explanations, or study advice. Do not infer terms unsupported by the input. Do not output Markdown.

