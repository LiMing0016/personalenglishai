from __future__ import annotations

import json
import os
from pathlib import Path

from ..agents.learning_asset_copilot import create_learning_asset_copilot_agent
from ..schemas.learning_assets import LearningAssetOrganizeRequest
from ..schemas.learning_assets import LearningAssetOrganizeResponse
from ..services.agent_session_runner import run_agent_session


class LearningAssetCopilotConfigError(RuntimeError):
    pass


_ACTION_INSTRUCTIONS = {
    "complete": "补全当前 Markdown 中空白的学习字段；不要重写用户已有内容。",
    "organize": "按对应学习资产模板整理当前材料。",
    "format": "优化当前 Markdown 的格式，尽量保留用户原意，只调整标题、加粗、引用、列表、表格和段落结构。",
    "examples": "基于当前笔记补充可复习的英文例句、中文解释和使用提醒。",
    "expand": "扩展当前学习笔记，补充必要的释义、结构、例句、搭配或使用提醒。",
    "polish": "润色当前笔记中的语句，让英文更自然、中文说明更清晰，同时保留学习重点。",
    "custom": "按用户自定义要求处理当前学习笔记。",
}

_TEMPLATES = {
    "vocabulary": """默认单词卡模板：
# {{title}}
**词性：**
**中文释义：**
**English meaning：**
**原句：**
**AI 例句：**
**常见搭配：**
## 我的笔记""",
    "grammar": """默认语法笔记模板：
# {{title}}
**类型：** 语法笔记
**结构/规则：**
**中文说明：**
**原句：**
## 结构拆解
-
## 使用提醒
-
## 我的笔记""",
    "sentence": """默认句子笔记模板：
# {{title}}
**中文含义：**
**核心结构：**
**可替换表达：**
**适用场景：**
## 句子拆解
-
## 我的笔记""",
    "expression": """默认表达笔记模板：
# {{title}}
**中文含义：**
**自然用法：**
**语气/场景：**
**例句：**
**常见搭配：**
## 我的笔记""",
}


class LearningAssetCopilotService:
    def __init__(self, *, model: str, session_db_path: str) -> None:
        self.model = model
        self.session_db_path = session_db_path
        self._agent = None

    @classmethod
    def from_env(cls) -> "LearningAssetCopilotService":
        model = (
            os.getenv("AI_ASSISTANT_MODEL", "").strip()
            or os.getenv("AI_PROVIDER_OPENAI_MODEL", "").strip()
            or "gpt-5.4-mini"
        )
        session_db_path = os.getenv(
            "AI_ASSISTANT_SESSION_DB_PATH",
            str(Path(__file__).resolve().parent.parent / "data" / "assistant_sessions.db"),
        )
        return cls(model=model, session_db_path=session_db_path)

    def is_configured(self) -> bool:
        return bool(os.getenv("OPENAI_API_KEY", "").strip())

    def _require_configured(self) -> None:
        if not self.is_configured():
            raise LearningAssetCopilotConfigError("OPENAI_API_KEY 未配置，学习资产 Copilot 暂时不可用。")

    def _get_agent(self):
        self._require_configured()
        if self._agent is None:
            self._agent = create_learning_asset_copilot_agent(self.model)
        return self._agent

    async def organize(self, request: LearningAssetOrganizeRequest) -> LearningAssetOrganizeResponse:
        result = await run_agent_session(
            agent=self._get_agent(),
            agent_input=self._build_agent_input(request),
            conversation_id="learning-asset-copilot",
            session_db_path=self.session_db_path,
            use_session=False,
            trace_workflow_name="learning_asset_copilot",
            trace_metadata={
                "asset_type": request.type,
                "action": self._normalize_action(request),
            },
        )
        return LearningAssetOrganizeResponse(candidateMarkdown=result.final_output.strip())

    def _build_agent_input(self, request: LearningAssetOrganizeRequest) -> str:
        action = self._normalize_action(request)
        template = _TEMPLATES.get(request.type, _TEMPLATES["vocabulary"])
        instruction = (request.instruction or "").strip()
        payload = {
            "assetType": request.type,
            "action": action,
            "taskInstruction": _ACTION_INSTRUCTIONS[action],
            "title": request.title.strip(),
            "selectedText": (request.selected_text or "").strip(),
            "contextText": (request.context_text or "").strip(),
            "customInstruction": instruction,
            "currentMarkdown": (request.current_markdown or "").strip(),
        }
        custom_instruction_line = f"自定义要求：{instruction}" if instruction else "自定义要求：无"
        return "\n".join(
            [
                "[学习资产 Copilot 请求]",
                f"资产类型：{request.type}",
                f"动作：{action}",
                f"任务说明：{_ACTION_INSTRUCTIONS[action]}",
                f"标题：{payload['title']}",
                f"选中文本：{payload['selectedText']}",
                f"上下文：{payload['contextText']}",
                custom_instruction_line,
                "",
                template,
                "",
                "当前 Markdown：",
                payload["currentMarkdown"],
                "",
                "结构化输入：",
                json.dumps(payload, ensure_ascii=False, indent=2),
            ]
        )

    def _normalize_action(self, request: LearningAssetOrganizeRequest) -> str:
        if request.action in _ACTION_INSTRUCTIONS:
            return request.action
        return "format" if request.mode == "format" else "organize"
