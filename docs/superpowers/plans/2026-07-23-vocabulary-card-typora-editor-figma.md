# 单词卡 Typora 式编辑器 Figma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有单词卡 Figma 文件中完成简化版 Typora 所见即所得编辑体验，并覆盖桌面默认态、文本选中态、章节 AI 操作态和移动端目录态。

**Architecture:** 复用现有单词卡页面的应用栏、单词头部、核心词义和视觉变量，仅替换学习内容编辑工作区。桌面端使用居中正文画布加右侧吸附目录；交互状态通过独立 Frame 表达，避免在一个 Frame 中叠放多个互斥状态。

**Tech Stack:** Figma Plugin API、Inter、Noto Sans SC、现有 Personal English AI 绿色视觉变量

## Global Constraints

- 学习内容继续使用单一 Markdown 文档存储。
- 核心词义继续使用结构化 JSON，并通过独立入口编辑。
- 正文画布宽度保持在 `760px` 至 `820px`。
- 不提供左右分屏预览。
- 不使用大型输入框、多层卡片或通栏工具栏。
- AI 操作只影响当前章节，不覆盖其他章节和个人笔记。
- 顶部只保留一组保存操作，正文底部不重复显示保存按钮。
- 移动端不依赖悬停交互。

---

### Task 1: 建立桌面默认编辑态

**Files:**
- Modify: Figma file `uBlROfFXGcUTjfbvhNU5M3`
- Source node: `20:166` `05 · 章节 Markdown 编辑态 v3`
- Create: `06 · Typora 编辑态 · 默认`

**Interfaces:**
- Consumes: 现有应用栏、单词头部、核心词义和顶部操作栏
- Produces: 桌面默认编辑 Frame，供后续交互状态复制

- [ ] **Step 1: 复制现有编辑 Frame**

使用 Figma Plugin API 复制 `20:166`，放置在当前页面右侧，保持 `1440 × 1200`。

```javascript
const source = await figma.getNodeByIdAsync("20:166");
const target = source.clone();
target.name = "06 · Typora 编辑态 · 默认";
target.x = source.x + source.width + 180;
```

- [ ] **Step 2: 重建学习内容工作区**

删除复制 Frame 中旧的学习内容编辑工作区，创建水平布局：

```text
正文区域 980px
  └─ 居中正文画布 800px
右侧目录 220px
间距 40px
```

正文画布不设置背景框、描边或阴影；只使用文章排版和章节间分隔。

- [ ] **Step 3: 绘制所见即所得正文**

使用真实渲染样式展示以下内容，不显示 `###`、`-` 和 `**`：

```markdown
## 例句

### 人择含义

1. The anthropic principle asks why the universe permits observers like us.
   人择原理探讨宇宙为何允许像我们这样的观察者存在。

2. The discussion took an anthropic perspective on the universe.
   这场讨论从人择角度看待宇宙。

### 人为含义

1. The wildfire had anthropic causes.
   这场野火有人为原因。

## 使用边界

### 适合使用

- Refer to theories about the conditions required for human existence.

### 谨慎使用

- Do not use it as a general synonym for human.
```

- [ ] **Step 4: 添加右侧吸附目录**

目录包含：

```text
本文目录
例句              当前项
使用边界
易混辨析
记忆提示
个人笔记
返回顶部
```

当前项使用 `3px` 绿色竖线、绿色文字和中等字重；目录本身无卡片容器。

- [ ] **Step 5: 获取截图并检查默认态**

调用 Figma 截图工具导出 `1440 × 1200` 画面。确认正文居中、目录不抢占正文视觉层级，并且页面中不存在大型编辑框。

---

### Task 2: 建立文本选中与浮动工具栏状态

**Files:**
- Modify: Figma file `uBlROfFXGcUTjfbvhNU5M3`
- Source: `06 · Typora 编辑态 · 默认`
- Create: `07 · Typora 编辑态 · 文本选中`

**Interfaces:**
- Consumes: Task 1 的默认编辑 Frame
- Produces: 选中文本时的浮动格式工具栏状态

- [ ] **Step 1: 复制默认编辑 Frame**

```javascript
const source = figma.currentPage.findOne(
  node => node.name === "06 · Typora 编辑态 · 默认"
);
const target = source.clone();
target.name = "07 · Typora 编辑态 · 文本选中";
target.x = source.x + source.width + 180;
```

- [ ] **Step 2: 表现文本选区**

在第一条例句的 `anthropic principle` 文本后添加浅绿色选区背景，颜色使用现有品牌浅绿色，透明度保持在 `16%` 至 `22%`。

- [ ] **Step 3: 添加浮动工具栏**

工具栏位于选区上方，尺寸约为 `284 × 40`，包含：

```text
Bold
Italic
Link
Quote
AI 改写
```

工具栏使用白色背景、`1px` 浅灰描边和轻微阴影；圆角不超过 `6px`。工具栏宽度由内容决定，不铺满正文。

- [ ] **Step 4: 截图检查浮层关系**

确认工具栏不遮挡章节标题、目录或顶部操作栏，并与选区保持 `8px` 至 `10px` 距离。

---

### Task 3: 建立章节 AI 操作状态

**Files:**
- Modify: Figma file `uBlROfFXGcUTjfbvhNU5M3`
- Source: `06 · Typora 编辑态 · 默认`
- Create: `08 · Typora 编辑态 · 章节 AI 操作`

**Interfaces:**
- Consumes: Task 1 的默认编辑 Frame
- Produces: 当前章节获得焦点时的 AI 操作状态

- [ ] **Step 1: 复制默认编辑 Frame**

```javascript
const source = figma.currentPage.findOne(
  node => node.name === "06 · Typora 编辑态 · 默认"
);
const target = source.clone();
target.name = "08 · Typora 编辑态 · 章节 AI 操作";
target.x = source.x + source.width + 180;
```

- [ ] **Step 2: 标记当前章节**

在“例句”章节标题左侧添加 `2px` 绿色竖线；正文其余区域保持原样，不使用整块背景。

- [ ] **Step 3: 显示章节级操作**

在“例句”标题右侧显示：

```text
AI 调整
换一组例句
降低难度
更多
```

主要操作使用绿色文字和图标，次要操作使用无底色文字按钮。操作区不得变成通栏绿色提示条。

- [ ] **Step 4: 添加差异预览提示**

在操作区下方用单行状态说明表达：

```text
AI 只会修改本章节，确认后替换
```

提示使用 `12px` 辅助文字，不创建大型提示卡。

- [ ] **Step 5: 截图检查章节作用域**

确认视觉上能够明确识别当前编辑章节，同时不让其他章节呈现禁用或不可编辑状态。

---

### Task 4: 建立移动端目录抽屉状态

**Files:**
- Modify: Figma file `uBlROfFXGcUTjfbvhNU5M3`
- Create: `09 · Typora 编辑态 · 移动端目录`

**Interfaces:**
- Consumes: Task 1 的正文层级和目录信息
- Produces: `390 × 844` 移动端目录打开状态

- [ ] **Step 1: 创建移动端 Frame**

```javascript
const mobile = figma.createFrame();
mobile.name = "09 · Typora 编辑态 · 移动端目录";
mobile.resize(390, 844);
mobile.clipsContent = true;
```

- [ ] **Step 2: 调整移动端正文**

正文左右边距为 `20px`，隐藏桌面右侧目录。顶部编辑栏保留返回、保存状态和更多操作。

- [ ] **Step 3: 添加目录入口**

在顶部编辑栏增加“目录”图标按钮。按钮使用现有图标库中的列表或目录图标，不使用文字符号模拟图标。

- [ ] **Step 4: 绘制目录抽屉**

抽屉从右侧覆盖页面，宽度约为 `300px`，包含桌面目录中的全部章节和关闭按钮。当前项同时使用绿色竖线、文字加深和“当前”辅助文本。

- [ ] **Step 5: 检查触控与文字适配**

所有目录项高度至少 `44px`，标题不溢出，关闭按钮和目录项均有清晰触控区域。

---

### Task 5: 视觉 QA 与交付

**Files:**
- Inspect: Figma nodes created by Tasks 1-4
- Reference screenshot: `C:\Users\Catalina\AppData\Local\Temp\figma-v3-open-canvas-edit.png`

**Interfaces:**
- Consumes: 四个 Figma 状态
- Produces: 可交付的节点链接、截图和检查结果

- [ ] **Step 1: 运行字体与溢出检查**

遍历四个 Frame 的文本节点，确认字体仅使用 `Inter` 和 `Noto Sans SC`，不存在固定尺寸文本溢出。

```javascript
const allowed = new Set(["Inter", "Noto Sans SC"]);
const invalidFonts = [];
const overflow = [];

function walk(node) {
  if (node.type === "TEXT") {
    if (node.fontName !== figma.mixed && !allowed.has(node.fontName.family)) {
      invalidFonts.push(node.id);
    }
    if (
      node.textAutoResize === "NONE" &&
      (node.width < node.minWidth || node.height < node.minHeight)
    ) {
      overflow.push(node.id);
    }
  }
  if ("children" in node) node.children.forEach(walk);
}
```

- [ ] **Step 2: 逐状态导出截图**

每个 Frame 使用相同最大尺寸导出截图：

```text
默认编辑态
文本选中态
章节 AI 操作态
移动端目录态
```

- [ ] **Step 3: 进行并排视觉比较**

将旧版编辑态截图与新版默认编辑态放入同一视觉比较输入，检查：

```text
大型输入框是否消失
正文层级是否清晰
目录是否比正文更弱
操作控件是否紧凑
留白是否均衡
顶部结构是否与现有产品一致
```

- [ ] **Step 4: 修复可见问题并重新截图**

只修复比较中实际出现的间距、字体、遮挡、溢出、边框和圆角问题。修复后重新完成字体、溢出和截图检查。

- [ ] **Step 5: 交付节点链接**

返回四个可直接打开的 Figma 节点链接，并说明各状态用途和验证结果。

