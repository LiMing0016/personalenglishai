# 多引擎语法建议 Suppress 系统

> 日期：2026-03-11
> 分支：feature/evaluate-scoring
> 难度：中高

---

## 1. 问题背景

### 现象
用户在写作编辑器中反复看到同一条语法建议：

```
cartoon's message → message of the cartoon
```

点击"替换"后，Trinka 立刻建议反向替换：

```
message of the cartoon → cartoon's message
```

形成 **A↔B flip-flop**，刷新页面、重新进入编辑器后问题依然存在。

### 根因分析
- Trinka API 将风格偏好（`type=1, error_category='Others'`）误分类为语法错误
- 同一个表达的两种写法都是合法的，Trinka 每次都会建议替换成另一种
- 没有 suppress 机制，被忽略/替换的建议在下次检查时又会回来

### 类比
Grammarly 的解决方式：用户点击 Accept 或 Dismiss 后，该建议不再出现。底层是基于 fingerprint 的持久化 suppress。

---

## 2. 方案演进

### v1: 前端内存数组（失败）
```
recentFixes: string[]  // 纯内存
```
- 问题：刷新页面即丢失，flip-flop 立刻复现

### v2: sessionStorage 持久化（不够）
```
sessionStorage['peai:writing:recentFixes'] = JSON.stringify([...])
```
- 问题：仅限当前浏览器标签页，新窗口/新设备无效

### v3: Redis 后端 suppress — 初版（有缺陷）
```
Redis key: grammar:suppress:{userId}:{docId}
匹配: original + suggestion（兜底匹配）
TTL: 72h 统一
```
- 问题 1：`original + suggestion` 兜底太激进，同文档不同位置的同短语会被误杀
- 问题 2：dismiss 和 fix 混存，语义不同但 TTL 相同
- 问题 3：contextHash 用整句，用户改一个无关词就失效
- 问题 4：evaluate 返回的错误未过滤，提交作文后 suppress 失效

### v4: 精确指纹 + 分离存储（最终版）
```
Redis keys:
  grammar:dismiss:{userId}:{docId}  → TTL 72h
  grammar:fix:{userId}:{docId}      → TTL 30min

指纹: engine | ruleType | original | suggestion | contextHash
contextHash: SHA-256(span前后各30字符窗口)
```

---

## 3. 关键设计决策

### 3.1 为什么 dismiss 和 fix 要分开？

| | dismiss | fix |
|---|---------|-----|
| 语义 | "这不是错误，别再提醒" | "我已经替换了，短期内别反向建议" |
| TTL | 72 小时（长期有效） | 30 分钟（仅防 flip-flop） |
| 匹配策略 | 精确指纹（不走兜底） | 精确指纹 + 弱兜底（original+suggestion） |
| 反向记录 | 不需要 | 自动生成反向 suppress |

如果混存：
- fix 的 30 分钟 TTL 会让用户的 dismiss 也过早失效
- dismiss 的 72 小时 TTL 会让 fix 的弱兜底长期覆盖真实错误

### 3.2 为什么 contextHash 用 ±30 字符窗口？

**全句 hash 的问题：**
```
原句: "The cartoon's message reflects the common differences."
用户改了无关词: "The cartoon's message reflects the major differences."
→ 整句 hash 变了 → 之前的 dismiss 失效 → 错误又出现
```

**±30 字符窗口：**
```
error span: "cartoon's message" (位置 4-21)
窗口: text[0:51] = "The cartoon's message reflects the common dif"
用户改了 "common" → "major": 窗口末尾变了，但错误附近没变
→ 大部分情况下 hash 仍然稳定
```

trade-off: 如果用户编辑的位置恰好在窗口内但不是错误本身，hash 会变。这是可接受的 — 上下文确实变了，重新检查一次是合理的。

### 3.3 为什么 dismiss 不走兜底匹配？

```
场景: 文章中 "cartoon's message" 出现两次
用户只 dismiss 了第一处
兜底匹配(original+suggestion): 第二处也被 suppress → 误杀
精确指纹匹配: 第二处的 contextHash 不同 → 正常显示 → 正确
```

fix 可以走兜底是因为：
- TTL 只有 30 分钟，影响范围小
- flip-flop 的特征就是 original+suggestion 完全相同，兜底能精准防住

### 3.4 为什么需要 engine 字段？

```
场景: LanguageTool 和 Trinka 对同一处文本给出不同建议
用户 dismiss 了 Trinka 的建议
如果不区分 engine: LanguageTool 的合理建议也被 suppress → 误杀
加了 engine: 只 suppress Trinka 的，LanguageTool 的正常显示
```

---

## 4. 最终架构

### 数据流

```
用户点击"忽略"
    ├─ 前端: dismissedLocally.add(errorId)     ← 立即隐藏，不等后端
    └─ POST /grammar/suppress
         └─ Redis SET grammar:dismiss:{uid}:{docId}
              value: [{engine, ruleType, original, suggestion, contextHash, action}, ...]
              TTL: 72h

用户点击"替换"
    ├─ 前端: recentFixedLocally.add(errorId)   ← 立即隐藏
    ├─ 替换编辑器文本
    └─ POST /grammar/suppress (action=fix)
         └─ Redis SET grammar:fix:{uid}:{docId}
              value: [正向entry, 反向entry, ...]    ← 自动生成反向
              TTL: 30min

语法检查 / 评分返回前
    └─ filterSuppressed(userId, docId, errors, text)
         ├─ 读取 dismiss entries → 精确指纹匹配
         ├─ 读取 fix entries → 精确指纹 + 弱兜底
         └─ 过滤命中的 errors
```

### 指纹构成

```
fingerprint = engine | ruleType | normalize(original) | normalize(suggestion) | contextHash

contextHash = SHA-256(text[span.start-30 : span.end+30].toLowerCase().trim())[0:16hex]
```

### 前端状态拆分

```
旧: grammarFixedErrorIds (混合 dismiss + fix)
新: dismissedLocally     — 用户明确忽略的
    recentFixedLocally   — 用户已替换的
    locallyHiddenIds     — computed: 两者合集，用于 UI 显示
```

---

## 5. 涉及文件

### 后端
| 文件 | 改动 |
|------|------|
| `WritingEvaluateResponse.ErrorDto` | 新增 `engine` 字段 |
| `LanguageToolService` / `SaplingService` / `TrinkaService` / `TextGearsService` | 各自设置 `engine` 值 |
| `GrammarSuppressService` | 接口加 `engine` 参数 |
| `GrammarSuppressServiceImpl` | 完全重写：分离存储、精确指纹、窗口 contextHash |
| `GrammarSuppressRequest` | 新增 `engine` 字段 |
| `GrammarCheckRequest` | 新增 `docId` 字段（用于过滤作用域） |
| `WritingController` | grammar-check + evaluate 统一过滤 |
| `RedisConfig` | 新增（StringRedisTemplate bean） |
| `application.yml` | 新增 Redis 连接配置 |

### 前端
| 文件 | 改动 |
|------|------|
| `api/writing.ts` | error 类型加 `engine`，suppress 请求加 `engine` |
| `stores/grammarStore.ts` | 拆分 `dismissedLocally` + `recentFixedLocally`，传 engine，context 窗口化 |
| `panels/GrammarCheckPanel.vue` | 新增"忽略"按钮 + `dismiss-error` 事件 |
| `RightPanel.vue` | 转发 `dismiss-error` 事件 |
| `EditorShell.vue` | 绑定 `grammar-dismiss-error` |
| `useEvaluateSubmission.ts` | 轮询时传 `documentId` 用于 suppress 过滤 |

---

## 6. 踩坑记录

### 6.1 Redis localhost → 127.0.0.1
- `localhost` 在某些 Windows 环境下解析为 IPv6 `::1`
- Redis 默认只监听 IPv4 `127.0.0.1`
- 现象：`Unable to connect to Redis`
- 修复：`application.yml` 中 `host` 改为 `127.0.0.1`

### 6.2 evaluate 返回的错误未过滤
- 初版只在 `/grammar-check` 端点做了 suppress 过滤
- 用户提交作文后，`/evaluate` 返回的错误未经过滤，suppress 的错误又出现
- 修复：在 evaluate（同步）和 getEvaluateTask（异步轮询）的返回前，统一调用 `filterSuppressed`

### 6.3 前端 grammarFixedErrorIds 命名混乱
- 最初 `grammarFixedErrorIds` 同时承载"已修复"和"已忽略"两种语义
- 导致后续逻辑判断困难，也让代码可读性下降
- 修复：拆分为 `dismissedLocally`（忽略）+ `recentFixedLocally`（替换）

### 6.4 ID 在合并时被重写
- 各引擎返回的错误 ID 前缀不同（`lt1`, `tk1`, `sp1`, `tg1`）
- `GrammarCheckServiceImpl` 合并后统一重写为 `gc1`, `gc2`...
- 导致原始引擎信息丢失
- 修复：新增 `engine` 字段，在 `setId` 之外独立保存引擎来源

---

## 7. 可能的后续优化

1. **suppress 统计** — 记录用户最常 dismiss 的引擎/规则类型，用于调整引擎权重
2. **批量 dismiss** — "忽略该引擎所有此类建议" 按钮
3. **suppress 同步到用户偏好** — 跨文档的全局 suppress（如用户始终偏好 `cartoon's message` 而非 `message of the cartoon`）
4. **contextHash 自适应窗口** — 短句用全句 hash，长句用 ±30 窗口
