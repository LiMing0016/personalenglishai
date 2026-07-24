# Raw Agent MCP Fetch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `single_agent_raw` 中增加可开关的官方 MCP Fetch 网页正文读取能力，同时保留 Web Search、原有会话和故障回退。

**Architecture:** 新增一个只负责 Fetch MCP 配置、进程连接和清理的基础设施适配器；`RawSingleAgentService` 在每次普通或流式运行期间取得可用 MCP server，并把它注入现有 `Raw Single Agent`。MCP 连接失败时适配器返回空 server 列表，服务继续使用原始 Agent，不改多 Agent 运行链路。

**Tech Stack:** Python 3.11+、OpenAI Agents SDK `0.17.6`、`MCPServerStdio`、官方 `mcp-server-fetch==2026.7.10`、`unittest`、`uv`。

## Global Constraints

- 只修改 `single_agent_raw`，不新增 Agent，不修改多 Agent 路由。
- 原始 Agent 不增加 application instructions、handoff 或结构化输出。
- 保留现有 `WebSearchTool`。
- Fetch MCP 默认关闭，只能通过 `AI_ASSISTANT_RAW_FETCH_MCP_ENABLED=true` 显式开启。
- 使用 `uvx --from mcp-server-fetch==2026.7.10 mcp-server-fetch`。
- Windows 子进程设置 `PYTHONIOENCODING=utf-8`。
- 保留 robots.txt 默认行为，不传 `--ignore-robots-txt`。
- MCP 连接失败必须回退普通原始 Agent，不能阻断普通对话。
- 直接接入仅用于本地实验；正式公网发布前必须增加参数级 SSRF 防护。
- 不覆盖、回退或顺带提交工作区中不属于本任务的既有修改。

---

### Task 1: Fetch MCP 配置与连接适配器

**Files:**
- Create: `python/ai_orchestrator/services/raw_fetch_mcp.py`
- Create: `python/ai_orchestrator/tests/test_raw_fetch_mcp.py`

**Interfaces:**
- Produces: `RawFetchMcpConfig(enabled: bool, package_version: str)`
- Produces: `create_raw_fetch_mcp_server(config: RawFetchMcpConfig)`
- Produces: `connected_raw_fetch_mcp_servers(config: RawFetchMcpConfig) -> AsyncIterator[tuple[object, ...]]`
- Consumes: OpenAI Agents SDK `MCPServerStdio.connect()` 与 `MCPServerStdio.cleanup()`

- [ ] **Step 1: Write failing configuration and construction tests**

```python
import os
import sys
import types
import unittest
from unittest.mock import patch

from python.ai_orchestrator.services.raw_fetch_mcp import (
    RawFetchMcpConfig,
    create_raw_fetch_mcp_server,
)


class FakeMCPServerStdio:
    def __init__(self, **kwargs):
        self.kwargs = kwargs


class RawFetchMcpConfigTest(unittest.TestCase):
    def test_from_env_is_disabled_by_default(self):
        with patch.dict(os.environ, {}, clear=True):
            config = RawFetchMcpConfig.from_env()
        self.assertFalse(config.enabled)

    def test_from_env_enables_explicit_true(self):
        with patch.dict(
            os.environ,
            {"AI_ASSISTANT_RAW_FETCH_MCP_ENABLED": "true"},
            clear=True,
        ):
            config = RawFetchMcpConfig.from_env()
        self.assertTrue(config.enabled)

    def test_server_uses_pinned_uvx_command_and_windows_encoding(self):
        fake_mcp = types.SimpleNamespace(MCPServerStdio=FakeMCPServerStdio)
        with patch.dict(sys.modules, {"agents.mcp": fake_mcp}):
            server = create_raw_fetch_mcp_server(RawFetchMcpConfig(enabled=True))

        self.assertEqual(server.kwargs["name"], "Fetch MCP")
        self.assertEqual(
            server.kwargs["params"]["args"],
            [
                "--from",
                "mcp-server-fetch==2026.7.10",
                "mcp-server-fetch",
            ],
        )
        self.assertEqual(
            server.kwargs["params"]["env"]["PYTHONIOENCODING"],
            "utf-8",
        )
```

- [ ] **Step 2: Run the new tests and verify RED**

Run:

```powershell
uv run --no-project --with-requirements python/ai_orchestrator/requirements.txt `
  python -m unittest python.ai_orchestrator.tests.test_raw_fetch_mcp -v
```

Expected: FAIL because `raw_fetch_mcp` does not exist.

- [ ] **Step 3: Implement the immutable configuration and server factory**

```python
from __future__ import annotations

import os
from dataclasses import dataclass


_TRUE_VALUES = {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class RawFetchMcpConfig:
    enabled: bool
    package_version: str = "2026.7.10"

    @classmethod
    def from_env(cls) -> "RawFetchMcpConfig":
        enabled = (
            os.getenv("AI_ASSISTANT_RAW_FETCH_MCP_ENABLED", "")
            .strip()
            .lower()
            in _TRUE_VALUES
        )
        return cls(enabled=enabled)


def create_raw_fetch_mcp_server(config: RawFetchMcpConfig):
    from agents.mcp import MCPServerStdio

    return MCPServerStdio(
        name="Fetch MCP",
        params={
            "command": "uvx",
            "args": [
                "--from",
                f"mcp-server-fetch=={config.package_version}",
                "mcp-server-fetch",
            ],
            "env": {"PYTHONIOENCODING": "utf-8"},
        },
        cache_tools_list=True,
    )
```

- [ ] **Step 4: Add failing lifecycle tests**

```python
from unittest.mock import AsyncMock

from python.ai_orchestrator.services.raw_fetch_mcp import (
    connected_raw_fetch_mcp_servers,
)


class RawFetchMcpLifecycleTest(unittest.IsolatedAsyncioTestCase):
    async def test_disabled_config_does_not_create_server(self):
        with patch(
            "python.ai_orchestrator.services.raw_fetch_mcp."
            "create_raw_fetch_mcp_server"
        ) as factory:
            async with connected_raw_fetch_mcp_servers(
                RawFetchMcpConfig(enabled=False)
            ) as servers:
                self.assertEqual(servers, ())
        factory.assert_not_called()

    async def test_connected_server_is_yielded_and_cleaned_up(self):
        server = types.SimpleNamespace(
            connect=AsyncMock(),
            cleanup=AsyncMock(),
        )
        with patch(
            "python.ai_orchestrator.services.raw_fetch_mcp."
            "create_raw_fetch_mcp_server",
            return_value=server,
        ):
            async with connected_raw_fetch_mcp_servers(
                RawFetchMcpConfig(enabled=True)
            ) as servers:
                self.assertEqual(servers, (server,))
        server.connect.assert_awaited_once()
        server.cleanup.assert_awaited_once()

    async def test_connection_failure_yields_empty_servers(self):
        server = types.SimpleNamespace(
            connect=AsyncMock(side_effect=RuntimeError("cannot start")),
            cleanup=AsyncMock(),
        )
        with patch(
            "python.ai_orchestrator.services.raw_fetch_mcp."
            "create_raw_fetch_mcp_server",
            return_value=server,
        ):
            async with connected_raw_fetch_mcp_servers(
                RawFetchMcpConfig(enabled=True)
            ) as servers:
                self.assertEqual(servers, ())
        server.cleanup.assert_not_awaited()
```

- [ ] **Step 5: Run lifecycle tests and verify RED**

Run the Task 1 unittest command again.

Expected: FAIL because `connected_raw_fetch_mcp_servers` does not exist.

- [ ] **Step 6: Implement connection fallback and cleanup**

```python
import logging
from contextlib import asynccontextmanager


log = logging.getLogger("uvicorn.error")


@asynccontextmanager
async def connected_raw_fetch_mcp_servers(config: RawFetchMcpConfig):
    if not config.enabled:
        yield ()
        return

    server = create_raw_fetch_mcp_server(config)
    try:
        await server.connect()
    except Exception:
        log.warning("Fetch MCP connection failed; continuing without it", exc_info=True)
        yield ()
        return

    try:
        yield (server,)
    finally:
        try:
            await server.cleanup()
        except Exception:
            log.warning("Fetch MCP cleanup failed", exc_info=True)
```

- [ ] **Step 7: Run Task 1 tests and verify GREEN**

Run the Task 1 unittest command.

Expected: all `test_raw_fetch_mcp` tests PASS.

- [ ] **Step 8: Commit the isolated new adapter**

```powershell
git add python/ai_orchestrator/services/raw_fetch_mcp.py `
  python/ai_orchestrator/tests/test_raw_fetch_mcp.py
git commit -m "feat(agent): 增加原始模型网页抓取适配器"
```

Before committing, run `git diff --cached --name-only` and confirm only these two files are staged.

---

### Task 2: 向同一个 Raw Agent 注入 MCP server

**Files:**
- Modify: `python/ai_orchestrator/agents/raw_single.py:4`
- Modify: `python/ai_orchestrator/tests/test_raw_single_agent.py:24-39`

**Interfaces:**
- Consumes: `mcp_servers: tuple[object, ...]`
- Produces: `create_raw_single_agent(model: str, *, mcp_servers=())`

- [ ] **Step 1: Extend the existing agent test before production code**

Add a second test:

```python
def test_agent_accepts_fetch_mcp_without_adding_instructions(self) -> None:
    fetch_server = object()
    fake_agents = types.SimpleNamespace(
        Agent=FakeAgent,
        WebSearchTool=FakeWebSearchTool,
    )
    with patch.dict(sys.modules, {"agents": fake_agents}):
        agent = create_raw_single_agent(
            "test-model",
            mcp_servers=(fetch_server,),
        )

    self.assertEqual(agent.mcp_servers, [fetch_server])
    self.assertIsNone(agent.instructions)
    self.assertEqual(len(agent.tools), 1)
    self.assertIsInstance(agent.tools[0], FakeWebSearchTool)
```

Update `FakeAgent.__init__`:

```python
self.mcp_servers = kwargs.get("mcp_servers", [])
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
uv run --no-project --with-requirements python/ai_orchestrator/requirements.txt `
  python -m unittest python.ai_orchestrator.tests.test_raw_single_agent -v
```

Expected: FAIL because `create_raw_single_agent` does not accept `mcp_servers`.

- [ ] **Step 3: Implement MCP injection without instructions**

```python
def create_raw_single_agent(model: str, *, mcp_servers=()):
    from agents import Agent, WebSearchTool

    return Agent(
        name="Raw Single Agent",
        model=model,
        tools=[WebSearchTool()],
        mcp_servers=list(mcp_servers),
    )
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Task 2 unittest command.

Expected: both raw agent tests PASS.

- [ ] **Step 5: Keep the change uncommitted if staging would include earlier unrelated hunks**

Inspect:

```powershell
git diff -- python/ai_orchestrator/agents/raw_single.py `
  python/ai_orchestrator/tests/test_raw_single_agent.py
```

Only commit when the staged diff contains this task and already approved raw-agent work:

```powershell
git add python/ai_orchestrator/agents/raw_single.py `
  python/ai_orchestrator/tests/test_raw_single_agent.py
git diff --cached
git commit -m "feat(agent): 为原始模型挂载 MCP 工具"
```

Otherwise leave the files uncommitted and report the mixed-worktree reason.

---

### Task 3: 普通与流式请求统一使用 Fetch MCP，并在失败时回退

**Files:**
- Modify: `python/ai_orchestrator/raw_assistant_service.py:29-220`
- Modify: `python/ai_orchestrator/tests/test_raw_assistant_service.py:13-122`

**Interfaces:**
- Consumes: `RawFetchMcpConfig.from_env()`
- Consumes: `connected_raw_fetch_mcp_servers(config)`
- Produces: `_get_agent(mcp_servers=())`
- Preserves: existing `run_agent_session` and `stream_agent_session` contracts

- [ ] **Step 1: Add failing constructor and environment tests**

```python
import os
from contextlib import asynccontextmanager

from python.ai_orchestrator.services.raw_fetch_mcp import RawFetchMcpConfig


def test_from_env_reads_fetch_mcp_switch(self) -> None:
    with patch.dict(
        os.environ,
        {
            "OPENAI_API_KEY": "test-key",
            "AI_ASSISTANT_RAW_FETCH_MCP_ENABLED": "true",
        },
        clear=True,
    ):
        service = RawSingleAgentService.from_env()
    self.assertTrue(service.fetch_mcp_config.enabled)
```

- [ ] **Step 2: Run the service tests and verify RED**

```powershell
uv run --no-project --with-requirements python/ai_orchestrator/requirements.txt `
  python -m unittest python.ai_orchestrator.tests.test_raw_assistant_service -v
```

Expected: FAIL because `fetch_mcp_config` does not exist.

- [ ] **Step 3: Add minimal service configuration**

Change the constructor and `from_env`:

```python
def __init__(
    self,
    *,
    model: str,
    session_db_path: str,
    fetch_mcp_config: RawFetchMcpConfig | None = None,
) -> None:
    self.model = model
    self.session_db_path = session_db_path
    self.fetch_mcp_config = fetch_mcp_config or RawFetchMcpConfig(enabled=False)
    self._agent = None


@classmethod
def from_env(cls) -> "RawSingleAgentService":
    # Preserve the existing model and session_db_path resolution.
    return cls(
        model=model,
        session_db_path=session_db_path,
        fetch_mcp_config=RawFetchMcpConfig.from_env(),
    )
```

- [ ] **Step 4: Add failing non-streaming MCP injection test**

```python
async def test_run_injects_connected_fetch_server_into_same_agent(self) -> None:
    fetch_server = object()
    fetch_config = RawFetchMcpConfig(enabled=True)
    service = RawSingleAgentService(
        model="test-model",
        session_db_path="unused.db",
        fetch_mcp_config=fetch_config,
    )
    built_agent = object()

    @asynccontextmanager
    async def fake_connection(_config):
        yield (fetch_server,)

    with (
        patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}, clear=False),
        patch(
            "python.ai_orchestrator.raw_assistant_service."
            "connected_raw_fetch_mcp_servers",
            fake_connection,
        ),
        patch(
            "python.ai_orchestrator.raw_assistant_service.create_raw_single_agent",
            return_value=built_agent,
        ) as factory,
        patch(
            "python.ai_orchestrator.raw_assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(
                final_output="页面摘要",
                agent_name="Raw Single Agent",
            ),
        ) as runner,
    ):
        await service.run_assistant_request(
            self._request("总结 https://example.com")
        )

    factory.assert_called_once_with(
        "test-model",
        mcp_servers=(fetch_server,),
    )
    self.assertIs(runner.await_args.kwargs["agent"], built_agent)
```

- [ ] **Step 5: Run the service tests and verify RED**

Run the Task 3 unittest command.

Expected: FAIL because the service does not open the MCP context or inject servers.

- [ ] **Step 6: Implement MCP-aware agent selection and wrap non-streaming run**

```python
def _get_agent(self, *, mcp_servers=()):
    if not self.is_configured() and self._agent is None:
        raise RawAssistantConfigError("OPENAI_API_KEY 未配置，原始模型暂时不可用。")
    if mcp_servers:
        return create_raw_single_agent(self.model, mcp_servers=mcp_servers)
    if self._agent is not None:
        return self._agent
    self._agent = create_raw_single_agent(self.model)
    return self._agent
```

Around `run_agent_session`:

```python
async with connected_raw_fetch_mcp_servers(
    self.fetch_mcp_config
) as mcp_servers:
    result = await run_agent_session(
        agent=self._get_agent(mcp_servers=mcp_servers),
        # Preserve all current arguments unchanged.
    )
```

- [ ] **Step 7: Add failing streaming MCP injection and cleanup test**

```python
async def test_stream_keeps_fetch_connection_open_until_completion(self) -> None:
    fetch_server = object()
    lifecycle = []
    service = RawSingleAgentService(
        model="test-model",
        session_db_path="unused.db",
        fetch_mcp_config=RawFetchMcpConfig(enabled=True),
    )

    @asynccontextmanager
    async def fake_connection(_config):
        lifecycle.append("connected")
        try:
            yield (fetch_server,)
        finally:
            lifecycle.append("cleaned")

    async def fake_stream(**kwargs):
        lifecycle.append(("agent", kwargs["agent"]))
        yield SimpleNamespace(
            type="completed",
            delta="",
            result=AgentSessionResult(
                final_output="页面内容",
                agent_name="Raw Single Agent",
            ),
        )

    built_agent = object()
    with (
        patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}, clear=False),
        patch(
            "python.ai_orchestrator.raw_assistant_service."
            "connected_raw_fetch_mcp_servers",
            fake_connection,
        ),
        patch(
            "python.ai_orchestrator.raw_assistant_service.create_raw_single_agent",
            return_value=built_agent,
        ),
        patch(
            "python.ai_orchestrator.raw_assistant_service.stream_agent_session",
            fake_stream,
        ),
    ):
        events = [
            event
            async for event in service.stream_assistant_request(
                self._request("翻译 https://example.com")
            )
        ]

    self.assertEqual(
        lifecycle,
        ["connected", ("agent", built_agent), "cleaned"],
    )
    self.assertEqual(events[-1]["type"], "run.completed")
```

- [ ] **Step 8: Run the service tests and verify RED**

Run the Task 3 unittest command.

Expected: FAIL because streaming does not keep the MCP context active.

- [ ] **Step 9: Wrap the full streaming iteration in the same MCP context**

```python
async with connected_raw_fetch_mcp_servers(
    self.fetch_mcp_config
) as mcp_servers:
    async for event in stream_agent_session(
        agent=self._get_agent(mcp_servers=mcp_servers),
        # Preserve all current arguments unchanged.
    ):
        # Preserve existing SSE event mapping unchanged.
```

The `async with` block must include the entire `async for` and final result processing so cleanup
cannot run before the SDK finishes its tool calls.

- [ ] **Step 10: Run the raw service and runtime regression tests**

```powershell
uv run --no-project --with-requirements python/ai_orchestrator/requirements.txt `
  python -m unittest `
  python.ai_orchestrator.tests.test_raw_assistant_service `
  python.ai_orchestrator.tests.test_assistant_runtime `
  python.ai_orchestrator.tests.test_assistant_runtime_mode -v
```

Expected: all listed tests PASS.

- [ ] **Step 11: Inspect mixed changes before deciding whether to commit**

```powershell
git diff -- python/ai_orchestrator/raw_assistant_service.py `
  python/ai_orchestrator/tests/test_raw_assistant_service.py
```

If the files contain only approved raw-agent work, commit:

```powershell
git add python/ai_orchestrator/raw_assistant_service.py `
  python/ai_orchestrator/tests/test_raw_assistant_service.py
git diff --cached
git commit -m "feat(agent): 接入原始模型网页抓取"
```

Otherwise keep them uncommitted and identify the pre-existing hunks in the handoff.

---

### Task 4: 文档、完整回归与真实网页验证

**Files:**
- Modify: `docs/agent/原始单Agent能力扩展.md:34-100`
- Modify: `docs/runbooks/environment-variables.md:150-170`
- Modify: `README.md:235-250`

**Interfaces:**
- Documents: `AI_ASSISTANT_RAW_FETCH_MCP_ENABLED`
- Documents: Web Search discovers URLs; Fetch MCP reads known URLs
- Documents: local-only SSRF warning and production hardening requirement

- [ ] **Step 1: Update the raw-agent capability document**

Add:

```markdown
| 能力 | 实现 | 用途 |
| --- | --- | --- |
| 网页搜索 | `WebSearchTool` | 搜索最新信息和发现页面 |
| 网页抓取 | `mcp-server-fetch` | 读取用户指定 URL 的网页正文 |

`AI_ASSISTANT_RAW_FETCH_MCP_ENABLED=true` 只用于本地实验。官方 Fetch Server
能够访问本地或内网地址，正式对外前必须增加 URL、DNS 与重定向级 SSRF 防护。
```

- [ ] **Step 2: Update environment-variable references**

Add the same exact variable to both environment tables:

```markdown
| `AI_ASSISTANT_RAW_FETCH_MCP_ENABLED` | `false` | 为原始模型启用本地 MCP Fetch 网页抓取实验能力。 |
```

- [ ] **Step 3: Run all Python orchestrator unit tests**

```powershell
uv run --no-project --with-requirements python/ai_orchestrator/requirements.txt `
  python -m unittest discover -s python/ai_orchestrator/tests -v
```

Expected: exit code `0`, no failing tests.

- [ ] **Step 4: Run a direct MCP protocol smoke test without calling OpenAI**

Run a temporary inline Python command that:

1. constructs `RawFetchMcpConfig(enabled=True)`;
2. enters `connected_raw_fetch_mcp_servers`;
3. asserts exactly one server is connected;
4. calls `await server.list_tools()`;
5. asserts the returned names contain `fetch`;
6. exits and observes process cleanup.

Command:

```powershell
@'
import asyncio
from python.ai_orchestrator.services.raw_fetch_mcp import (
    RawFetchMcpConfig,
    connected_raw_fetch_mcp_servers,
)

async def main():
    async with connected_raw_fetch_mcp_servers(
        RawFetchMcpConfig(enabled=True)
    ) as servers:
        assert len(servers) == 1
        tools = await servers[0].list_tools()
        assert "fetch" in {tool.name for tool in tools}
        print("fetch-mcp-smoke: ok")

asyncio.run(main())
'@ | uv run --no-project `
  --with-requirements python/ai_orchestrator/requirements.txt python -
```

Expected: `fetch-mcp-smoke: ok`.

- [ ] **Step 5: Enable the local flag without printing secrets**

Add `AI_ASSISTANT_RAW_FETCH_MCP_ENABLED=true` to the existing local environment source used by
the orchestrator. Do not print, rewrite, or commit `OPENAI_API_KEY`.

Restart only the Python orchestrator process and verify `/health` still returns HTTP 200.

- [ ] **Step 6: Run real raw-agent HTTP smoke cases**

Send two requests with the same conversation ID and `agentMode=single_agent_raw`:

First message:

```text
请读取 https://example.com 并用中文概括页面内容。
```

Second message:

```text
把刚才页面的标题翻译成中文，并说明你依据的是哪个页面。
```

Expected observable results:

- first response references the Example Domain page content;
- second response resolves “刚才页面” through the existing SDK Session;
- run metadata includes the MCP fetch tool call;
- no extra “联网 Agent” appears in the UI.

Then ask:

```text
2 + 2 等于多少？
```

Expected: ordinary answer succeeds without requiring a webpage fetch.

- [ ] **Step 7: Verify failure fallback**

Temporarily make the MCP command unavailable in a test-only process configuration while keeping
the raw model enabled. Send `2 + 2 等于多少？`.

Expected:

- log contains the Fetch MCP connection warning;
- the raw Agent still returns an answer;
- restore the normal command immediately after the test.

- [ ] **Step 8: Check formatting, docs, status, and merge suitability**

```powershell
git diff --check
git status --short
git diff --stat
```

Review the design success criteria line by line. Confirm:

- multi Agent files were not changed for this feature;
- `uv.lock` was not generated;
- no secret or `.env` file is staged;
- only intended files are staged or committed.

This feature remains unsuitable for direct merge into a public production configuration until
the documented SSRF gateway is implemented. It is suitable for the existing local experimental
branch with the feature flag defaulting to `false`.

- [ ] **Step 9: Commit documentation only when it does not include unrelated hunks**

```powershell
git add docs/agent/原始单Agent能力扩展.md `
  docs/runbooks/environment-variables.md README.md
git diff --cached
git commit -m "docs(agent): 补充原始模型网页抓取说明"
```

If `docs/agent/原始单Agent能力扩展.md` already contains earlier uncommitted work, leave it
uncommitted or stage only the reviewed hunk; never include unrelated content silently.
