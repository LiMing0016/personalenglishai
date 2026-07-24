# 原始模型接入 MCP Fetch 设计

日期：2026-07-24

## 目标

在现有“原始模型”模式中接入官方 `mcp-server-fetch`，让同一个 `Raw Single Agent`
在保留 `WebSearchTool` 的同时，能够读取用户指定网页的正文并继续完成总结、问答或翻译。

本次不新增 Agent、不修改多 Agent 路由、不增加系统提示词，也不承诺处理登录页、
强反爬页面、纯客户端渲染页面或无限长度网页。

## 选择的方案

使用 OpenAI Agents SDK 的 `MCPServerStdio` 启动本地 Fetch MCP 子进程，并把已连接的
server 传给现有 `Raw Single Agent` 的 `mcp_servers`。

Fetch Server 使用官方推荐的 `uvx` 方式运行，并固定包版本，避免每次启动获得不同实现：

```text
uvx --from mcp-server-fetch==2026.7.10 mcp-server-fetch
```

Windows 子进程设置 `PYTHONIOENCODING=utf-8`。

第一版按每次 Agent 运行建立并释放 stdio 连接。这样普通回复和流式回复都能在完整运行期间
使用 Fetch，清理边界清晰，也不需要现在改造 FastAPI 生命周期。代价是首次调用会增加子进程
启动延迟；验证能力稳定后，再考虑改为进程级 `MCPServerManager`。

## 能力挂载位置

能力只挂到 `single_agent_raw`：

```text
前端选择“原始模型”
  -> RawSingleAgentService
  -> Raw Single Agent
     -> WebSearchTool：发现网页、查询最新信息
     -> Fetch MCP：读取已知 URL 的网页正文
```

多 Agent 模式保持不变。前端不增加“联网 Agent”选项。

## 运行流程

1. `RawSingleAgentService` 校验请求并恢复原有会话上下文。
2. 如果 `AI_ASSISTANT_RAW_FETCH_MCP_ENABLED=true`，服务在本次运行期间连接 Fetch MCP。
3. 创建同一个 `Raw Single Agent`，同时挂载 `WebSearchTool` 和已连接的 Fetch MCP。
4. 模型根据当前消息与会话上下文自行决定是否搜索、抓取或直接回答。
5. Fetch 返回 Markdown；长网页可通过 `start_index` 分段继续读取。
6. Agent 生成最终答案后关闭 MCP 子进程；会话仍由现有 SQLite Session 管理。
7. MCP 启动失败时记录错误并回退到不带 Fetch 的原始 Agent，普通对话不因此不可用。

普通回复和流式回复必须走同一套能力装配逻辑，避免两种模式行为不一致。

## 配置与默认行为

- 新增 `AI_ASSISTANT_RAW_FETCH_MCP_ENABLED`，默认 `false`。
- 本地验证时显式设为 `true`。
- 不读取 Fetch MCP 提供的 Prompt，只暴露它的 `fetch` 工具。
- 不使用 `--ignore-robots-txt`，保留服务默认的 robots.txt 行为。
- 不修改原始 Agent 的 instructions，继续观察基础模型加工具后的原生决策能力。

## 安全边界

官方 Fetch Server 明确说明它可以访问本地或内网 IP，因此本次直接接入只用于本地消融实验，
不能按当前形态直接开放给不可信公网用户。

正式发布前必须在 Fetch Server 前增加参数级 URL 安全层，至少包括：

- 只允许 `http` 和 `https`；
- 拒绝 localhost、环回地址、私网地址、链路本地地址和云元数据地址；
- DNS 解析后再次校验目标 IP，并对重定向后的每一跳重复校验；
- 限制响应大小、重定向次数、超时和并发；
- 记录抓取域名与结果状态，不记录敏感正文。

Agents SDK 的 `tool_filter` 只能控制工具是否可见，不能校验每次调用的 URL 参数，因此不能把它
当作上述 SSRF 防护。

## 验证

先写失败测试，再实现：

1. 原始 Agent 未启用开关时只有现有 Web Search。
2. 开关启用且 MCP 已连接时，同一个 Agent 同时拥有 Web Search 和 Fetch MCP。
3. MCP 连接失败时回退到普通原始 Agent。
4. 普通与流式请求都会在运行结束后释放 MCP 连接。
5. 多 Agent 模式不加载 Fetch MCP。
6. Windows 启动参数包含 UTF-8 编码设置和固定的 Fetch Server 版本。
7. 端到端验证：
   - 给出一个公开静态网页 URL，请求总结页面；
   - 在下一轮不重复 URL，请求翻译页面中的一段，验证会话连续性；
   - 询问不需要联网的普通问题，验证模型不会被强制调用 Fetch；
   - 模拟 MCP 不可用，验证普通对话仍可完成。

## 成功标准

- 页面中只保留“多 Agent / 原始模型”两种模式。
- 原始模型可以自主组合搜索与网页抓取能力。
- 已知 URL 的公开静态网页可以被读取并用于回答。
- 追问仍使用同一对话上下文。
- Fetch 不可用不会阻断普通对话。
- 现有多 Agent 路由和前端 Markdown 渲染没有回归。

