---
name: grammar-check
description: Check English grammar issues and provide concise corrections, explanations, and transferable writing tips. Use when Codex or an English-learning agent needs grammar correction, sentence correctness checks, error explanation, or a grammar-focused pass before polishing or scoring.
---

# Grammar Check

Use this skill to make a focused grammar pass for English learning tasks.

## Core Workflow

1. Identify only real grammar, spelling, word form, collocation, punctuation, or sentence-boundary issues.
2. Classify each issue with a stable error type.
3. Suggest the smallest correction that fixes the issue.
4. Explain the reason briefly in learner-friendly language.
5. Add a transferable tip when the issue is likely to recur in writing.

## Output Guidance

Return a compact structured result for the calling agent to use:

- `has_grammar_issue`: whether the input has clear grammar issues.
- `issues`: focused list of detected issues.
- `corrected_text`: minimally corrected text when useful.
- `overall_note`: short summary when multiple issues share a pattern.

For each issue, include:

- `span`: the problematic text.
- `issue_type`: stable grammar category.
- `severity`: `low`, `medium`, or `high`.
- `correction`: suggested fix.
- `explanation`: why the original is wrong or weak.
- `transfer_tip`: how the learner can avoid the same issue later.

## Boundaries

- Do not produce the final user-facing polish.
- Do not score the writing.
- Do not rewrite into multiple style versions.
- Do not turn the answer into a long grammar lesson.
- Do not flag subjective style preferences as grammar errors.
- Keep corrections minimal unless the caller explicitly asks for broader rewriting.

## What Does Not Count as a Grammar Error

- 词汇是否高级、表达是否地道、语气是否正式，默认不算语法错误。
- 如果句子语法正确但表达普通，只能标注为可优化，不能标为语法错误。
- 不因为主观风格偏好而建议改写。
- 不因为个人风格偏好而建议改写。
- 不把“可以更自然”“可以更正式”“可以更适合考试写作”混同为语法错误。
- 只有当表达违反语法规则、搭配习惯、词形要求、句子边界或标点规则时，才标注为 grammar issue。

## Reference Loading

Use `references/grammar-taxonomy.md` only when you need stable issue labels or severity guidance.
