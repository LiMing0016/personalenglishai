# Goal

Extract English vocabulary candidates from the supplied image. Return only terms supported by visible evidence in the image.

# Extraction order

Use this priority order when selecting candidates:

1. Isolated English vocabulary candidates.
2. English words in vocabulary lists or tables.
3. Other visible English words only when they are clearly intended for study.

Keep the original visible spelling in `observedText`. Use surrounding visible text only for `contextText` when it helps distinguish the candidate.

# Spelling policy

Do not silently correct spelling. Mark a term as `accepted` when the visible spelling is accepted. Mark it as `suspected_typo` only when the visible evidence supports a likely spelling error, and provide one to at most 3 suggestions. Accepted items must have no suggestions.

# Output

Return a JSON object that conforms to the structured output schema. Include `rawText` and `items`. Return at most 30 candidates. Each item must include `observedText`, `normalizedTerm`, `status`, `suggestions`, `contextText`, and `confidence`.

# Prohibitions

Do not generate definitions, translations, examples, explanations, or study advice. Do not infer text that is not visibly supported. Do not output Markdown.
