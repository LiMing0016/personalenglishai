<template>
  <div class="intensive-workspace-page">
    <header class="workspace-ide-titlebar">
      <div class="workspace-brand">
        <span class="workspace-brand__mark" aria-hidden="true">E</span>
        <div>
          <strong>Personal English AI</strong>
        </div>
      </div>

      <label class="workspace-command-center">
        <span class="sr-only">搜索或输入命令</span>
        <input type="search" placeholder="搜索 PDF、笔记、知识点，或输入命令..." />
      </label>

      <div class="workspace-titlebar-actions">
        <button
          type="button"
          class="back-button"
          aria-label="返回翻译列表"
          title="返回翻译列表"
          @click="goBackToHub">
          <span class="back-button-icon" aria-hidden="true">←</span>
        </button>
        <button type="button" class="primary-action" @click="completeLearningSession">完成学习</button>
      </div>
    </header>

    <main
      v-if="readingDocument && activeBlock && activeInsight"
      ref="workspaceShellRef"
      class="workspace-shell workspace-shell--ide"
      :class="{
        'workspace-shell--outline-collapsed': isOutlineCollapsed,
        'workspace-shell--agent-collapsed': isAgentCollapsed,
      }"
      :style="{
        '--outline-column-width': `${outlineColumnWidth}px`,
        '--agent-column-width': `${agentColumnWidth}px`,
      }">
      <nav class="workspace-activity-bar" aria-label="学习工作台入口">
        <button
          v-for="panel in sidePanelOptions"
          :key="panel.id"
          type="button"
          class="activity-button"
          :class="{ active: activeSidePanel === panel.id && !isOutlineCollapsed }"
          :aria-label="panel.label"
          :title="panel.label"
          @click="selectSidePanel(panel.id)">
          <span class="activity-button__icon" aria-hidden="true">{{ panel.icon }}</span>
          <small v-if="panel.count > 0">{{ panel.count }}</small>
        </button>
      </nav>

      <aside
        class="workspace-outline-panel workspace-side-drawer workspace-explorer"
        :class="{ 'workspace-panel--collapsed': isOutlineCollapsed }"
        aria-label="目录与学习资产">
        <div class="outline-header">
          <div class="outline-heading-main">
            <span>{{ sidePanelSummary }}</span>
          </div>
          <button
            type="button"
            class="panel-drawer-toggle"
            aria-label="收起左侧目录导航"
            title="收起左侧目录导航"
            @click="toggleOutlineDrawer">
            收起
          </button>
        </div>

        <section class="workspace-resource-actions" aria-label="导入与新建">
          <button type="button" @click="openImportPdfEntry">导入 PDF</button>
          <button type="button" @click="openStandaloneNoteTab">新建笔记</button>
          <button type="button" @click="openTopicTab()">新建专题</button>
        </section>

        <div class="side-drawer-switcher" aria-label="左侧工作区切换">
          <button
            v-for="panel in sidePanelOptions"
            :key="panel.id"
            type="button"
            :class="{ active: activeSidePanel === panel.id }"
            @click="selectSidePanel(panel.id)">
            {{ panel.label }}
          </button>
        </div>

        <template v-if="activeSidePanel === 'outline'">
        <section class="project-tree-shell" aria-label="项目资源树">
          <header class="project-tree-toolbar">
            <div>
              <span>学习项目</span>
              <strong>{{ readingDocument.title }}</strong>
            </div>
            <small>{{ projectTreeSummary }}</small>
          </header>

          <section class="project-tree">
            <article
              v-for="folder in projectTreeFolders"
              :key="folder.id"
              class="project-tree-folder"
              :class="{ 'is-collapsed': isProjectTreeFolderCollapsed(folder.id) }">
              <button
                type="button"
                class="project-tree-folder__header"
                :aria-expanded="!isProjectTreeFolderCollapsed(folder.id)"
                @click="toggleProjectTreeFolder(folder.id)">
                <span class="project-tree-folder__chevron" aria-hidden="true">›</span>
                <span>{{ folder.label }}</span>
                <small>{{ folder.badge }}</small>
              </button>

              <div v-if="!isProjectTreeFolderCollapsed(folder.id)" class="project-tree-folder__body">
                <button
                  v-for="resource in folder.resources"
                  :key="resource.id"
                  type="button"
                  class="project-tree-resource"
                  :class="`project-tree-resource--${resource.kind}`"
                  @click="openProjectTreeResource(resource)">
                  <span class="project-tree-resource__icon" aria-hidden="true">{{ getProjectTreeResourceIcon(resource.kind) }}</span>
                  <span class="project-tree-resource__main">
                    <strong>{{ resource.title }}</strong>
                    <small v-if="resource.subtitle">{{ resource.subtitle }}</small>
                  </span>
                  <mark v-if="resource.count">{{ resource.count }}</mark>
                </button>
                <button
                  v-if="folder.resources.length === 0"
                  type="button"
                  class="project-tree-empty"
                  @click="handleProjectTreeFolderEmptyAction(folder)">
                  {{ folder.emptyText }}
                </button>
              </div>
            </article>

            <article
              class="project-tree-folder project-tree-outline"
              :class="{ 'is-collapsed': isProjectTreeFolderCollapsed('pdf-outline') }">
              <button
                type="button"
                class="project-tree-folder__header"
                :aria-expanded="!isProjectTreeFolderCollapsed('pdf-outline')"
                @click="toggleProjectTreeFolder('pdf-outline')">
                <span class="project-tree-folder__chevron" aria-hidden="true">›</span>
                <span>PDF 目录</span>
                <small>{{ outlineTreeItems.length || outlinePageItems.length }}</small>
              </button>

              <div v-if="!isProjectTreeFolderCollapsed('pdf-outline')" class="project-tree-folder__body">
                <section class="outline-controls" aria-label="目录筛选">
                  <label class="outline-search">
                    <span>搜索目录</span>
                    <input
                      v-model="outlineSearchQuery"
                      type="search"
                      placeholder="章节、页码、笔记..."
                      aria-label="搜索目录"
                    />
                  </label>

                  <div class="outline-filter-tabs" aria-label="目录范围">
                    <button
                      v-for="scope in outlineFilterScopes"
                      :key="scope.id"
                      type="button"
                      :class="{ active: outlineFilterScope === scope.id }"
                      @click="outlineFilterScope = scope.id">
                      {{ scope.label }}
                      <small>{{ scope.count }}</small>
                    </button>
                  </div>

                  <div class="outline-quick-actions" aria-label="书签操作">
                    <button type="button" @click="createUserBookmark">添加书签</button>
                    <button type="button" @click="exportWorkspaceBookmarks">导出 PDF</button>
                    <button v-if="activeUserBookmark" type="button" @click="renameActiveUserBookmark">重命名</button>
                    <button v-if="activeUserBookmark" type="button" class="danger" @click="deleteActiveUserBookmark">删除</button>
                  </div>
                </section>

                <nav class="outline-list" aria-label="PDF 页码与目录">
                  <section v-if="displayOutlineItems.length > 0 && filteredOutlineItems.length > 0" class="outline-page-group">
                    <div
                      v-for="item in filteredOutlineItems"
                      :key="item.id"
                      class="outline-tree-row"
                      :class="[
                        `outline-tree-row--level-${item.displayLevel}`,
                        {
                          active: !item.syntheticRoot && isOutlineItemActive(item),
                          'is-current-page': !item.syntheticRoot && item.pageNumber === currentPdfPage,
                          'has-notes': getOutlineItemNoteCount(item) > 0,
                          'is-document-root': item.syntheticRoot,
                          'is-user-bookmark': item.source === 'user_bookmark',
                          'is-user-bookmark-root': item.source === 'user_bookmark_root',
                          'is-collapsed': item.hasChildren && isOutlineNodeCollapsed(item),
                        },
                      ]">
                      <button
                        type="button"
                        class="outline-node-toggle"
                        :class="{ 'is-placeholder': !item.hasChildren }"
                        :aria-label="isOutlineNodeCollapsed(item) ? `展开 ${item.title}` : `收起 ${item.title}`"
                        :aria-expanded="item.hasChildren ? !isOutlineNodeCollapsed(item) : undefined"
                        :disabled="!item.hasChildren"
                        @click.stop="toggleOutlineNode(item)">
                        <span>›</span>
                      </button>

                      <button
                        type="button"
                        class="outline-block-button"
                        :class="[
                          `outline-block-button--level-${item.displayLevel}`,
                          {
                            active: !item.syntheticRoot && isOutlineItemActive(item),
                            'is-current-page': !item.syntheticRoot && item.pageNumber === currentPdfPage,
                            'has-notes': getOutlineItemNoteCount(item) > 0,
                            'is-document-root': item.syntheticRoot,
                            'is-user-bookmark': item.source === 'user_bookmark',
                            'is-user-bookmark-root': item.source === 'user_bookmark_root',
                          },
                        ]"
                        @click="selectOutlineItem(item)">
                        <span class="outline-item-title">{{ item.title }}</span>
                        <span class="outline-item-meta">
                          <small v-if="item.source === 'user_bookmark_root'">自定义</small>
                          <small v-else-if="item.syntheticRoot">全文目录</small>
                          <small v-else-if="item.source === 'user_bookmark'">Page {{ item.pageNumber }} · 我的</small>
                          <small v-else>Page {{ item.pageNumber }}</small>
                          <mark v-if="!item.syntheticRoot && item.pageNumber === currentPdfPage">当前</mark>
                          <mark v-if="getOutlineItemNoteCount(item) > 0" class="note-count">
                            {{ getOutlineItemNoteCount(item) }} 记
                          </mark>
                        </span>
                      </button>
                    </div>
                  </section>

                  <section v-else-if="outlineItems.length === 0 && userBookmarks.length === 0 && filteredOutlinePageItems.length > 0" class="outline-page-group">
                    <button
                      v-for="page in filteredOutlinePageItems"
                      :key="page"
                      type="button"
                      class="outline-page-button"
                      :class="{ active: page === currentPdfPage, 'has-notes': getPageNoteCount(page) > 0 }"
                      @click="selectOutlinePage(page)">
                      <span>Page {{ page }}</span>
                      <small v-if="getPageNoteCount(page) > 0">{{ getPageNoteCount(page) }} 条笔记</small>
                    </button>
                  </section>

                  <section v-else class="outline-empty-state">
                    <strong>没有匹配的目录</strong>
                    <span>换个关键词，或切回全部范围。</span>
                  </section>
                </nav>
              </div>
            </article>
          </section>
        </section>
        </template>

        <section v-else-if="activeSidePanel === 'bookmarks'" class="side-drawer-panel side-bookmark-list" aria-label="我的书签">
          <div class="side-section-heading">
            <strong>我的书签</strong>
            <button type="button" @click="createUserBookmark">添加书签</button>
          </div>
          <article
            v-for="bookmark in userBookmarks"
            :key="bookmark.id"
            class="side-list-card"
            :class="{ active: bookmark.id === activeOutlineItemId }"
            @click="jumpToUserBookmark(bookmark)">
            <strong>{{ bookmark.title }}</strong>
            <span>Page {{ bookmark.pageNumber }} · {{ bookmark.source === 'user_bookmark' ? '我的定位' : 'PDF 书签' }}</span>
          </article>
          <button
            v-if="userBookmarks.length === 0"
            type="button"
            class="side-empty-action"
            @click="createUserBookmark">
            给当前页添加书签
          </button>
        </section>

        <section v-else-if="activeSidePanel === 'notes'" class="side-drawer-panel side-note-list" aria-label="全文笔记">
          <div class="side-section-heading">
            <strong>全文笔记</strong>
            <button type="button" @click="startNoteFromActiveBlock">新建笔记</button>
          </div>
          <article
            v-for="note in studyNotes.slice(0, 24)"
            :key="note.id"
            class="side-list-card"
            :class="{ active: note.id === activeNoteId }"
            @click="openStudyNote(note.id)">
            <strong>{{ note.title }}</strong>
            <span>Page {{ note.pageNumber }} · {{ noteStatusLabels[note.status] }}</span>
          </article>
          <button
            v-if="studyNotes.length === 0"
            type="button"
            class="side-empty-action"
            @click="startNoteFromActiveBlock">
            记录当前理解
          </button>
        </section>

        <section v-else-if="activeSidePanel === 'assets'" class="side-drawer-panel side-asset-board" aria-label="学习资产">
          <div class="side-section-heading">
            <strong>学习资产</strong>
            <button type="button" @click="askAgent('整理当前段落为笔记草稿')">Agent 整理</button>
          </div>
          <section
            v-for="column in studyAssetPipeline"
            :key="column.id"
            class="side-asset-group"
            :class="`side-asset-group--${column.tone}`">
            <header>
              <span>{{ column.label }}</span>
              <small>{{ column.notes.length }}</small>
            </header>
            <p>{{ column.description }}</p>
            <article
              v-for="note in column.notes.slice(0, 4)"
              :key="note.id"
              class="side-asset-card"
              :class="{ active: note.id === activeNoteId }"
              @click="openStudyNote(note.id)">
              <strong>{{ note.title }}</strong>
              <span>Page {{ note.pageNumber }} · {{ note.source === 'agent' ? 'Agent' : '我' }}</span>
            </article>
            <button
              v-if="column.notes.length === 0"
              type="button"
              class="side-empty-action"
              @click="column.id === 'draft' ? askAgent('整理当前段落为笔记草稿') : startNoteFromActiveBlock()">
              {{ column.id === 'draft' ? '让 Agent 整理' : '新增笔记' }}
            </button>
          </section>
        </section>

        <section v-else-if="activeSidePanel === 'search'" class="side-drawer-panel side-search-panel" aria-label="搜索当前文档">
          <label class="outline-search">
            <span>搜索当前文档</span>
            <input
              v-model="outlineSearchQuery"
              type="search"
              placeholder="章节、页码、笔记..."
              aria-label="搜索当前文档"
            />
          </label>
          <article
            v-for="item in filteredOutlineItems.slice(0, 18)"
            :key="item.id"
            class="side-list-card"
            @click="selectOutlineItem(item)">
            <strong>{{ item.title }}</strong>
            <span>Page {{ item.pageNumber }}</span>
          </article>
          <button
            v-if="filteredOutlineItems.length === 0"
            type="button"
            class="side-empty-action"
            @click="outlineSearchQuery = ''">
            清空搜索
          </button>
        </section>
      </aside>

      <button
        v-if="!isOutlineCollapsed"
        type="button"
        class="workspace-resizer workspace-resizer--outline"
        aria-label="调整左侧目录宽度"
        title="拖动调整左侧目录宽度"
        @pointerdown="startWorkspaceResize('outline', $event)"
      />

      <section class="workspace-canvas-panel" aria-label="阅读区">
        <header class="workspace-tabs" aria-label="工作区标签页">
          <button
            v-for="tab in workspaceTabs"
            :key="tab.id"
            type="button"
            class="workspace-tab"
            :class="[`workspace-tab--${tab.kind}`, { active: tab.id === activeWorkspaceTabId, dirty: tab.dirty }]"
            @click="activateWorkspaceTab(tab.id)">
            <span>{{ tab.subtitle }}</span>
            <strong>{{ tab.title }}</strong>
            <small v-if="tab.dirty">●</small>
            <small
              v-else
              class="workspace-tab__close"
              role="button"
              tabindex="-1"
              aria-label="关闭标签页"
              @click.stop="closeWorkspaceTab(tab.id)">
              ×
            </small>
          </button>
          <button
            type="button"
            class="workspace-tab workspace-tab--new"
            aria-label="新建学习资源"
            @click="openStandaloneNoteTab">
            +
          </button>
        </header>

        <div class="workspace-editor-area" :class="`workspace-editor-area--${activeWorkspaceTabKind}`">
          <section v-if="activeWorkspaceTabKind !== 'pdf'" class="note-document-editor">
            <p>{{ activeWorkspaceTab?.subtitle }}</p>
            <h2>{{ activeWorkspaceTab?.title }}</h2>
            <textarea placeholder="这里是完整笔记编辑区，后续阶段接入真实笔记内容。" />
          </section>

          <template v-else>
            <div class="workspace-editor-toolbar">
              <div v-if="readingDocument" class="document-view-tabs document-view-tabs--compact" aria-label="原文展示模式">
                <button
                  type="button"
                  :class="{ active: documentView === 'pdf-canvas' }"
                  @click="documentView = 'pdf-canvas'">
                  PDF 学习画布
                </button>
                <button
                  type="button"
                  :class="{ active: documentView === 'text' }"
                  @click="documentView = 'text'">
                  精读文本
                </button>
              </div>
            </div>

            <div v-if="documentView === 'text'" class="ide-reader-surface" role="list" aria-label="原文段落列表">
              <article
                v-for="block in readingDocument.blocks"
                :key="block.id"
                role="listitem"
                class="ide-document-block"
                :class="{ active: block.id === activeBlockId }"
                @click="selectOutlineBlock(block.id, block.pageNumber || 1)">
                <aside class="ide-gutter" aria-label="段落定位">
                  <span>P{{ block.order }}</span>
                  <small v-if="block.pageNumber">Page {{ block.pageNumber }}</small>
                </aside>

                <div class="ide-source-cell">
                  <div class="ide-block-meta">
                    <span>{{ block.type === 'heading' ? 'Heading' : 'Paragraph' }}</span>
                    <button type="button" @click.stop="askAgent('解释当前段落')">Ask</button>
                    <button type="button" @click.stop="askAgent('翻译当前段落')">Translate</button>
                    <button type="button" @click.stop="startNoteFromActiveBlock">Note</button>
                  </div>
                  <p class="source-text source-text--ide">{{ block.text }}</p>
                </div>
              </article>
            </div>

            <PdfLearningCanvas
              v-else
              :document-id="readingDocument.id"
              :title="readingDocument.title"
              :src="readingDocument.pdfPreviewUrl"
              :blocks="readingDocument.blocks"
              :active-block-id="activeBlockId"
              :page-count="readingDocument.pageCount"
              :target-page="targetPdfPage"
              :source-highlight="pdfSourceHighlight"
              :note-anchors="noteAnchors"
              :active-note-id="activeNoteId"
              @select-block="selectBlock"
              @ask-agent="askAgent"
              @note-selection="startNoteFromPdfSelection"
              @open-note="openStudyNote"
              @selection-change="handlePdfSelectionChange"
              @page-change="handlePdfPageChange"
            />
          </template>
        </div>
      </section>

      <button
        v-if="!isAgentCollapsed"
        type="button"
        class="workspace-resizer workspace-resizer--agent"
        aria-label="调整右侧 Agent 宽度"
        title="拖动调整右侧 Agent 宽度"
        @pointerdown="startWorkspaceResize('agent', $event)"
      />

      <aside
        class="agent-panel agent-panel--ide workspace-agent-panel"
        :class="{ 'workspace-panel--collapsed': isAgentCollapsed }"
        aria-labelledby="agent-title">
        <button
          v-if="isAgentCollapsed"
          type="button"
          class="workspace-drawer-rail workspace-drawer-rail--agent"
          aria-label="展开右侧 Agent"
          title="展开右侧 Agent"
          @click="toggleAgentDrawer">
          Agent
        </button>

        <div class="agent-header agent-header--ide">
          <div>
            <h2 id="agent-title">{{ agentPanelMode === 'note-workbench' ? '笔记工作台' : 'Agent' }}</h2>
          </div>
          <span>P{{ activeBlock.order }} · {{ modeLabels[activeMode] }}</span>
          <button
            type="button"
            class="panel-drawer-toggle"
            aria-label="收起右侧 Agent"
            title="收起右侧 Agent"
            @click="toggleAgentDrawer">
            收起
          </button>
        </div>

        <section v-if="agentPanelMode === 'note-workbench'" class="note-workbench-panel" aria-label="笔记工作台">
          <header class="note-workbench-header">
            <div>
              <p class="answer-label">锚点笔记</p>
              <strong>P{{ selectedPdfContext?.pageNumber || currentPdfPage }} · 选区</strong>
            </div>
            <button type="button" @click="agentPanelMode = 'agent'">返回 Agent</button>
          </header>

          <nav class="note-workbench-tabs" aria-label="笔记工作台模式">
            <button type="button" class="active">写笔记</button>
            <button
              type="button"
              :disabled="noteAgentLoading"
              @click="askAgentToAppendNote('结合当前选区，补充一段适合写入学习笔记的解释。')">
              问 AI
            </button>
            <button
              type="button"
              :disabled="noteAgentLoading"
              @click="askAgentToAppendNote('把当前选区整理成 3 条复习要点。')">
              整理
            </button>
          </nav>

          <form
            v-if="noteComposer.mode !== 'idle'"
            class="study-note-composer note-workbench-composer"
            @submit.prevent="saveStudyNote">
            <div class="study-note-source">
              <span>{{ noteComposer.source === 'agent' ? 'Agent 草稿' : '手动笔记' }}</span>
              <small>{{ noteComposerContextLabel }}</small>
            </div>
            <blockquote v-if="noteComposer.context?.text" class="study-note-selected-text">
              {{ noteComposer.context.text }}
            </blockquote>
            <input
              v-model="noteComposer.title"
              type="text"
              placeholder="笔记标题"
              aria-label="笔记标题"
            />
            <textarea
              ref="noteContentInputRef"
              v-model="noteComposer.content"
              rows="8"
              placeholder="围绕这个选区写下理解、疑问或总结。"
              aria-label="笔记内容"
            />
            <div class="note-agent-compose" aria-label="Agent 辅助补充笔记">
              <textarea
                v-model="noteAgentPrompt"
                rows="2"
                placeholder="边问边补：例如这里为什么能推出时间复杂度？"
                aria-label="问 Agent 并生成候选补充"
              />
              <div class="note-agent-compose__actions">
                <span>{{ noteAgentLoading ? 'Agent 正在补充...' : '回答会先进入候选区' }}</span>
                <button
                  type="button"
                  :disabled="noteAgentLoading || !noteAgentPrompt.trim()"
                  @click="askAgentToAppendNote(noteAgentPrompt)">
                  问 AI 生成候选
                </button>
              </div>
            </div>
            <section class="ai-candidate-card" aria-label="AI 候选补充">
              <p class="answer-label">AI 候选补充</p>
              <blockquote>{{ aiCandidateContent || 'Agent 的补充会先出现在这里，确认后再追加到笔记。' }}</blockquote>
              <button type="button" :disabled="!aiCandidateContent" @click="appendAiCandidateToNote">
                追加到笔记
              </button>
            </section>
            <div class="study-note-composer__actions">
              <button type="button" @click="cancelStudyNoteComposer">取消</button>
              <button type="submit" class="primary-action">
                {{ noteComposer.status === 'draft' ? '保存草稿' : '保存笔记' }}
              </button>
            </div>
          </form>

          <div v-else class="study-note-empty">
            <span>当前没有打开锚点笔记</span>
            <small>从 PDF 选区点击记笔记后，这里会进入沉浸式笔记工作台。</small>
          </div>
        </section>

        <template v-else>
        <section
          class="study-note-panel"
          :class="{ 'study-note-panel--composer-active': noteComposer.mode !== 'idle' }"
          aria-label="本段笔记">
          <header class="study-note-panel__header">
            <div>
              <p class="answer-label">{{ noteComposer.mode !== 'idle' ? '锚点笔记' : '本段笔记' }}</p>
              <strong>{{ noteComposer.mode !== 'idle' ? '正在编辑当前选区' : activeBlockNotes.length + ' 条' }}</strong>
            </div>
            <button v-if="noteComposer.mode === 'idle'" type="button" @click="startNoteFromActiveBlock">新建</button>
          </header>

          <form
            v-if="noteComposer.mode !== 'idle'"
            class="study-note-composer"
            @submit.prevent="saveStudyNote">
            <div class="study-note-source">
              <span>{{ noteComposer.source === 'agent' ? 'Agent 草稿' : '手动笔记' }}</span>
              <small>{{ noteComposerContextLabel }}</small>
            </div>
            <blockquote v-if="noteComposer.context?.text" class="study-note-selected-text">
              {{ noteComposer.context.text }}
            </blockquote>
            <input
              v-model="noteComposer.title"
              type="text"
              placeholder="笔记标题"
              aria-label="笔记标题"
            />
            <textarea
              ref="noteContentInputRef"
              v-model="noteComposer.content"
              rows="7"
              placeholder="围绕这个选区写下理解、疑问或总结。"
              aria-label="笔记内容"
            />
            <div class="note-agent-compose" aria-label="Agent 辅助补充笔记">
              <div class="note-agent-compose__quick">
                <button
                  type="button"
                  :disabled="noteAgentLoading"
                  @click="askAgentToAppendNote('结合当前选区，补充一段适合写入学习笔记的解释。')">
                  Agent 补充笔记
                </button>
                <button
                  type="button"
                  :disabled="noteAgentLoading"
                  @click="askAgentToAppendNote('把当前选区整理成 3 条复习要点。')">
                  整理要点
                </button>
              </div>
              <textarea
                v-model="noteAgentPrompt"
                rows="2"
                placeholder="边问边补：例如这里为什么能推出时间复杂度？"
                aria-label="问 Agent 并追加到当前笔记"
              />
              <div class="note-agent-compose__actions">
                <span>{{ noteAgentLoading ? 'Agent 正在补充...' : '回答会先进入候选区' }}</span>
                <button
                  type="button"
                  :disabled="noteAgentLoading || !noteAgentPrompt.trim()"
                  @click="askAgentToAppendNote(noteAgentPrompt)">
                  问 AI 生成候选
                </button>
              </div>
            </div>
            <div class="study-note-composer__actions">
              <button type="button" @click="cancelStudyNoteComposer">取消</button>
              <button type="submit" class="primary-action">
                {{ noteComposer.status === 'draft' ? '保存草稿' : '保存笔记' }}
              </button>
            </div>
          </form>

          <div v-else-if="activeBlockNotes.length === 0" class="study-note-empty">
            <span>当前段落还没有笔记</span>
            <small>选中文本或点击新建，把理解沉淀到学习资产管道。</small>
          </div>

          <div v-else class="study-note-list">
            <article
              v-for="note in activeBlockNotes"
              :key="note.id"
              class="study-note-card"
              :class="[`study-note-card--${note.status}`, { active: note.id === activeNoteId }]">
              <div class="study-note-card__meta">
                <span>{{ note.source === 'agent' ? 'Agent' : '我' }}</span>
                <small>Page {{ note.pageNumber }}</small>
                <small v-if="note.bookmarkId">{{ resolveNoteBookmarkLabel(note) }}</small>
                <mark>{{ noteStatusLabels[note.status] }}</mark>
              </div>
              <h3>{{ note.title }}</h3>
              <p>{{ note.content }}</p>
              <blockquote v-if="note.selectedText">{{ note.selectedText }}</blockquote>
              <div v-if="note.tags.length" class="study-note-tags">
                <span v-for="tag in note.tags" :key="tag">{{ tag }}</span>
              </div>
              <div class="study-note-card__actions">
                <button type="button" @click="jumpToStudyNote(note)">定位</button>
                <button type="button" @click="editStudyNote(note)">编辑</button>
                <button v-if="note.status === 'draft'" type="button" @click="updateStudyNoteStatus(note.id, 'saved')">确认沉淀</button>
                <button v-if="note.status !== 'reviewing'" type="button" @click="updateStudyNoteStatus(note.id, 'reviewing')">加入复习</button>
                <button v-else type="button" @click="updateStudyNoteStatus(note.id, 'mastered')">标记掌握</button>
              </div>
            </article>
          </div>
        </section>

        <section class="agent-context">
          <p class="answer-label">上下文</p>
          <strong>{{ selectedPdfText ? '当前选区' : '当前页 / 当前段落' }}</strong>
          <small v-if="selectedPdfContext">
            Page {{ selectedPdfContext.pageNumber }} · {{ selectedPdfContext.elementId }}
            <template v-if="selectedPdfSelectionType === 'region'"> · region</template>
          </small>
          <blockquote>{{ agentContextText }}</blockquote>
        </section>

        <section class="agent-answer agent-answer--ide">
          <p class="answer-label">推荐译文</p>
          <p>{{ activeInsight.translation }}</p>
        </section>

        <section class="agent-toolbar" aria-label="Agent 快捷操作">
          <button type="button" @click="askAgent('翻译并解释当前段落')">解释段落</button>
          <button type="button" @click="askAgent('拆解当前段落长难句')">长难句</button>
          <button type="button" @click="askAgent('提取当前段落短语和生词')">提取表达</button>
          <button type="button" @click="startNoteFromActiveBlock">记笔记</button>
        </section>

        <section class="agent-card agent-card--ide">
          <p class="answer-label">学习资产候选</p>
          <div class="agent-chip-list">
            <span v-for="phrase in activeInsight.phrases" :key="phrase.text">
              {{ phrase.text }}
            </span>
            <span v-for="word in activeInsight.vocabulary" :key="word.text">
              {{ word.text }}
            </span>
            <span v-for="grammar in activeInsight.grammarPoints" :key="grammar.text">
              {{ grammar.text }}
            </span>
          </div>
        </section>

        <section class="agent-conversation agent-conversation--ide">
          <article v-for="message in agentMessages" :key="message.id" :class="`message message--${message.role}`">
            <strong>{{ message.role === 'assistant' ? 'Agent' : '我' }}</strong>
            <p>{{ message.content }}</p>
            <button
              v-if="message.role === 'assistant' && message.id !== 'agent-welcome'"
              type="button"
              class="message-save-note"
              @click="startNoteFromAgentMessage(message)">
              保存为笔记
            </button>
            <button
              v-if="message.role === 'assistant' && message.id !== 'agent-welcome' && noteComposer.mode !== 'idle'"
              type="button"
              class="message-append-note"
              @click="appendAgentAnswerToNoteComposer(message.content)">
              追加到当前笔记
            </button>
            <div v-if="message.citations?.length" class="message-citations" aria-label="引用来源">
              <button
                v-for="citation in message.citations"
                :key="`${citation.chunkId}-${citation.elementId || citation.pageNumber}`"
                type="button"
                @click="jumpToCitation(citation)">
                引用 Page {{ citation.pageNumber || '?' }} · {{ citation.elementId || citation.chunkId }}
              </button>
            </div>
          </article>
        </section>

        <form class="agent-command agent-command--ide" @submit.prevent="submitAgentQuestion">
          <textarea
            v-model="agentPrompt"
            rows="4"
            placeholder="围绕当前段落提问，或让 Agent 整理成笔记..."
          />
          <div class="command-actions">
            <button type="button" @click="startNoteFromActiveBlock">新建笔记</button>
            <button type="button" @click="askAgent('整理当前段落为笔记草稿')">Agent 整理</button>
            <button type="submit" class="primary-action" :disabled="agentAnswerLoading">
              {{ agentAnswerLoading ? '检索中...' : '发送' }}
            </button>
          </div>
        </form>
        </template>
      </aside>
    </main>

    <section v-else class="missing-state">
      <p>{{ workspaceLoading ? 'LOADING TRANSLATION' : 'TRANSLATION NOT FOUND' }}</p>
      <h1>{{ workspaceLoading ? '正在恢复精读材料' : '没有找到这篇精读材料' }}</h1>
      <span>{{ workspaceLoading ? '正在从后端知识底座读取文档结构和学习上下文。' : workspaceLoadError || '可能是知识快照不存在，或者链接里的翻译 ID 不存在。' }}</span>
      <button type="button" @click="goBackToHub">返回</button>
    </section>

    <footer v-if="readingDocument" class="workspace-status-bar workspace-status-bar--ide" aria-label="学习工作台状态">
      <span>{{ totalStudyNoteCount }} 条笔记</span>
      <span>{{ userBookmarks.length }} 个书签</span>
      <span>{{ workspaceStateSaving ? '同步中' : '已同步' }}</span>
      <span>P{{ currentPdfPage }}</span>
      <span>{{ modeLabels[activeMode] }}</span>
      <button type="button" @click="selectSidePanel('assets')">整理队列</button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  answerTranslationDocumentQuestion,
  downloadTranslationDocumentWithBookmarks,
  getTranslationDocumentFileUrl,
  getTranslationDocumentKnowledge,
  saveTranslationDocumentWorkspaceState,
  type TranslationDocumentStudyNoteDto,
  type TranslationDocumentUserBookmarkDto,
  type TranslationDocumentWorkspaceStateDto,
  type TranslationSourceCitationDto,
} from '@/api/translation'
import PdfLearningCanvas from '@/components/translation/PdfLearningCanvas.vue'
import { showToast } from '@/utils/toast'
import {
  buildDocumentSelectionContext,
  buildIntensiveReadingDocument,
  createTranslationWorkspaceDraftFromParsedDocument,
  loadTranslationWorkspaceDraft,
  type DocumentBlock,
  type DocumentOutlineItem,
  type DocumentSelectionContext,
  type IntensiveReadingDocument,
  type IntensiveAgentMode,
  type TranslationWorkspaceDraft,
} from './translationWorkspaceData'

type AgentMessageRole = 'user' | 'assistant'
type WorkspaceResizeTarget = 'outline' | 'agent'
type StudyNoteStatus = 'draft' | 'saved' | 'reviewing' | 'mastered'
type StudyNoteSource = 'manual' | 'agent'
type OutlineFilterScope = 'all' | 'current' | 'notes'
type WorkspaceSidePanel = 'outline' | 'bookmarks' | 'notes' | 'assets' | 'search'
type WorkspaceTabKind = 'pdf' | 'anchor-note' | 'standalone-note' | 'topic'
type AgentPanelMode = 'agent' | 'note-workbench' | 'note-assistant' | 'topic-organizer'

interface LocalAgentMessage {
  id: string
  role: AgentMessageRole
  content: string
  sourceContext?: DocumentSelectionContext | null
  citations?: TranslationSourceCitationDto[]
}

interface StudyNote {
  id: string
  documentId: string
  bookmarkId: string | null
  pageNumber: number
  blockId: string
  elementId: string
  bbox: string | null
  selectedText: string
  title: string
  content: string
  source: StudyNoteSource
  status: StudyNoteStatus
  tags: string[]
  createdAt: string
  updatedAt: string
}

interface StudyNoteComposerState {
  mode: 'idle' | 'create' | 'edit'
  noteId: string | null
  bookmarkId: string | null
  source: StudyNoteSource
  status: StudyNoteStatus
  title: string
  content: string
  context: DocumentSelectionContext | null
}

interface StudyAssetPipelineColumn {
  id: 'draft' | 'saved' | 'reviewing'
  label: string
  description: string
  tone: string
  notes: StudyNote[]
}

interface OutlineFilterScopeOption {
  id: OutlineFilterScope
  label: string
  count: number
}

interface WorkspaceSidePanelOption {
  id: WorkspaceSidePanel
  label: string
  icon: string
  count: number
}

interface WorkspaceTab {
  id: string
  kind: WorkspaceTabKind
  title: string
  subtitle?: string
  documentId?: string
  noteId?: string
  topicId?: string
  dirty?: boolean
}

type ProjectTreeFolderId =
  | 'sources'
  | 'notes'
  | 'anchor-notes'
  | 'assets'
  | 'review'
  | 'question-bank'
  | 'mistakes'
  | 'prompts'
  | 'pdf-outline'

type ProjectTreeResourceKind =
  | 'pdf'
  | 'note'
  | 'anchor-note'
  | 'asset'
  | 'review'
  | 'question-bank'
  | 'mistake'
  | 'prompt'

interface ProjectTreeResource {
  id: string
  kind: ProjectTreeResourceKind
  title: string
  subtitle?: string
  noteId?: string
  tabId?: string
  count?: number
}

interface ProjectTreeFolder {
  id: ProjectTreeFolderId
  label: string
  badge: string
  resources: ProjectTreeResource[]
  emptyText: string
}

interface DisplayOutlineItem extends DocumentOutlineItem {
  displayLevel: number
  hasChildren?: boolean
  syntheticRoot?: boolean
}

interface UserBookmark {
  id: string
  title: string
  pageNumber: number
  level: number
  elementId?: string | null
  bbox?: string | null
  source: 'user_bookmark'
  parentId?: string | null
  order: number
  createdAt: string
  updatedAt: string
}

type PdfSelectionType = 'text' | 'region'

interface PdfSelectionPayload {
  text: string
  documentId: string
  pageNumber: number
  blockId: string | null
  elementId: string | null
  bbox: string | null
  selectionType?: PdfSelectionType
}

interface PdfSourceHighlight {
  pageNumber: number
  bbox: string | null
  label: string
  text?: string | null
}

interface PdfNoteAnchor {
  id: string
  pageNumber: number
  title: string
  excerpt?: string
  bbox?: string | null
  status?: string
  active?: boolean
}

const route = useRoute()
const router = useRouter()
const activeMode = ref<IntensiveAgentMode>('immersive')
const activeBlockId = ref('')
const documentView = ref<'text' | 'pdf-canvas'>('text')
const selectedPdfText = ref('')
const selectedPdfContext = ref<DocumentSelectionContext | null>(null)
const selectedPdfSelectionType = ref<PdfSelectionType | null>(null)
const pdfSourceHighlight = ref<PdfSourceHighlight | null>(null)
const targetPdfPage = ref(1)
const currentPdfPage = ref(1)
const workspaceShellRef = ref<HTMLElement | null>(null)
const readingDocument = ref<IntensiveReadingDocument | null>(null)
const workspaceLoading = ref(false)
const workspaceLoadError = ref('')
const outlineColumnWidth = ref(280)
const agentColumnWidth = ref(430)
const activeResizeTarget = ref<WorkspaceResizeTarget | null>(null)
const isOutlineCollapsed = ref(false)
const isAgentCollapsed = ref(false)
const activeSidePanel = ref<WorkspaceSidePanel>('outline')
const collapsedProjectTreeFolderIds = ref<Set<ProjectTreeFolderId>>(new Set())
const collapsedOutlineItemIds = ref<Set<string>>(new Set())
const activeOutlineItemId = ref<string | null>(null)
const outlineSearchQuery = ref('')
const outlineFilterScope = ref<OutlineFilterScope>('all')
const agentPrompt = ref('')
const agentAnswerLoading = ref(false)
const userBookmarks = ref<UserBookmark[]>([])
const studyNotes = ref<StudyNote[]>([])
const activeNoteId = ref<string | null>(null)
const noteComposer = ref<StudyNoteComposerState>(createEmptyNoteComposer())
const noteContentInputRef = ref<HTMLTextAreaElement | null>(null)
const noteAgentPrompt = ref('')
const noteAgentLoading = ref(false)
const aiCandidateContent = ref('')
const workspaceTabs = ref<WorkspaceTab[]>([])
const activeWorkspaceTabId = ref<string | null>(null)
const agentPanelMode = ref<AgentPanelMode>('agent')
const workspaceStateSaving = ref(false)
const agentMessages = ref<LocalAgentMessage[]>([
  {
    id: 'agent-welcome',
    role: 'assistant',
    content: '选择左侧段落后，我会围绕当前段落解释译文、短语、语法，并帮你整理成学习资产。',
  },
])

const modeLabels: Record<IntensiveAgentMode, string> = {
  immersive: '沉浸精读',
  foreign: '外刊精读',
  exam: '考试精读',
  technical: '技术文档',
}

const noteStatusLabels: Record<StudyNoteStatus, string> = {
  draft: '待整理',
  saved: '已沉淀',
  reviewing: '复习中',
  mastered: '已掌握',
}

const outlineColumnMinWidth = 220
const outlineColumnMaxWidth = 440
const agentColumnMinWidth = 340
const agentColumnMaxWidth = 620
const centerColumnMinWidth = 560
const resizerColumnsWidth = 16
let workspaceStateRestoring = false
let workspaceStateSaveTimer: ReturnType<typeof setTimeout> | null = null
let workspaceStateSaveErrorShown = false

const activeBlock = computed(() => {
  const document = readingDocument.value
  if (!document) return null
  if (!activeBlockId.value) {
    activeBlockId.value = document.blocks[0]?.id ?? ''
  }
  return document.blocks.find((block) => block.id === activeBlockId.value) ?? document.blocks[0] ?? null
})

const activeInsight = computed(() => {
  const document = readingDocument.value
  const block = activeBlock.value
  if (!document || !block) return null
  return document.insights.find((insight) => insight.blockId === block.id) ?? null
})

const agentContextText = computed(() => {
  return selectedPdfContext.value?.text || selectedPdfText.value || activeBlock.value?.text || ''
})

const outlineItems = computed<DocumentOutlineItem[]>(() => {
  return readingDocument.value?.outline ?? []
})

const userBookmarkOutlineItems = computed<DocumentOutlineItem[]>(() => {
  return userBookmarks.value.map((bookmark) => ({
    id: bookmark.id,
    title: bookmark.title,
    level: bookmark.level,
    pageNumber: bookmark.pageNumber,
    elementId: bookmark.elementId ?? null,
    bbox: bookmark.bbox ?? null,
    source: bookmark.source,
    confidence: null,
  }))
})

const displayOutlineItems = computed<DisplayOutlineItem[]>(() => {
  const document = readingDocument.value
  if (!document || (outlineItems.value.length === 0 && userBookmarkOutlineItems.value.length === 0)) return []

  const firstPage = Math.max(1, Math.min(...document.blocks.map((block) => block.pageNumber || 1)))
  const root: DisplayOutlineItem = {
    id: 'outline-document-root',
    title: document.title,
    level: 1,
    displayLevel: 1,
    pageNumber: firstPage,
    elementId: document.blocks[0]?.elementId ?? document.blocks[0]?.id ?? null,
    bbox: document.blocks[0]?.bbox ?? null,
    source: 'document_root',
    confidence: null,
    syntheticRoot: true,
  }

  const sourceItems = outlineItems.value.filter((item, index) => !isDuplicateDocumentRoot(item, document.title, index))
  let lastNumberedLevel = 2
  const children = sourceItems.map<DisplayOutlineItem>((item) => {
    const explicitLevel = inferDisplayOutlineLevel(item.title)
    const displayLevel = explicitLevel ?? Math.min(6, lastNumberedLevel + 1)
    if (explicitLevel) lastNumberedLevel = displayLevel
    return {
      ...item,
      level: displayLevel,
      displayLevel,
    }
  })

  const bookmarkChildren = userBookmarkOutlineItems.value.map<DisplayOutlineItem>((item) => ({
    ...item,
    level: 3,
    displayLevel: 3,
  }))
  const bookmarkRoot: DisplayOutlineItem[] = bookmarkChildren.length > 0
    ? [{
        id: 'outline-user-bookmark-root',
        title: '我的书签',
        level: 2,
        displayLevel: 2,
        pageNumber: bookmarkChildren[0]?.pageNumber ?? currentPdfPage.value,
        elementId: null,
        bbox: null,
        source: 'user_bookmark_root',
        confidence: null,
        syntheticRoot: true,
      }]
    : []

  return [root, ...children, ...bookmarkRoot, ...bookmarkChildren]
})

const outlineTreeItems = computed<DisplayOutlineItem[]>(() => {
  const items = displayOutlineItems.value
  return items.map((item, index) => ({
    ...item,
    hasChildren: items[index + 1]?.displayLevel > item.displayLevel,
  }))
})

const visibleOutlineItems = computed<DisplayOutlineItem[]>(() => {
  const query = normalizeOutlineQuery(outlineSearchQuery.value)
  const applyCollapse = outlineFilterScope.value === 'all' && !query
  if (!applyCollapse) return outlineTreeItems.value

  const collapsedAncestorLevels: number[] = []
  return outlineTreeItems.value.filter((item) => {
    while (collapsedAncestorLevels.length > 0 && collapsedAncestorLevels[collapsedAncestorLevels.length - 1] >= item.displayLevel) {
      collapsedAncestorLevels.pop()
    }

    const hiddenByAncestor = collapsedAncestorLevels.length > 0
    if (!hiddenByAncestor && item.hasChildren && isOutlineNodeCollapsed(item)) {
      collapsedAncestorLevels.push(item.displayLevel)
    }
    return !hiddenByAncestor
  })
})

const outlinePageItems = computed<number[]>(() => {
  const document = readingDocument.value
  if (!document) return []
  const pages = new Set<number>()
  for (const block of document.blocks) {
    pages.add(block.pageNumber || 1)
  }
  return Array.from(pages).sort((left, right) => left - right)
})

const noteCountByElementId = computed(() => {
  const counts = new Map<string, number>()
  for (const note of studyNotes.value) {
    if (!note.elementId) continue
    counts.set(note.elementId, (counts.get(note.elementId) ?? 0) + 1)
  }
  return counts
})

const noteCountByBookmarkId = computed(() => {
  const counts = new Map<string, number>()
  for (const note of studyNotes.value) {
    if (!note.bookmarkId) continue
    counts.set(note.bookmarkId, (counts.get(note.bookmarkId) ?? 0) + 1)
  }
  return counts
})

const noteCountByPage = computed(() => {
  const counts = new Map<number, number>()
  for (const note of studyNotes.value) {
    counts.set(note.pageNumber, (counts.get(note.pageNumber) ?? 0) + 1)
  }
  return counts
})

const outlineSummary = computed(() => {
  const total = outlineTreeItems.value.length || outlinePageItems.value.length
  const noteTotal = studyNotes.value.length
  return `Page ${currentPdfPage.value} · ${total} 个定位 · ${noteTotal} 条笔记`
})

const outlineFilterScopes = computed<OutlineFilterScopeOption[]>(() => {
  const itemSource = outlineTreeItems.value
  const total = itemSource.length || outlinePageItems.value.length
  const currentCount = itemSource.length
    ? itemSource.filter((item) => item.pageNumber === currentPdfPage.value).length
    : outlinePageItems.value.includes(currentPdfPage.value) ? 1 : 0
  const notesCount = itemSource.length
    ? itemSource.filter((item) => getOutlineItemNoteCount(item) > 0).length
    : outlinePageItems.value.filter((page) => getPageNoteCount(page) > 0).length
  return [
    { id: 'all', label: '全部', count: total },
    { id: 'current', label: '当前页', count: currentCount },
    { id: 'notes', label: '有笔记', count: notesCount },
  ]
})

const filteredOutlineItems = computed<DisplayOutlineItem[]>(() => {
  return visibleOutlineItems.value
    .filter((item) => matchesOutlineScope(item))
    .filter((item) => matchesOutlineSearch(item))
})

const filteredOutlinePageItems = computed<number[]>(() => {
  return outlinePageItems.value
    .filter((page) => matchesPageScope(page))
    .filter((page) => matchesPageSearch(page))
})

const activeBlockNotes = computed(() => {
  return studyNotes.value.filter(isStudyNoteInActiveContext)
})

const activeUserBookmark = computed(() => {
  const activeId = activeOutlineItemId.value
  if (!activeId) return null
  return userBookmarks.value.find((bookmark) => bookmark.id === activeId) ?? null
})

const noteAnchors = computed<PdfNoteAnchor[]>(() => {
  return studyNotes.value.map((note) => ({
    id: note.id,
    pageNumber: note.pageNumber,
    title: note.title,
    excerpt: note.selectedText || note.content,
    bbox: note.bbox,
    status: note.status,
    active: note.id === activeNoteId.value,
  }))
})

const studyAssetPipeline = computed<StudyAssetPipelineColumn[]>(() => [
  {
    id: 'draft',
    label: '待整理',
    description: 'Agent 生成或还没确认的笔记草稿',
    tone: 'warm',
    notes: studyNotes.value.filter((note) => note.status === 'draft'),
  },
  {
    id: 'saved',
    label: '已沉淀',
    description: '用户主动保存并确认的学习笔记',
    tone: 'green',
    notes: studyNotes.value.filter((note) => note.status === 'saved' || note.status === 'mastered'),
  },
  {
    id: 'reviewing',
    label: '复习中',
    description: '用户手动加入复习的笔记卡',
    tone: 'blue',
    notes: studyNotes.value.filter((note) => note.status === 'reviewing'),
  },
])

const totalStudyNoteCount = computed(() => studyNotes.value.length)

const projectTreeSummary = computed(() => {
  const outlineTotal = outlineTreeItems.value.length || outlinePageItems.value.length
  return `${outlineTotal} 个定位 · ${studyNotes.value.length} 条笔记`
})

const projectTreeFolders = computed<ProjectTreeFolder[]>(() => {
  const document = readingDocument.value
  const anchorNotes = studyNotes.value.filter((note) => note.selectedText || note.bbox || note.bookmarkId)
  const standaloneNotes = studyNotes.value.filter((note) => !note.selectedText && !note.bbox && !note.bookmarkId)
  const reviewingNotes = studyNotes.value.filter((note) => note.status === 'reviewing')
  const assetResources = buildProjectTreeAssetResources()

  return [
    {
      id: 'sources',
      label: '资料',
      badge: document ? '1' : '0',
      resources: document
        ? [{
            id: `project-pdf-${document.id}`,
            kind: 'pdf',
            title: document.title,
            subtitle: `PDF · ${document.pageCount} 页`,
            tabId: `pdf-${document.id}`,
          }]
        : [],
      emptyText: '导入 PDF 建立学习项目',
    },
    {
      id: 'notes',
      label: '笔记',
      badge: String(standaloneNotes.length),
      resources: standaloneNotes.map((note) => buildProjectTreeNoteResource(note, 'note')),
      emptyText: '新建章节笔记',
    },
    {
      id: 'anchor-notes',
      label: '锚点笔记',
      badge: String(anchorNotes.length),
      resources: anchorNotes.map((note) => buildProjectTreeNoteResource(note, 'anchor-note')),
      emptyText: '从 PDF 选区创建锚点笔记',
    },
    {
      id: 'assets',
      label: '学习资产',
      badge: String(assetResources.length),
      resources: assetResources,
      emptyText: '让 Agent 整理当前段落',
    },
    {
      id: 'review',
      label: '复习队列',
      badge: String(reviewingNotes.length),
      resources: reviewingNotes.map((note) => buildProjectTreeNoteResource(note, 'review')),
      emptyText: '把笔记加入复习队列',
    },
    {
      id: 'question-bank',
      label: '题库',
      badge: '0',
      resources: [],
      emptyText: '新建专题后生成题目',
    },
    {
      id: 'mistakes',
      label: '错题本',
      badge: '0',
      resources: [],
      emptyText: '还没有错题记录',
    },
    {
      id: 'prompts',
      label: '提示词',
      badge: '0',
      resources: [],
      emptyText: '保存常用 Agent 提示词',
    },
  ]
})

const sidePanelOptions = computed<WorkspaceSidePanelOption[]>(() => [
  { id: 'outline', label: '目录', icon: '目', count: outlineTreeItems.value.length || outlinePageItems.value.length },
  { id: 'bookmarks', label: '书签', icon: '签', count: userBookmarks.value.length },
  { id: 'notes', label: '笔记', icon: '记', count: studyNotes.value.length },
  { id: 'assets', label: '资产', icon: '资', count: totalStudyNoteCount.value },
  { id: 'search', label: '搜索', icon: '搜', count: filteredOutlineItems.value.length || filteredOutlinePageItems.value.length },
])

const sidePanelSummary = computed(() => {
  if (activeSidePanel.value === 'outline') return outlineSummary.value
  if (activeSidePanel.value === 'bookmarks') return `${userBookmarks.value.length} 个书签 · Page ${currentPdfPage.value}`
  if (activeSidePanel.value === 'notes') return `${studyNotes.value.length} 条笔记 · ${activeBlockNotes.value.length} 条在当前上下文`
  if (activeSidePanel.value === 'assets') return `${totalStudyNoteCount.value} 条笔记 · ${workspaceStateSaving.value ? '同步中' : '已同步'}`
  return `${filteredOutlineItems.value.length || filteredOutlinePageItems.value.length} 个匹配结果`
})

const noteComposerContextLabel = computed(() => {
  const context = noteComposer.value.context
  if (!context) return '未选择来源'
  return `Page ${context.pageNumber} · ${context.elementId || context.blockId}`
})

const activeWorkspaceTab = computed(() => {
  return workspaceTabs.value.find((tab) => tab.id === activeWorkspaceTabId.value) ?? workspaceTabs.value[0] ?? null
})

const activeWorkspaceTabKind = computed<WorkspaceTabKind>(() => activeWorkspaceTab.value?.kind ?? 'pdf')

watch(readingDocument, (document) => {
  if (!document) {
    workspaceTabs.value = []
    activeWorkspaceTabId.value = null
    return
  }

  const pdfTabId = `pdf-${document.id}`
  const existingTabs = workspaceTabs.value.filter((tab) => tab.id !== pdfTabId)
  const pdfTab: WorkspaceTab = {
    id: pdfTabId,
    kind: 'pdf',
    title: document.title,
    subtitle: 'PDF',
    documentId: document.id,
  }
  workspaceTabs.value = [pdfTab, ...existingTabs]
  if (!activeWorkspaceTabId.value) activeWorkspaceTabId.value = pdfTabId

  if (document?.sourceType === 'pdf') {
    documentView.value = 'pdf-canvas'
  }
  if (workspaceStateRestoring) return
  syncDocumentDefaultPage()
}, { immediate: true })

watch(
  () => String(route.params.id ?? ''),
  (id) => {
    void restoreWorkspaceDocument(id)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopWorkspaceResize()
  if (workspaceStateSaveTimer) {
    clearTimeout(workspaceStateSaveTimer)
    workspaceStateSaveTimer = null
    void persistWorkspaceState()
  }
})

async function goBackToHub() {
  await flushWorkspaceStateSave()
  void router.push('/app/translation')
}

async function completeLearningSession() {
  await flushWorkspaceStateSave()
  void router.push('/app/translation')
}

function activateWorkspaceTab(tabId: string) {
  const tab = workspaceTabs.value.find((item) => item.id === tabId)
  if (!tab) return
  activeWorkspaceTabId.value = tab.id
  if (tab.kind === 'pdf') {
    agentPanelMode.value = 'agent'
    documentView.value = 'pdf-canvas'
  } else if (tab.kind === 'anchor-note' || tab.kind === 'standalone-note') {
    agentPanelMode.value = 'note-assistant'
  } else {
    agentPanelMode.value = 'topic-organizer'
  }
}

function closeWorkspaceTab(tabId: string) {
  const nextTabs = workspaceTabs.value.filter((tab) => tab.id !== tabId)
  workspaceTabs.value = nextTabs
  if (activeWorkspaceTabId.value !== tabId) return
  activeWorkspaceTabId.value = nextTabs[0]?.id ?? null
  if (!activeWorkspaceTabId.value) agentPanelMode.value = 'agent'
}

function openStandaloneNoteTab() {
  const tabId = `standalone-note-${Date.now()}`
  workspaceTabs.value.push({
    id: tabId,
    kind: 'standalone-note',
    title: 'Untitled Note',
    subtitle: '独立笔记',
    dirty: true,
  })
  activateWorkspaceTab(tabId)
}

function openTopicTab(title = '排序算法') {
  const tabId = `topic-${Date.now()}`
  workspaceTabs.value.push({
    id: tabId,
    kind: 'topic',
    title,
    subtitle: '专题',
  })
  activateWorkspaceTab(tabId)
}

function openImportPdfEntry() {
  showToast('PDF 导入入口将在下一阶段接入当前上传流程', 'info')
}

function buildProjectTreeNoteResource(note: StudyNote, kind: Extract<ProjectTreeResourceKind, 'note' | 'anchor-note' | 'review'>): ProjectTreeResource {
  return {
    id: `project-${kind}-${note.id}`,
    kind,
    title: note.title,
    subtitle: `Page ${note.pageNumber} · ${noteStatusLabels[note.status]}`,
    noteId: note.id,
    count: note.tags.length || undefined,
  }
}

function buildProjectTreeAssetResources(): ProjectTreeResource[] {
  const assets = new Map<string, ProjectTreeResource>()

  for (const note of studyNotes.value) {
    for (const tag of note.tags) {
      const key = tag.trim()
      if (!key) continue
      const existing = assets.get(key)
      assets.set(key, {
        id: `project-asset-${normalizeProjectTreeId(key)}`,
        kind: 'asset',
        title: key,
        subtitle: existing ? '多条笔记关联' : `来自 ${note.title}`,
        count: (existing?.count ?? 0) + 1,
      })
    }
  }

  if (assets.size === 0 && activeInsight.value) {
    for (const candidate of [
      ...activeInsight.value.phrases,
      ...activeInsight.value.vocabulary,
      ...activeInsight.value.grammarPoints,
    ].slice(0, 6)) {
      const title = candidate.text.trim()
      if (!title) continue
      assets.set(title, {
        id: `project-asset-candidate-${normalizeProjectTreeId(title)}`,
        kind: 'asset',
        title,
        subtitle: '当前段落候选',
      })
    }
  }

  return Array.from(assets.values()).slice(0, 12)
}

function openProjectTreeResource(resource: ProjectTreeResource) {
  if (resource.kind === 'pdf') {
    const pdfTabId = resource.tabId ?? (readingDocument.value ? `pdf-${readingDocument.value.id}` : null)
    if (pdfTabId) activateWorkspaceTab(pdfTabId)
    documentView.value = 'pdf-canvas'
    agentPanelMode.value = 'agent'
    return
  }

  if ((resource.kind === 'anchor-note' || resource.kind === 'note' || resource.kind === 'review') && resource.noteId) {
    openStudyNote(resource.noteId)
    return
  }

  if (resource.kind === 'asset') {
    openTopicTab(resource.title)
    return
  }

  if (resource.kind === 'question-bank') {
    openTopicTab('题库')
    return
  }

  if (resource.kind === 'mistake') {
    openTopicTab('错题本')
    return
  }

  if (resource.kind === 'prompt') {
    openTopicTab('提示词')
  }
}

function handleProjectTreeFolderEmptyAction(folder: ProjectTreeFolder) {
  if (folder.id === 'sources') {
    openImportPdfEntry()
    return
  }
  if (folder.id === 'notes') {
    openStandaloneNoteTab()
    return
  }
  if (folder.id === 'anchor-notes') {
    startNoteFromActiveBlock()
    return
  }
  if (folder.id === 'assets') {
    askAgent('整理当前段落为笔记草稿')
    return
  }
  openTopicTab(folder.label)
}

function isProjectTreeFolderCollapsed(folderId: ProjectTreeFolderId) {
  return collapsedProjectTreeFolderIds.value.has(folderId)
}

function toggleProjectTreeFolder(folderId: ProjectTreeFolderId) {
  const nextCollapsedIds = new Set(collapsedProjectTreeFolderIds.value)
  if (nextCollapsedIds.has(folderId)) {
    nextCollapsedIds.delete(folderId)
  } else {
    nextCollapsedIds.add(folderId)
  }
  collapsedProjectTreeFolderIds.value = nextCollapsedIds
}

function getProjectTreeResourceIcon(kind: ProjectTreeResourceKind) {
  const icons: Record<ProjectTreeResourceKind, string> = {
    pdf: 'PDF',
    note: 'MD',
    'anchor-note': '锚',
    asset: '资',
    review: '复',
    'question-bank': '题',
    mistake: '错',
    prompt: '提',
  }
  return icons[kind]
}

function normalizeProjectTreeId(value: string) {
  return value.trim().toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9\u4e00-\u9fa5-]/g, '')
}

async function flushWorkspaceStateSave() {
  if (workspaceStateSaveTimer) {
    clearTimeout(workspaceStateSaveTimer)
    workspaceStateSaveTimer = null
  }
  await persistWorkspaceState()
}

async function restoreWorkspaceDocument(id: string) {
  workspaceStateRestoring = true
  readingDocument.value = null
  activeBlockId.value = ''
  activeOutlineItemId.value = null
  workspaceLoadError.value = ''
  studyNotes.value = []
  userBookmarks.value = []
  activeNoteId.value = null
  targetPdfPage.value = 1
  currentPdfPage.value = 1
  collapsedOutlineItemIds.value = new Set()
  noteComposer.value = createEmptyNoteComposer()
  if (!id) {
    workspaceLoadError.value = '缺少翻译 ID。'
    workspaceStateRestoring = false
    return
  }

  workspaceLoading.value = true
  try {
    const persisted = await getTranslationDocumentKnowledge(id)
    const localDraft = loadLocalWorkspaceDraft(id)
    const draft = createTranslationWorkspaceDraftFromParsedDocument(
      {
        mode: localDraft?.mode ?? restoreTranslationMode(),
        pdfPreviewUrl: resolvePersistedPdfPreviewUrl(id, persisted.fileUrl, localDraft?.pdfPreviewUrl),
      },
      persisted,
    )
    activeMode.value = draft.mode
    readingDocument.value = buildIntensiveReadingDocument(applyLocalDraftDisplayOverrides(draft, localDraft))
    restoreWorkspaceState(persisted.workspaceState ?? null)
    focusRouteStudyNote()
  } catch {
    const localDraft = loadLocalWorkspaceDraft(id)
    if (localDraft) {
      activeMode.value = localDraft.mode
      readingDocument.value = buildIntensiveReadingDocument({
        ...localDraft,
        pdfPreviewUrl: localDraft.pdfPreviewUrl || getTranslationDocumentFileUrl(id),
      })
      restoreWorkspaceState(localDraft.workspaceState ?? null)
      focusRouteStudyNote()
      workspaceLoadError.value = ''
      return
    }
    workspaceLoadError.value = '后端知识快照不存在，且没有可兼容恢复的本地草稿。'
  } finally {
    workspaceStateRestoring = false
    workspaceLoading.value = false
  }
}

function resolvePersistedPdfPreviewUrl(
  documentId: string,
  persistedFileUrl?: string | null,
  localFileUrl?: string,
) {
  return persistedFileUrl || localFileUrl || getTranslationDocumentFileUrl(documentId)
}

function loadLocalWorkspaceDraft(id: string): TranslationWorkspaceDraft | null {
  if (typeof window === 'undefined') return null
  return loadTranslationWorkspaceDraft(window.localStorage, id)
}

function restoreTranslationMode() {
  return activeMode.value === 'exam' ? 'exam' : 'immersive'
}

function syncDocumentDefaultPage() {
  targetPdfPage.value = activeBlock.value?.pageNumber || 1
  currentPdfPage.value = targetPdfPage.value
}

function syncActiveBlockToPdfPage(page: number) {
  const document = readingDocument.value
  if (!document) return
  const pageBlock = document.blocks.find((block) => (block.pageNumber || 1) === page)
  if (pageBlock) {
    activeBlockId.value = pageBlock.id
  }
}

function applyLocalDraftDisplayOverrides(
  draft: TranslationWorkspaceDraft,
  localDraft: TranslationWorkspaceDraft | null,
): TranslationWorkspaceDraft {
  if (!localDraft?.title) return draft
  return {
    ...draft,
    title: localDraft.title,
  }
}

function focusRouteStudyNote() {
  const noteId = parseRouteNoteId(route.query.noteId)
  if (!noteId) return
  openStudyNote(noteId)
}

function parseRouteNoteId(value: unknown): string | null {
  if (typeof value === 'string') return value.trim() || null
  if (Array.isArray(value)) {
    const first = value.find((item): item is string => typeof item === 'string' && item.trim().length > 0)
    return first?.trim() ?? null
  }
  return null
}

function restoreWorkspaceState(state: TranslationDocumentWorkspaceStateDto | null) {
  if (!state) return
  userBookmarks.value = normalizeWorkspaceBookmarks(state.userBookmarks ?? [])
  studyNotes.value = normalizeWorkspaceNotes(state.studyNotes ?? [])
  collapsedOutlineItemIds.value = new Set((state.collapsedOutlineItemIds ?? []).filter(Boolean))
  activeOutlineItemId.value = state.activeOutlineItemId ?? null
  activeNoteId.value = state.activeNoteId ?? null

  const document = readingDocument.value
  if (document && state.activeBlockId) {
    const restoredBlock = document.blocks.find((block) => block.id === state.activeBlockId || block.elementId === state.activeBlockId)
    if (restoredBlock) {
      activeBlockId.value = restoredBlock.id
    }
  }

  const restoredPage = normalizePageNumber(state.currentPage ?? currentPdfPage.value)
  if (document) {
    const restoredBlock = document.blocks.find((block) => block.id === activeBlockId.value)
    if (!restoredBlock || (restoredBlock.pageNumber || 1) !== restoredPage) {
      syncActiveBlockToPdfPage(restoredPage)
    }
  }
  currentPdfPage.value = restoredPage
  targetPdfPage.value = restoredPage
}

function scheduleWorkspaceStateSave() {
  if (workspaceStateRestoring || !readingDocument.value) return
  if (workspaceStateSaveTimer) {
    clearTimeout(workspaceStateSaveTimer)
  }
  workspaceStateSaveTimer = setTimeout(() => {
    workspaceStateSaveTimer = null
    void persistWorkspaceState()
  }, 450)
}

async function persistWorkspaceState() {
  const document = readingDocument.value
  if (!document || workspaceStateRestoring) return
  workspaceStateSaving.value = true
  try {
    await saveTranslationDocumentWorkspaceState(document.id, buildWorkspaceStatePayload())
    workspaceStateSaveErrorShown = false
  } catch (error) {
    console.warn('[TranslationWorkspace] save workspace state failed', error)
    if (!workspaceStateSaveErrorShown) {
      workspaceStateSaveErrorShown = true
      showToast('学习状态暂时未同步，请稍后重试', 'error')
    }
  } finally {
    workspaceStateSaving.value = false
  }
}

function buildWorkspaceStatePayload(): TranslationDocumentWorkspaceStateDto {
  const now = new Date().toISOString()
  return {
    userBookmarks: userBookmarks.value.map(toWorkspaceBookmarkDto),
    studyNotes: studyNotes.value.map(toWorkspaceStudyNoteDto),
    collapsedOutlineItemIds: Array.from(collapsedOutlineItemIds.value),
    currentPage: currentPdfPage.value,
    activeBlockId: activeBlockId.value || null,
    activeOutlineItemId: activeOutlineItemId.value,
    activeNoteId: activeNoteId.value,
    updatedAt: now,
  }
}

function normalizeWorkspaceBookmarks(bookmarks: TranslationDocumentUserBookmarkDto[]): UserBookmark[] {
  return bookmarks
    .filter((bookmark) => bookmark && bookmark.id && bookmark.title)
    .map((bookmark, index) => ({
      id: bookmark.id,
      title: bookmark.title,
      pageNumber: normalizePageNumber(bookmark.pageNumber),
      level: normalizeBookmarkLevel(bookmark.level),
      elementId: bookmark.elementId ?? null,
      bbox: bookmark.bbox ?? null,
      source: 'user_bookmark' as const,
      parentId: bookmark.parentId ?? null,
      order: bookmark.order ?? index + 1,
      createdAt: bookmark.createdAt ?? new Date().toISOString(),
      updatedAt: bookmark.updatedAt ?? bookmark.createdAt ?? new Date().toISOString(),
    }))
}

function normalizeWorkspaceNotes(notes: TranslationDocumentStudyNoteDto[]): StudyNote[] {
  const document = readingDocument.value
  return notes
    .filter((note) => note && note.id && note.title)
    .map((note) => {
      const pageNumber = normalizePageNumber(note.pageNumber)
      const fallbackBlock = document?.blocks.find((block) => block.id === note.blockId || block.elementId === note.elementId)
        ?? document?.blocks.find((block) => (block.pageNumber || 1) === pageNumber)
        ?? document?.blocks[0]
      return {
        id: note.id,
        documentId: note.documentId || document?.id || '',
        bookmarkId: note.bookmarkId ?? null,
        pageNumber,
        blockId: note.blockId || fallbackBlock?.id || 'selection',
        elementId: note.elementId || fallbackBlock?.elementId || fallbackBlock?.id || 'selection',
        bbox: note.bbox ?? fallbackBlock?.bbox ?? null,
        selectedText: note.selectedText ?? '',
        title: note.title,
        content: note.content ?? '',
        source: normalizeStudyNoteSource(note.source),
        status: normalizeStudyNoteStatus(note.status),
        tags: Array.isArray(note.tags) ? note.tags : [],
        createdAt: note.createdAt ?? new Date().toISOString(),
        updatedAt: note.updatedAt ?? note.createdAt ?? new Date().toISOString(),
      }
    })
}

function toWorkspaceBookmarkDto(bookmark: UserBookmark): TranslationDocumentUserBookmarkDto {
  return {
    id: bookmark.id,
    title: bookmark.title,
    pageNumber: bookmark.pageNumber,
    level: bookmark.level,
    elementId: bookmark.elementId ?? null,
    bbox: bookmark.bbox ?? null,
    source: bookmark.source,
    parentId: bookmark.parentId ?? null,
    order: bookmark.order,
    createdAt: bookmark.createdAt,
    updatedAt: bookmark.updatedAt,
  }
}

function toWorkspaceStudyNoteDto(note: StudyNote): TranslationDocumentStudyNoteDto {
  return {
    id: note.id,
    documentId: note.documentId,
    bookmarkId: note.bookmarkId,
    pageNumber: note.pageNumber,
    blockId: note.blockId,
    elementId: note.elementId,
    bbox: note.bbox,
    selectedText: note.selectedText,
    title: note.title,
    content: note.content,
    source: note.source,
    status: note.status,
    tags: note.tags,
    createdAt: note.createdAt,
    updatedAt: note.updatedAt,
  }
}

function normalizeStudyNoteStatus(status: string | undefined): StudyNoteStatus {
  if (status === 'draft' || status === 'saved' || status === 'reviewing' || status === 'mastered') {
    return status
  }
  return 'saved'
}

function normalizeStudyNoteSource(source: string | undefined): StudyNoteSource {
  return source === 'agent' ? 'agent' : 'manual'
}

function normalizePageNumber(pageNumber: number | null | undefined) {
  const value = Number(pageNumber)
  return Number.isFinite(value) ? Math.max(1, Math.floor(value)) : 1
}

function normalizeBookmarkLevel(level: number | null | undefined) {
  const value = Number(level)
  return Number.isFinite(value) ? Math.max(1, Math.min(6, Math.floor(value))) : 3
}

function selectBlock(blockId: string) {
  activeBlockId.value = blockId
  activeOutlineItemId.value = null
  clearPdfSelection()
  scheduleWorkspaceStateSave()
}

function selectOutlinePage(page: number) {
  currentPdfPage.value = page
  targetPdfPage.value = page
  activeOutlineItemId.value = null
  clearPdfSelection()
  const firstBlock = readingDocument.value?.blocks.find((block) => (block.pageNumber || 1) === page)
  if (firstBlock) activeBlockId.value = firstBlock.id
  scheduleWorkspaceStateSave()
}

function selectSidePanel(panel: WorkspaceSidePanel) {
  activeSidePanel.value = panel
  if (isOutlineCollapsed.value) {
    isOutlineCollapsed.value = false
  }
}

function jumpToUserBookmark(bookmark: UserBookmark) {
  activeOutlineItemId.value = bookmark.id
  targetPdfPage.value = bookmark.pageNumber
  currentPdfPage.value = bookmark.pageNumber
  documentView.value = 'pdf-canvas'
  if (bookmark.elementId) {
    const block = readingDocument.value?.blocks.find((item) => item.id === bookmark.elementId || item.elementId === bookmark.elementId)
    if (block) activeBlockId.value = block.id
  } else {
    syncActiveBlockToPdfPage(bookmark.pageNumber)
  }
  clearPdfSelection()
  scheduleWorkspaceStateSave()
}

function selectOutlineItem(item: DocumentOutlineItem) {
  const page = item.pageNumber || 1
  const document = readingDocument.value
  activeOutlineItemId.value = item.source === 'user_bookmark_root' ? null : item.id
  if ((item as DisplayOutlineItem).syntheticRoot) {
    selectOutlinePage(page)
    return
  }
  const targetBlock = document?.blocks.find((block) => {
    return block.id === item.elementId || block.elementId === item.elementId
  }) ?? document?.blocks.find((block) => (block.pageNumber || 1) === page)
  if (targetBlock) {
    activeBlockId.value = targetBlock.id
  }
  targetPdfPage.value = page
  currentPdfPage.value = page
  clearPdfSelection()
  scheduleWorkspaceStateSave()
}

function selectOutlineBlock(blockId: string, page: number) {
  activeBlockId.value = blockId
  activeOutlineItemId.value = null
  targetPdfPage.value = page
  currentPdfPage.value = page
  clearPdfSelection()
  scheduleWorkspaceStateSave()
}

function handlePdfPageChange(page: number) {
  currentPdfPage.value = normalizePageNumber(page)
  syncActiveBlockToPdfPage(page)
  scheduleWorkspaceStateSave()
}

function isOutlineItemActive(item: DocumentOutlineItem) {
  return item.id === activeOutlineItemId.value
    || item.elementId === activeBlock.value?.elementId
    || item.elementId === activeBlockId.value
    || (!item.elementId && item.pageNumber === currentPdfPage.value)
}

function getOutlineItemNoteCount(item: DocumentOutlineItem) {
  if (item.source === 'user_bookmark_root') {
    return studyNotes.value.filter((note) => !!note.bookmarkId).length
  }
  if (item.source === 'user_bookmark') {
    return noteCountByBookmarkId.value.get(item.id) ?? 0
  }
  if (item.elementId) {
    return noteCountByElementId.value.get(item.elementId) ?? 0
  }
  return getPageNoteCount(item.pageNumber)
}

function getPageNoteCount(page: number) {
  return noteCountByPage.value.get(page) ?? 0
}

function isStudyNoteInActiveContext(note: StudyNote) {
  if (note.id === activeNoteId.value) return true

  const context = selectedPdfContext.value
  if (context && note.pageNumber === context.pageNumber) {
    return note.blockId === context.blockId
      || note.elementId === context.elementId
      || (!!context.bbox && note.bbox === context.bbox)
  }

  const block = activeBlock.value
  return (!!block && (note.blockId === block.id || note.elementId === block.elementId))
    || (!!activeOutlineItemId.value && note.bookmarkId === activeOutlineItemId.value)
}

function isOutlineNodeCollapsed(item: DisplayOutlineItem) {
  return collapsedOutlineItemIds.value.has(item.id)
}

function toggleOutlineNode(item: DisplayOutlineItem) {
  if (!item.hasChildren) return
  const nextCollapsedIds = new Set(collapsedOutlineItemIds.value)
  if (nextCollapsedIds.has(item.id)) {
    nextCollapsedIds.delete(item.id)
  } else {
    nextCollapsedIds.add(item.id)
  }
  collapsedOutlineItemIds.value = nextCollapsedIds
  scheduleWorkspaceStateSave()
}

function createUserBookmark() {
  const document = readingDocument.value
  if (!document) return
  const context = resolveAgentSourceContext()
  if (!context) {
    showToast('请先定位到 PDF 页或段落', 'info')
    return
  }
  const now = new Date().toISOString()
  const bookmark: UserBookmark = {
    id: `user-bookmark-${Date.now()}`,
    title: buildDefaultBookmarkTitle(context.text),
    pageNumber: context.pageNumber,
    level: 3,
    elementId: context.elementId,
    bbox: context.bbox,
    source: 'user_bookmark',
    parentId: activeOutlineItemId.value,
    order: userBookmarks.value.length + 1,
    createdAt: now,
    updatedAt: now,
  }
  userBookmarks.value = [...userBookmarks.value, bookmark]
  activeOutlineItemId.value = bookmark.id
  targetPdfPage.value = bookmark.pageNumber
  currentPdfPage.value = bookmark.pageNumber
  showToast('已添加到我的书签', 'success')
  scheduleWorkspaceStateSave()
}

function renameActiveUserBookmark() {
  const bookmark = activeUserBookmark.value
  if (!bookmark || typeof window === 'undefined') return
  const nextTitle = window.prompt('重命名书签', bookmark.title)?.trim()
  if (!nextTitle || nextTitle === bookmark.title) return
  const now = new Date().toISOString()
  userBookmarks.value = userBookmarks.value.map((item) => item.id === bookmark.id
    ? { ...item, title: nextTitle, updatedAt: now }
    : item)
  showToast('书签已重命名', 'success')
  scheduleWorkspaceStateSave()
}

function deleteActiveUserBookmark() {
  const bookmark = activeUserBookmark.value
  if (!bookmark) return
  if (typeof window !== 'undefined' && !window.confirm(`删除书签「${bookmark.title}」？笔记会保留。`)) {
    return
  }
  userBookmarks.value = userBookmarks.value.filter((item) => item.id !== bookmark.id)
  studyNotes.value = studyNotes.value.map((note) => note.bookmarkId === bookmark.id
    ? { ...note, bookmarkId: null, updatedAt: new Date().toISOString() }
    : note)
  activeOutlineItemId.value = null
  showToast('书签已删除，相关笔记已保留', 'success')
  scheduleWorkspaceStateSave()
}

async function exportWorkspaceBookmarks() {
  const document = readingDocument.value
  if (!document) return
  try {
    await persistWorkspaceState()
    const { blob, fileName } = await downloadTranslationDocumentWithBookmarks(document.id)
    downloadBlob(blob, fileName)
    showToast('已导出带书签 PDF', 'success')
  } catch (error) {
    console.warn('[TranslationWorkspace] export bookmarked PDF failed', error)
    showToast('导出带书签 PDF 失败', 'error')
  }
}

function downloadBlob(blob: Blob, fileName: string) {
  if (typeof window === 'undefined' || typeof document === 'undefined') return
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  URL.revokeObjectURL(url)
}

function buildDefaultBookmarkTitle(text: string) {
  const currentOutline = activeOutlineItemId.value ? findDisplayOutlineItemById(activeOutlineItemId.value) : null
  if (currentOutline && currentOutline.source !== 'user_bookmark_root') {
    return currentOutline.title.length > 28 ? `${currentOutline.title.slice(0, 28)}...` : currentOutline.title
  }
  return buildDefaultNoteTitle(text)
}

function findDisplayOutlineItemById(id: string) {
  return outlineTreeItems.value.find((item) => item.id === id) ?? null
}

function matchesOutlineScope(item: DocumentOutlineItem) {
  if (outlineFilterScope.value === 'current') return item.pageNumber === currentPdfPage.value
  if (outlineFilterScope.value === 'notes') return getOutlineItemNoteCount(item) > 0
  return true
}

function matchesPageScope(page: number) {
  if (outlineFilterScope.value === 'current') return page === currentPdfPage.value
  if (outlineFilterScope.value === 'notes') return getPageNoteCount(page) > 0
  return true
}

function matchesOutlineSearch(item: DocumentOutlineItem) {
  const query = normalizeOutlineQuery(outlineSearchQuery.value)
  if (!query) return true
  const noteCount = getOutlineItemNoteCount(item)
  return normalizeOutlineQuery(item.title).includes(query)
    || String(item.pageNumber).includes(query)
    || (noteCount > 0 && ('笔记'.includes(query) || 'note'.includes(query)))
    || (item.pageNumber === currentPdfPage.value && '当前页'.includes(query))
}

function matchesPageSearch(page: number) {
  const query = normalizeOutlineQuery(outlineSearchQuery.value)
  if (!query) return true
  const noteCount = getPageNoteCount(page)
  return String(page).includes(query)
    || (noteCount > 0 && ('笔记'.includes(query) || 'note'.includes(query)))
    || (page === currentPdfPage.value && '当前页'.includes(query))
}

function normalizeOutlineQuery(value: string) {
  return value.trim().toLowerCase()
}

function isDuplicateDocumentRoot(item: DocumentOutlineItem, documentTitle: string, index: number) {
  if (index > 2) return false
  const itemTitle = normalizeOutlineTitleForCompare(item.title)
  const currentTitle = normalizeOutlineTitleForCompare(documentTitle)
  if (!itemTitle || !currentTitle) return false
  const looksLikeParsedCoverTitle = index === 0
    && item.pageNumber <= 2
    && item.level <= 1
    && !inferDisplayOutlineLevel(item.title)
  return item.pageNumber <= 2
    && (looksLikeParsedCoverTitle || itemTitle === currentTitle || currentTitle.includes(itemTitle) || itemTitle.includes(currentTitle))
}

function inferDisplayOutlineLevel(title: string): number | null {
  const text = title.trim()
  if (!text) return null
  if (/^第[一二三四五六七八九十百千万0-9]+[章节篇部]/.test(text)) return 2
  if (/^chapter\s+\d+/i.test(text) || /^unit\s+\d+/i.test(text)) return 2

  const sectionMatch = text.match(/^§?\s*(\d+(?:\.\d+){0,5})/)
  if (sectionMatch?.[1]) {
    const depth = sectionMatch[1].split('.').filter(Boolean).length
    return Math.max(2, Math.min(6, depth + 1))
  }

  if (/^[□■▪●·•-]\s*\S+/.test(text)) return null
  return null
}

function normalizeOutlineTitleForCompare(value: string) {
  return value
    .replace(/\.[a-z0-9]+$/i, '')
    .replace(/[()\[\]（）【】_\-+\s·.]/g, '')
    .toLowerCase()
}

function handlePdfSelectionChange(payload: PdfSelectionPayload) {
  const selectionType = payload.selectionType ?? 'text'
  const displayText = payload.text.trim() || (payload.selectionType === 'region' ? '图表/图片区选区' : '')
  selectedPdfSelectionType.value = displayText ? selectionType : null
  selectedPdfText.value = displayText
  if (displayText) {
    pdfSourceHighlight.value = null
  }
  selectedPdfContext.value = displayText && (payload.elementId || payload.bbox || payload.selectionType === 'region')
    ? {
        documentId: payload.documentId,
        pageNumber: payload.pageNumber,
        blockId: payload.blockId ?? payload.elementId ?? 'region-selection',
        elementId: payload.elementId ?? 'region-selection',
        bbox: payload.bbox,
        text: displayText,
      }
    : null
}

function clearPdfSelection() {
  selectedPdfText.value = ''
  selectedPdfContext.value = null
  selectedPdfSelectionType.value = null
  pdfSourceHighlight.value = null
}

function toggleOutlineDrawer() {
  isOutlineCollapsed.value = !isOutlineCollapsed.value
  stopWorkspaceResize()
}

function toggleAgentDrawer() {
  isAgentCollapsed.value = !isAgentCollapsed.value
  stopWorkspaceResize()
}

function startWorkspaceResize(target: WorkspaceResizeTarget, event: PointerEvent) {
  if ((target === 'outline' && isOutlineCollapsed.value) || (target === 'agent' && isAgentCollapsed.value)) {
    return
  }
  activeResizeTarget.value = target
  event.preventDefault()
  document.body.classList.add('translation-workspace-resizing')
  window.addEventListener('pointermove', resizeWorkspacePanels)
  window.addEventListener('pointerup', stopWorkspaceResize)
}

function resizeWorkspacePanels(event: PointerEvent) {
  const shell = workspaceShellRef.value
  const target = activeResizeTarget.value
  if (!shell || !target) return

  const rect = shell.getBoundingClientRect()
  const shellWidth = rect.width

  if (target === 'outline') {
    const maxOutlineWidth = Math.min(
      outlineColumnMaxWidth,
      shellWidth - agentColumnWidth.value - centerColumnMinWidth - resizerColumnsWidth,
    )
    outlineColumnWidth.value = clamp(
      event.clientX - rect.left,
      outlineColumnMinWidth,
      Math.max(outlineColumnMinWidth, maxOutlineWidth),
    )
    return
  }

  const maxAgentWidth = Math.min(
    agentColumnMaxWidth,
    shellWidth - outlineColumnWidth.value - centerColumnMinWidth - resizerColumnsWidth,
  )
  agentColumnWidth.value = clamp(
    rect.right - event.clientX,
    agentColumnMinWidth,
    Math.max(agentColumnMinWidth, maxAgentWidth),
  )
}

function stopWorkspaceResize() {
  activeResizeTarget.value = null
  if (typeof document !== 'undefined') {
    document.body.classList.remove('translation-workspace-resizing')
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('pointermove', resizeWorkspacePanels)
    window.removeEventListener('pointerup', stopWorkspaceResize)
  }
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function askAgent(question: string) {
  agentPrompt.value = question
  void submitAgentQuestion()
}

async function submitAgentQuestion() {
  const question = agentPrompt.value.trim()
  const document = readingDocument.value
  if (!question || !activeBlock.value || !document || agentAnswerLoading.value) return

  const currentBlock = activeBlock.value
  const sourceContext = resolveAgentSourceContext()
  const selectedQuestion = question
  agentPrompt.value = ''
  agentMessages.value.push({
    id: `user-${Date.now()}`,
    role: 'user',
    content: selectedQuestion,
    sourceContext,
  })
  agentAnswerLoading.value = true
  try {
    const answer = await requestAgentAnswerForContext(selectedQuestion, sourceContext, currentBlock)
    agentMessages.value.push({
      id: `assistant-${Date.now()}`,
      role: 'assistant',
      content: answer.answer,
      sourceContext,
      citations: answer.citations,
    })
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Agent 回答失败'
    agentMessages.value.push({
      id: `assistant-error-${Date.now()}`,
      role: 'assistant',
      content: `暂时无法基于资料生成回答：${message}`,
      sourceContext,
    })
    showToast('Agent 回答失败，请稍后重试', 'error')
  } finally {
    agentAnswerLoading.value = false
  }
}

async function askAgentToAppendNote(question: string) {
  const selectedQuestion = question.trim()
  const composer = noteComposer.value
  const sourceContext = composer.context ?? resolveAgentSourceContext()
  const currentBlock = activeBlock.value
  if (composer.mode === 'idle') {
    showToast('请先打开一个笔记', 'info')
    return
  }
  if (!selectedQuestion || !sourceContext || !currentBlock || noteAgentLoading.value) return

  noteAgentLoading.value = true
  agentMessages.value.push({
    id: `user-note-${Date.now()}`,
    role: 'user',
    content: selectedQuestion,
    sourceContext,
  })
  try {
    const answer = await requestAgentAnswerForContext(selectedQuestion, sourceContext, currentBlock)
    aiCandidateContent.value = answer.answer.trim()
    agentMessages.value.push({
      id: `assistant-note-${Date.now()}`,
      role: 'assistant',
      content: answer.answer,
      sourceContext,
      citations: answer.citations,
    })
    noteAgentPrompt.value = ''
    showToast('Agent 已生成候选补充', 'success')
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Agent 回答失败'
    agentMessages.value.push({
      id: `assistant-note-error-${Date.now()}`,
      role: 'assistant',
      content: `暂时无法补充笔记：${message}`,
      sourceContext,
    })
    showToast('Agent 补充失败，请稍后重试', 'error')
  } finally {
    noteAgentLoading.value = false
  }
}

function appendAiCandidateToNote() {
  appendAgentAnswerToNoteComposer(aiCandidateContent.value)
  aiCandidateContent.value = ''
}

async function requestAgentAnswerForContext(
  question: string,
  sourceContext: DocumentSelectionContext | null,
  fallbackBlock: DocumentBlock,
) {
  const document = readingDocument.value
  if (!document) throw new Error('缺少当前文档')
  return answerTranslationDocumentQuestion(document.id, {
    question,
    selectedText: sourceContext?.text ?? fallbackBlock.text,
    pageNumber: sourceContext?.pageNumber ?? fallbackBlock.pageNumber,
    elementId: sourceContext?.elementId ?? fallbackBlock.elementId ?? fallbackBlock.id,
    bbox: sourceContext?.bbox ?? fallbackBlock.bbox ?? null,
    mode: activeMode.value,
  })
}

function appendAgentAnswerToNoteComposer(content: string) {
  const normalizedContent = content.trim()
  if (!normalizedContent) return
  if (noteComposer.value.mode === 'idle') {
    showToast('请先打开一个笔记', 'info')
    return
  }
  const currentContent = noteComposer.value.content.trimEnd()
  noteComposer.value.content = `${currentContent}${currentContent ? '\n\n' : ''}Agent 补充：\n${normalizedContent}`
  focusNoteComposer()
}

function resolveAgentSourceContext(): DocumentSelectionContext | null {
  if (selectedPdfContext.value) return selectedPdfContext.value
  const document = readingDocument.value
  const block = activeBlock.value
  if (!document || !block) return null
  return buildDocumentSelectionContext(document.id, block)
}

function jumpToCitation(citation: TranslationSourceCitationDto) {
  const document = readingDocument.value
  if (!document) return
  const pageNumber = citation.pageNumber || currentPdfPage.value || 1
  const matchedBlock = document.blocks.find((block) => {
    return block.elementId === citation.elementId || block.id === citation.elementId
  })
  if (matchedBlock) {
    activeBlockId.value = matchedBlock.id
  }
  const resolvedElementId = citation.elementId ?? matchedBlock?.elementId ?? matchedBlock?.id ?? citation.chunkId
  targetPdfPage.value = pageNumber
  currentPdfPage.value = pageNumber
  documentView.value = 'pdf-canvas'
  selectedPdfText.value = citation.quote
  selectedPdfSelectionType.value = 'text'
  pdfSourceHighlight.value = buildCitationHighlight(citation, pageNumber)
  selectedPdfContext.value = {
    documentId: document.id,
    pageNumber,
    blockId: matchedBlock?.id ?? resolvedElementId,
    elementId: resolvedElementId,
    bbox: citation.bbox,
    text: citation.quote,
  }
  scheduleWorkspaceStateSave()
}

function buildCitationHighlight(
  citation: TranslationSourceCitationDto,
  pageNumber: number,
): PdfSourceHighlight | null {
  if (!citation.bbox) return null
  return {
    pageNumber,
    bbox: citation.bbox,
    label: '引用定位',
    text: citation.quote,
  }
}

function createEmptyNoteComposer(): StudyNoteComposerState {
  return {
    mode: 'idle',
    noteId: null,
    bookmarkId: null,
    source: 'manual',
    status: 'saved',
    title: '',
    content: '',
    context: null,
  }
}

function startNoteFromPdfSelection(payload: PdfSelectionPayload) {
  agentPanelMode.value = 'note-workbench'
  isAgentCollapsed.value = false
  const context = resolveNoteContextFromPdfSelection(payload)
  openNoteComposer({
    context,
    title: buildDefaultNoteTitle(payload.text),
    content: '',
    source: 'manual',
    status: 'saved',
  })
}

function startNoteFromActiveBlock() {
  const context = resolveAgentSourceContext()
  if (!context) {
    showToast('请先选择一段内容', 'info')
    return
  }
  openNoteComposer({
    context,
    title: buildDefaultNoteTitle(context.text),
    content: '',
    source: 'manual',
    status: 'saved',
  })
}

function startNoteFromAgentMessage(message: LocalAgentMessage) {
  const context = message.sourceContext ?? resolveAgentSourceContext()
  if (!context) {
    showToast('缺少笔记来源位置', 'info')
    return
  }
  openNoteComposer({
    context,
    title: buildDefaultNoteTitle(message.content),
    content: message.content,
    source: 'agent',
    status: 'draft',
  })
}

function openNoteComposer(input: {
  context: DocumentSelectionContext
  title: string
  content: string
  source: StudyNoteSource
  status: StudyNoteStatus
}) {
  activeNoteId.value = null
  activeSidePanel.value = 'notes'
  isAgentCollapsed.value = false
  agentPanelMode.value = 'note-workbench'
  if (isOutlineCollapsed.value) {
    isOutlineCollapsed.value = false
  }
  const bookmarkId = resolveActiveBookmarkId(input.context)
  noteAgentPrompt.value = ''
  aiCandidateContent.value = ''
  noteComposer.value = {
    mode: 'create',
    noteId: null,
    bookmarkId,
    source: input.source,
    status: input.status,
    title: input.title,
    content: input.content,
    context: input.context,
  }
  documentView.value = 'pdf-canvas'
  focusNoteComposer()
}

function editStudyNote(note: StudyNote) {
  activeNoteId.value = note.id
  noteAgentPrompt.value = ''
  aiCandidateContent.value = ''
  agentPanelMode.value = 'note-workbench'
  isAgentCollapsed.value = false
  noteComposer.value = {
    mode: 'edit',
    noteId: note.id,
    bookmarkId: note.bookmarkId,
    source: note.source,
    status: note.status,
    title: note.title,
    content: note.content,
    context: {
      documentId: note.documentId,
      pageNumber: note.pageNumber,
      blockId: note.blockId,
      elementId: note.elementId,
      bbox: note.bbox,
      text: note.selectedText,
    },
  }
  focusNoteComposer()
}

function focusNoteComposer() {
  void nextTick(() => {
    noteContentInputRef.value?.focus()
  })
}

function resolveActiveBookmarkId(context: DocumentSelectionContext): string | null {
  const activeItem = activeOutlineItemId.value ? findDisplayOutlineItemById(activeOutlineItemId.value) : null
  if (activeItem && activeItem.source !== 'user_bookmark_root' && !activeItem.syntheticRoot && activeItem.pageNumber === context.pageNumber) {
    return activeItem.id
  }
  const matchedUserBookmark = userBookmarks.value.find((bookmark) => {
    return bookmark.pageNumber === context.pageNumber
      && (!!bookmark.elementId && bookmark.elementId === context.elementId)
  })
  return matchedUserBookmark?.id ?? null
}

function saveStudyNote() {
  const composer = noteComposer.value
  const context = composer.context
  const title = composer.title.trim()
  const content = composer.content.trim()
  if (!context) {
    showToast('请先选择笔记来源', 'info')
    return
  }
  if (!title || !content) {
    showToast('请补充笔记标题和内容', 'info')
    return
  }

  const now = new Date().toISOString()
  const bookmarkId = composer.bookmarkId ?? resolveActiveBookmarkId(context)
  if (composer.mode === 'edit' && composer.noteId) {
    studyNotes.value = studyNotes.value.map((note) => note.id === composer.noteId
      ? {
          ...note,
          bookmarkId,
          title,
          content,
          status: composer.status,
          source: composer.source,
          selectedText: context.text,
          pageNumber: context.pageNumber,
          blockId: context.blockId,
          elementId: context.elementId,
          bbox: context.bbox,
          updatedAt: now,
        }
      : note)
    activeNoteId.value = composer.noteId
  } else {
    const note: StudyNote = {
      id: `study-note-${Date.now()}`,
      documentId: context.documentId,
      bookmarkId,
      pageNumber: context.pageNumber,
      blockId: context.blockId,
      elementId: context.elementId,
      bbox: context.bbox,
      selectedText: context.text,
      title,
      content,
      source: composer.source,
      status: composer.status,
      tags: inferNoteTags(title, context.text),
      createdAt: now,
      updatedAt: now,
    }
    studyNotes.value = [note, ...studyNotes.value]
    activeNoteId.value = note.id
  }
  noteComposer.value = createEmptyNoteComposer()
  noteAgentPrompt.value = ''
  aiCandidateContent.value = ''
  agentPanelMode.value = 'agent'
  showToast(composer.status === 'draft' ? '已生成待整理笔记' : '已保存为学习笔记', 'success')
  scheduleWorkspaceStateSave()
}

function cancelStudyNoteComposer() {
  noteComposer.value = createEmptyNoteComposer()
  noteAgentPrompt.value = ''
  aiCandidateContent.value = ''
  agentPanelMode.value = 'agent'
}

function updateStudyNoteStatus(noteId: string, status: StudyNoteStatus) {
  const now = new Date().toISOString()
  studyNotes.value = studyNotes.value.map((note) => note.id === noteId
    ? { ...note, status, updatedAt: now }
    : note)
  const statusLabel: Record<StudyNoteStatus, string> = {
    draft: '待整理',
    saved: '已沉淀',
    reviewing: '复习中',
    mastered: '已掌握',
  }
  showToast(`已移动到${statusLabel[status]}`, 'success')
  scheduleWorkspaceStateSave()
}

function openStudyNote(noteId: string) {
  const note = studyNotes.value.find((item) => item.id === noteId)
  if (!note) return
  activeNoteId.value = note.id
  jumpToStudyNote(note)
}

function resolveNoteBookmarkLabel(note: StudyNote) {
  if (!note.bookmarkId) return ''
  const userBookmark = userBookmarks.value.find((bookmark) => bookmark.id === note.bookmarkId)
  if (userBookmark) return userBookmark.title
  const outlineItem = findDisplayOutlineItemById(note.bookmarkId)
  return outlineItem?.title ?? '已绑定定位'
}

function jumpToStudyNote(note: StudyNote) {
  activeBlockId.value = note.blockId
  activeOutlineItemId.value = note.bookmarkId
  targetPdfPage.value = note.pageNumber
  currentPdfPage.value = note.pageNumber
  documentView.value = 'pdf-canvas'
  selectedPdfText.value = note.selectedText
  selectedPdfSelectionType.value = note.bbox ? 'text' : null
  selectedPdfContext.value = {
    documentId: note.documentId,
    pageNumber: note.pageNumber,
    blockId: note.blockId,
    elementId: note.elementId,
    bbox: note.bbox,
    text: note.selectedText,
  }
  pdfSourceHighlight.value = note.bbox
    ? {
        pageNumber: note.pageNumber,
        bbox: note.bbox,
        label: '笔记来源',
        text: note.selectedText,
      }
    : null
  scheduleWorkspaceStateSave()
}

function resolveNoteContextFromPdfSelection(payload: PdfSelectionPayload): DocumentSelectionContext {
  const document = readingDocument.value
  const fallbackBlock = document?.blocks.find((block) => {
    return block.id === payload.blockId || block.elementId === payload.elementId
  }) ?? activeBlock.value
  return {
    documentId: payload.documentId,
    pageNumber: payload.pageNumber,
    blockId: payload.blockId ?? fallbackBlock?.id ?? 'selection',
    elementId: payload.elementId ?? fallbackBlock?.elementId ?? fallbackBlock?.id ?? 'selection',
    bbox: payload.bbox,
    text: payload.text,
  }
}

function buildDefaultNoteTitle(text: string) {
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (!normalized) return '新的学习笔记'
  return normalized.length > 24 ? `${normalized.slice(0, 24)}...` : normalized
}

function inferNoteTags(title: string, text: string) {
  const source = `${title} ${text}`.toLowerCase()
  const tags: string[] = []
  if (source.includes('o(') || source.includes('复杂度')) tags.push('复杂度')
  if (source.includes('sort') || source.includes('排序')) tags.push('排序')
  if (source.includes('公式') || source.includes('n²') || source.includes('n^2')) tags.push('公式')
  return tags
}

</script>

<style scoped>
.intensive-workspace-page {
  --ide-bg: #f3f6fa;
  --ide-panel: #ffffff;
  --ide-panel-2: #f8fafc;
  --ide-panel-3: #eef7f6;
  --ide-border: #d9e2ec;
  --ide-text: #102033;
  --ide-muted: #667085;
  --ide-accent: #0f8f89;
  --reader-bg: #f5f8fb;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 0;
  height: 100vh;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  background: var(--ide-bg);
  color: var(--ide-text);
}

.workspace-toolbar,
.workspace-ide-titlebar,
.workspace-shell,
.workspace-status-bar {
  width: 100%;
  margin: 0;
}

.workspace-ide-titlebar {
  display: grid;
  grid-template-columns: minmax(220px, auto) minmax(280px, 520px) auto;
  gap: 16px;
  align-items: center;
  min-height: 52px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
  color: var(--ide-text);
}

.workspace-brand {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.workspace-brand__mark {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid rgba(45, 212, 191, 0.45);
  border-radius: 9px;
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
  font-weight: 900;
}

.workspace-brand strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-brand strong {
  display: block;
  color: var(--ide-text);
  font-size: 14px;
}

.workspace-command-center input {
  width: 100%;
  min-height: 34px;
  border: 1px solid var(--ide-border);
  border-radius: 999px;
  background: #ffffff;
  color: var(--ide-text);
  padding: 0 16px;
}

.workspace-command-center input::placeholder {
  color: var(--ide-muted);
}

.workspace-titlebar-actions {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.workspace-toolbar {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

button,
input,
textarea {
  font: inherit;
}

.back-button,
.toolbar-actions button,
.activity-button,
.reader-status span,
.inline-actions button,
.agent-card-actions button,
.command-actions button,
.side-drawer-panel button,
.workspace-status-bar button,
.study-note-panel button,
.message-save-note,
.message-append-note,
.missing-state button {
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-weight: 800;
}

.back-button,
.toolbar-actions button,
.command-actions button,
.missing-state button {
  min-height: 38px;
  padding: 0 13px;
  cursor: pointer;
}

.back-button {
  display: grid;
  width: 32px;
  min-height: 32px;
  padding: 0;
  place-items: center;
}

.back-button-icon {
  font-size: 18px;
  line-height: 1;
}

.primary-action {
  border-color: #0f8f89 !important;
  background: #0f8f89 !important;
  color: #ffffff !important;
}

.document-heading p,
.reader-heading p,
.agent-header p,
.answer-label,
.missing-state p {
  margin: 0;
  color: #667085;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
}

.document-heading h1 {
  max-width: 820px;
  margin: 0;
  overflow: hidden;
  color: #111827;
  font-size: 22px;
  line-height: 1.15;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-heading {
  min-width: 0;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.workspace-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 20px;
  min-height: 0;
  align-items: stretch;
}

.workspace-shell--ide {
  grid-template-columns:
    48px
    minmax(220px, var(--outline-column-width, 300px))
    8px
    minmax(560px, 1fr)
    8px
    minmax(340px, var(--agent-column-width, 430px));
  align-items: stretch;
  gap: 0;
  width: 100%;
  height: 100%;
}

.workspace-shell--outline-collapsed {
  grid-template-columns:
    48px
    0
    0
    minmax(560px, 1fr)
    8px
    minmax(340px, var(--agent-column-width, 430px));
}

.workspace-shell--agent-collapsed {
  grid-template-columns:
    48px
    minmax(220px, var(--outline-column-width, 300px))
    8px
    minmax(560px, 1fr)
    0
    44px;
}

.workspace-shell--outline-collapsed.workspace-shell--agent-collapsed {
  grid-template-columns: 48px 0 0 minmax(560px, 1fr) 0 44px;
}

.workspace-activity-bar,
.workspace-outline-panel,
.workspace-canvas-panel,
.workspace-agent-panel {
  min-height: 0;
  height: 100%;
  min-width: 0;
  border: 1px solid var(--ide-border);
  border-radius: 0;
  background: var(--ide-panel);
  overflow: hidden;
}

.workspace-activity-bar {
  grid-column: 1;
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 8px 6px;
  border-right: 0;
  background: #ffffff;
}

.workspace-activity-bar .activity-button {
  border-color: transparent;
  background: transparent;
  color: var(--ide-muted);
}

.workspace-activity-bar .activity-button.active,
.workspace-activity-bar .activity-button:hover,
.workspace-activity-bar .activity-button:focus-visible {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
}

.workspace-outline-panel {
  grid-column: 2;
}

.workspace-explorer {
  background: var(--ide-panel);
  color: var(--ide-text);
}

.workspace-resizer--outline {
  grid-column: 3;
}

.workspace-canvas-panel {
  grid-column: 4;
}

.workspace-resizer--agent {
  grid-column: 5;
}

.workspace-agent-panel {
  grid-column: 6;
}

.workspace-outline-panel {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
  border-left: 0;
}

.workspace-canvas-panel {
  border-radius: 0;
  background: var(--reader-bg);
}

.workspace-agent-panel {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
  background: var(--ide-panel);
  color: var(--ide-text);
}

.workspace-resizer {
  position: relative;
  z-index: 5;
  min-width: 8px;
  height: 100%;
  padding: 0;
  border: 0;
  background:
    linear-gradient(90deg, transparent 0, transparent 3px, #cbd5e1 3px, #cbd5e1 5px, transparent 5px);
  cursor: col-resize;
}

.workspace-resizer::after {
  position: absolute;
  inset: 0 -5px;
  content: '';
}

.workspace-resizer:hover,
.workspace-resizer:focus-visible {
  background:
    linear-gradient(90deg, transparent 0, transparent 2px, #14b8a6 2px, #14b8a6 6px, transparent 6px);
  outline: none;
}

:global(.translation-workspace-resizing) {
  cursor: col-resize;
  user-select: none;
}

.workspace-panel--collapsed {
  display: grid !important;
  grid-template-rows: minmax(0, 1fr) !important;
  place-items: stretch;
  padding: 0 !important;
}

.workspace-panel--collapsed > :not(.workspace-drawer-rail) {
  display: none !important;
}

.workspace-outline-panel.workspace-panel--collapsed {
  position: relative;
  z-index: 1;
  border: 0;
  background: transparent;
  overflow: visible;
}

.activity-button {
  position: relative;
  display: grid;
  width: 34px;
  height: 34px;
  min-height: 34px;
  padding: 0;
  place-items: center;
  border-color: transparent;
  border-radius: 8px;
  color: #526071;
  cursor: pointer;
}

.activity-button.active,
.activity-button:hover,
.activity-button:focus-visible {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
}

.activity-button__icon {
  font-size: 14px;
  font-weight: 950;
  line-height: 1;
}

.activity-button small {
  position: absolute;
  right: -3px;
  top: -3px;
  min-width: 16px;
  padding: 1px 4px;
  border: 1px solid var(--ide-bg);
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 9px;
  font-weight: 950;
  line-height: 1.2;
}

.workspace-drawer-rail {
  display: grid;
  width: 100%;
  min-width: 0;
  height: 100%;
  place-items: center;
  border: 0;
  border-radius: 0;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
  writing-mode: vertical-rl;
}

.workspace-drawer-rail:hover,
.workspace-drawer-rail:focus-visible {
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
  transform: none;
}

.workspace-drawer-rail--outline {
  position: absolute;
  top: 12px;
  left: 8px;
  z-index: 13;
  width: 34px;
  min-width: 34px;
  height: 34px;
  border: 1px solid var(--ide-border);
  border-radius: 9px;
  background: var(--ide-panel-2);
  box-shadow: 0 8px 20px rgb(15 23 42 / 14%);
  color: var(--ide-accent);
  font-size: 16px;
  writing-mode: horizontal-tb;
}

.workspace-drawer-rail--outline:hover,
.workspace-drawer-rail--outline:focus-visible {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
}

.workspace-drawer-rail__icon {
  line-height: 1;
}

.panel-drawer-toggle {
  flex: 0 0 auto;
  min-height: 28px;
  padding: 0 8px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.panel-drawer-toggle:hover {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
}

.workspace-outline-panel {
  position: relative;
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  background: var(--ide-panel);
}

.outline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px 10px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
}

.outline-header h2 {
  margin: 0;
  color: var(--ide-text);
  font-size: 15px;
  line-height: 1.2;
  font-weight: 800;
}

.outline-heading-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.outline-heading-main p,
.outline-heading-main span {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-heading-main p {
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
}

.outline-heading-main span {
  color: #748094;
  font-size: 12px;
  font-weight: 650;
}

.workspace-explorer {
  background: var(--ide-panel);
}

.workspace-explorer .outline-header {
  border-color: var(--ide-border);
  background: var(--ide-panel);
}

.workspace-explorer .outline-heading-main p,
.workspace-explorer .outline-heading-main span {
  color: var(--ide-muted);
}

.workspace-explorer .outline-header h2 {
  color: var(--ide-text);
  letter-spacing: 0.08em;
}

.workspace-resource-actions {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
}

.workspace-resource-actions button {
  min-width: 0;
  border: 1px solid transparent;
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  cursor: pointer;
}

.workspace-resource-actions button:hover,
.workspace-resource-actions button:focus-visible {
  border-color: rgba(45, 212, 191, 0.4);
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
  outline: none;
}

.workspace-resource-actions {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.workspace-resource-actions button {
  min-height: 34px;
  padding: 0 8px;
  text-align: center;
  font-size: 12px;
  font-weight: 900;
}

.side-drawer-switcher {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 4px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
}

.side-drawer-switcher button {
  min-height: 28px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.side-drawer-switcher button.active,
.side-drawer-switcher button:hover,
.side-drawer-switcher button:focus-visible {
  border-color: rgba(45, 212, 191, 0.42);
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
}

.project-tree-shell {
  grid-row: 4;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  background: var(--ide-panel);
}

.project-tree-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel-2);
}

.project-tree-toolbar div {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.project-tree-toolbar span,
.project-tree-toolbar small {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
}

.project-tree-toolbar strong {
  min-width: 0;
  overflow: hidden;
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-tree-toolbar small {
  flex: 0 0 auto;
  color: #0f766e;
}

.project-tree {
  display: grid;
  align-content: start;
  gap: 7px;
  min-height: 0;
  padding: 10px 8px 16px;
  overflow: auto;
  scrollbar-color: #cbd5e1 transparent;
  scrollbar-width: thin;
}

.project-tree::-webkit-scrollbar {
  width: 8px;
}

.project-tree::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: #cbd5e1;
  background-clip: content-box;
}

.project-tree-folder {
  display: grid;
  gap: 4px;
}

.project-tree-folder__header {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  min-height: 32px;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 8px;
  padding: 0 8px;
  background: transparent;
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 950;
  text-align: left;
  cursor: pointer;
}

.project-tree-folder__header:hover,
.project-tree-folder__header:focus-visible {
  background: #eefaf7;
  color: #0f766e;
  outline: none;
}

.project-tree-folder__header span:not(.project-tree-folder__chevron) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-tree-folder__header small {
  min-width: 24px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #eef2f7;
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 900;
  text-align: center;
}

.project-tree-folder__chevron {
  display: inline-grid;
  place-items: center;
  color: #8b98aa;
  font-size: 18px;
  line-height: 1;
  transform: rotate(90deg);
  transition: transform 0.16s ease;
}

.project-tree-folder.is-collapsed .project-tree-folder__chevron {
  transform: rotate(0deg);
}

.project-tree-folder__body {
  display: grid;
  gap: 3px;
  padding-left: 22px;
}

.project-tree-resource {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  min-height: 38px;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 5px 7px;
  background: transparent;
  color: var(--ide-text);
  text-align: left;
  cursor: pointer;
}

.project-tree-resource:hover,
.project-tree-resource:focus-visible {
  border-color: rgba(20, 184, 166, 0.28);
  background: #eefaf7;
  outline: none;
}

.project-tree-resource__icon {
  display: inline-grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid #d8e2ee;
  border-radius: 7px;
  background: #ffffff;
  color: #0f766e;
  font-size: 10px;
  font-weight: 950;
}

.project-tree-resource__main {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.project-tree-resource strong,
.project-tree-resource small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-tree-resource strong {
  color: inherit;
  font-size: 12px;
  font-weight: 850;
}

.project-tree-resource small {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 750;
}

.project-tree-resource mark {
  min-width: 20px;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.14);
  color: #0f766e;
  font-size: 11px;
  font-weight: 950;
  text-align: center;
}

.project-tree-empty {
  min-height: 34px;
  border: 1px dashed #cdd9e6;
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.project-tree-empty:hover,
.project-tree-empty:focus-visible {
  border-color: rgba(20, 184, 166, 0.42);
  background: #eefaf7;
  color: #0f766e;
  outline: none;
}

.project-tree-outline {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid var(--ide-border);
}

.project-tree-outline .project-tree-folder__body {
  gap: 7px;
  padding-left: 0;
}

.project-tree-outline .outline-controls {
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  padding: 8px;
  background: var(--ide-panel-2);
}

.project-tree-outline .outline-list {
  grid-row: auto;
  overflow: visible;
  padding: 2px 0 6px;
}

.side-drawer-panel {
  grid-row: 3 / 5;
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 0;
  padding: 10px;
  overflow: auto;
}

.side-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 4px;
}

.side-section-heading strong {
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 950;
}

.side-section-heading button,
.side-empty-action {
  min-height: 30px;
  padding: 0 10px;
  cursor: pointer;
}

.side-list-card,
.side-asset-card {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  cursor: pointer;
}

.side-list-card:hover,
.side-list-card.active,
.side-asset-card:hover,
.side-asset-card.active {
  border-color: #5eead4;
  background: rgba(45, 212, 191, 0.12);
  box-shadow: inset 3px 0 0 #14b8a6;
}

.side-list-card strong,
.side-list-card span,
.side-asset-card strong,
.side-asset-card span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-list-card strong,
.side-asset-card strong {
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 950;
}

.side-list-card span,
.side-asset-card span {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
}

.side-empty-action {
  border-style: dashed !important;
  background: var(--ide-panel-2) !important;
  color: var(--ide-accent) !important;
  font-size: 12px;
  font-weight: 950;
}

.side-asset-board {
  gap: 10px;
}

.side-asset-group {
  display: grid;
  gap: 7px;
  padding: 9px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
}

.side-asset-group--warm {
  border-color: rgba(253, 230, 138, 0.36);
  background: rgba(253, 230, 138, 0.08);
}

.side-asset-group--green {
  border-color: rgba(187, 247, 208, 0.3);
  background: rgba(45, 212, 191, 0.08);
}

.side-asset-group--blue {
  border-color: rgba(191, 219, 254, 0.28);
  background: rgba(96, 165, 250, 0.08);
}

.side-asset-group header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.side-asset-group header span {
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 950;
}

.side-asset-group header small {
  min-width: 22px;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--ide-panel);
  color: var(--ide-accent);
  font-size: 11px;
  font-weight: 950;
  text-align: center;
}

.side-asset-group p {
  margin: 0;
  color: var(--ide-muted);
  font-size: 11px;
  line-height: 1.45;
}

.outline-controls {
  display: grid;
  gap: 8px;
  padding: 10px 12px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
}

.outline-search {
  display: grid;
  gap: 5px;
}

.outline-search span {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 750;
}

.outline-search input {
  min-height: 36px;
  width: 100%;
  min-width: 0;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  padding: 0 11px;
  background: #ffffff;
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 700;
}

.outline-search input:focus {
  border-color: #5eead4;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
  outline: none;
}

.outline-filter-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.outline-filter-tabs button {
  display: flex;
  min-width: 0;
  min-height: 30px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, color 0.16s ease;
}

.outline-filter-tabs button.active {
  border-color: rgba(45, 212, 191, 0.5);
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
}

.outline-filter-tabs small {
  color: inherit;
  font-size: 11px;
  opacity: 0.78;
}

.outline-quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.outline-quick-actions button {
  min-width: 0;
  min-height: 30px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.outline-quick-actions button:hover,
.outline-quick-actions button:focus-visible {
  border-color: rgba(45, 212, 191, 0.5);
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
  outline: none;
}

.outline-quick-actions button.danger:hover,
.outline-quick-actions button.danger:focus-visible {
  border-color: rgba(248, 113, 113, 0.5);
  background: rgba(248, 113, 113, 0.14);
  color: #fca5a5;
}

.outline-list {
  grid-row: 4;
  min-height: 0;
  overflow: auto;
  padding: 9px 8px 14px;
  scrollbar-color: #cbd5e1 transparent;
  scrollbar-width: thin;
}

.outline-list::-webkit-scrollbar {
  width: 8px;
}

.outline-list::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: #cbd5e1;
  background-clip: content-box;
}

.outline-list::-webkit-scrollbar-track {
  background: transparent;
}

.outline-page-group {
  display: grid;
  gap: 1px;
  margin-bottom: 10px;
}

.outline-tree-row {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: center;
  gap: 2px;
}

.outline-tree-row--level-1 {
  padding-left: 0;
}

.outline-tree-row--level-2 {
  padding-left: 12px;
}

.outline-tree-row--level-3 {
  padding-left: 24px;
}

.outline-tree-row--level-4 {
  padding-left: 36px;
}

.outline-tree-row--level-5 {
  padding-left: 48px;
}

.outline-tree-row--level-6 {
  padding-left: 60px;
}

.outline-tree-row.is-document-root {
  margin: 0 4px 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #edf2f7;
}

.outline-node-toggle {
  display: grid;
  width: 22px;
  height: 28px;
  min-height: 0;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #8b98aa;
  cursor: pointer;
}

.outline-node-toggle span {
  display: inline-block;
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
  transform: rotate(90deg);
  transition: transform 0.16s ease, color 0.16s ease;
}

.outline-tree-row.is-collapsed .outline-node-toggle span {
  transform: rotate(0deg);
}

.outline-node-toggle:hover,
.outline-node-toggle:focus-visible {
  background: #eefaf7;
  color: #0f766e;
  transform: none;
}

.outline-node-toggle.is-placeholder {
  pointer-events: none;
  visibility: hidden;
}

.outline-page-button,
.outline-block-button {
  display: grid;
  width: 100%;
  min-width: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ide-muted);
  text-align: left;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.outline-page-button {
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  font-weight: 750;
}

.outline-block-button {
  --outline-rail-x: 5px;
  position: relative;
  grid-template-columns: minmax(0, 1fr) max-content;
  align-items: center;
  column-gap: 8px;
  min-height: 34px;
  padding: 6px 9px 6px 14px;
}

.outline-block-button::before {
  position: absolute;
  top: 7px;
  bottom: 7px;
  left: var(--outline-rail-x);
  width: 2px;
  border-radius: 999px;
  background: #44515f;
  content: '';
}

.outline-block-button::after {
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: calc(var(--outline-rail-x) - 1px);
  width: 3px;
  border-radius: 999px;
  background: #14b8a6;
  content: '';
  opacity: 0;
  transition: opacity 0.16s ease;
}

.outline-block-button--level-1 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-1::before {
  background: #14b8a6;
}

.outline-block-button--level-2 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-3 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-4 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-5 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-6 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button.is-document-root {
  margin: 0;
  border-bottom: 0;
  border-radius: 0;
  padding: 8px 6px 8px 14px;
  color: #0f766e;
}

.outline-block-button.is-document-root::before {
  top: 10px;
  bottom: 13px;
}

.outline-block-button.is-document-root::after {
  display: none;
}

.outline-block-button.is-document-root .outline-item-title {
  font-size: 13px;
  font-weight: 850;
}

.outline-block-button.is-user-bookmark-root {
  color: #fbbf24;
}

.outline-block-button.is-user-bookmark-root::before {
  background: #f59e0b;
}

.outline-block-button.is-user-bookmark {
  color: #334155;
}

.outline-block-button.is-user-bookmark::before {
  background: #f59e0b;
}

.outline-block-button.is-user-bookmark.active {
  background: rgba(245, 158, 11, 0.14);
  color: #fbbf24;
  box-shadow: inset 0 0 0 1px rgba(251, 146, 60, 0.38);
}

.outline-block-button.is-user-bookmark.active::after {
  background: #f97316;
}

.outline-page-button:hover,
.outline-block-button:hover {
  background: #f8fafc;
  color: var(--ide-text);
}

.outline-page-button.active,
.outline-block-button.active {
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
  box-shadow: inset 0 0 0 1px rgba(45, 212, 191, 0.42);
}

.outline-block-button.is-current-page:not(.active) {
  background: rgba(56, 189, 248, 0.08);
  color: #0369a1;
}

.outline-block-button.has-notes::before {
  background: #f59e0b;
}

.outline-page-button.has-notes {
  box-shadow: inset 3px 0 0 #f59e0b;
}

.outline-block-button.active::after,
.outline-block-button.is-current-page:not(.active)::after {
  opacity: 1;
}

.outline-block-button.is-current-page:not(.active)::after {
  background: #38bdf8;
}

.outline-block-button.active::before {
  background: #14b8a6;
}

.outline-page-button span,
.outline-block-button .outline-item-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-item-title {
  color: inherit;
  font-size: 13px;
  font-weight: 750;
  line-height: 1.3;
}

.outline-block-button--level-2 .outline-item-title {
  font-size: 13.5px;
  font-weight: 800;
}

.outline-block-button--level-3 .outline-item-title {
  font-size: 13px;
  font-weight: 760;
}

.outline-block-button--level-4 .outline-item-title,
.outline-block-button--level-5 .outline-item-title,
.outline-block-button--level-6 .outline-item-title {
  color: var(--ide-muted);
  font-size: 12.5px;
  font-weight: 720;
}

.outline-item-meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
}

.outline-page-button small,
.outline-block-button small,
.outline-item-meta mark {
  color: #7a8796;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.outline-item-meta mark {
  display: inline-flex;
  min-height: 18px;
  align-items: center;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(56, 189, 248, 0.16);
  color: #7dd3fc;
}

.outline-item-meta .note-count {
  background: rgba(245, 158, 11, 0.14);
  color: #fbbf24;
}

.outline-empty-state {
  display: grid;
  gap: 5px;
  margin: 2px 4px;
  padding: 16px 12px;
  border: 1px dashed var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-muted);
}

.outline-empty-state strong {
  color: var(--ide-text);
  font-size: 13px;
}

.outline-empty-state span {
  font-size: 12px;
  line-height: 1.5;
}

.workspace-canvas-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.workspace-tabs {
  display: flex;
  min-width: 0;
  min-height: 54px;
  align-items: end;
  gap: 2px;
  padding: 10px 12px 0;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
  overflow-x: auto;
}

.workspace-tab {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: auto auto;
  column-gap: 8px;
  min-width: 150px;
  max-width: 280px;
  height: 44px;
  padding: 7px 12px 8px;
  border: 0;
  border-radius: 8px 8px 0 0;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  text-align: left;
  cursor: pointer;
}

.workspace-tab span,
.workspace-tab strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-tab span {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 900;
}

.workspace-tab strong {
  color: inherit;
  font-size: 13px;
}

.workspace-tab small {
  grid-row: 1 / 3;
  grid-column: 2;
  align-self: center;
  color: var(--ide-muted);
}

.workspace-tab.active {
  border-top: 2px solid var(--ide-accent);
  background: var(--ide-panel-3);
  color: var(--ide-text);
}

.workspace-tab.active span {
  color: var(--ide-muted);
}

.workspace-tab--new {
  display: grid;
  min-width: 42px;
  width: 42px;
  place-items: center;
  color: var(--ide-muted);
  text-align: center;
  font-size: 20px;
}

.workspace-editor-area {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  background: var(--reader-bg);
}

.workspace-editor-toolbar {
  display: flex;
  justify-content: flex-end;
  min-height: 44px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
}

.note-document-editor {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
  padding: 24px;
  background: var(--reader-bg);
}

.note-document-editor p,
.note-document-editor h2 {
  margin: 0;
}

.note-document-editor p {
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.note-document-editor h2 {
  color: var(--ide-text);
  font-size: 24px;
}

.note-document-editor textarea {
  width: 100%;
  min-height: 0;
  resize: none;
  border: 1px solid var(--ide-border);
  border-radius: 10px;
  padding: 16px;
  background: #ffffff;
  color: var(--ide-text);
}

.workspace-canvas-panel :deep(.pdf-learning-canvas) {
  min-height: 0;
  height: 100%;
}

.document-reader,
.agent-panel,
.missing-state {
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel);
}

.document-reader {
  min-width: 0;
  padding: 18px;
}

.document-reader--ide {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  height: calc(100vh - 184px);
  min-height: 620px;
  padding: 0;
  overflow: hidden;
}

.ide-pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--ide-border);
}

.ide-pane-header p {
  margin: 0;
  color: #667085;
  font-size: 12px;
  font-weight: 900;
}

.ide-pane-header h2 {
  margin: 3px 0 0;
  color: #111827;
  font-size: 20px;
  line-height: 1.2;
  font-weight: 900;
}

.document-pathbar {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
}

.document-pathbar strong {
  min-width: 0;
  overflow: hidden;
  color: #111827;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-pathbar small {
  min-width: 0;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-view-tabs {
  display: flex;
  gap: 8px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
}

.document-view-tabs--compact {
  gap: 4px;
  padding: 3px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: #ffffff;
}

.document-view-tabs button {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.document-view-tabs--compact button {
  min-height: 28px;
  padding: 0 9px;
  border-radius: 6px;
  font-size: 12px;
}

.document-view-tabs button.active {
  border-color: rgba(45, 212, 191, 0.55);
  background: rgba(45, 212, 191, 0.16);
  color: var(--ide-accent);
}

.document-badge--compact {
  flex: 0 0 auto;
  width: auto;
  height: 26px;
  min-width: 38px;
  padding: 0 9px;
  border-radius: 6px;
  font-size: 12px;
}

.ide-reader-surface {
  min-height: 0;
  overflow: auto;
  padding: 14px 0 24px;
  background:
    linear-gradient(rgba(15, 23, 42, 0.035) 31px, transparent 31px) 0 0 / 100% 32px,
    var(--reader-bg);
}

.ide-document-block {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 0;
  border-left: 3px solid transparent;
  cursor: pointer;
}

.ide-document-block:hover {
  background: #f8fafc;
}

.ide-document-block.active {
  border-left-color: #0f8f89;
  background: rgba(45, 212, 191, 0.12);
}

.ide-gutter {
  display: grid;
  align-content: start;
  gap: 5px;
  padding: 18px 12px 18px 18px;
  border-right: 1px solid var(--ide-border);
  color: var(--ide-muted);
  text-align: right;
}

.ide-gutter span {
  color: #0f766e;
  font-size: 13px;
  font-weight: 900;
}

.ide-gutter small {
  font-size: 11px;
  font-weight: 800;
}

.ide-source-cell {
  min-width: 0;
  padding: 14px 18px 18px;
}

.ide-block-meta {
  display: flex;
  min-height: 28px;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.ide-document-block.active .ide-block-meta,
.ide-document-block:hover .ide-block-meta {
  opacity: 1;
}

.ide-block-meta span {
  margin-right: auto;
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 900;
  text-transform: uppercase;
}

.ide-block-meta button,
.agent-toolbar button {
  min-height: 26px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.ide-block-meta button {
  padding: 0 8px;
}

.source-text--ide {
  max-width: 860px;
  color: var(--ide-text);
  font-size: 17px;
  line-height: 1.95;
}

.reader-heading,
.agent-header,
.document-summary,
.inline-actions,
.command-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.reader-heading h2,
.agent-header h2 {
  margin: 4px 0 0;
  color: var(--ide-text);
  font-size: 22px;
  line-height: 1.2;
  font-weight: 900;
}

.reader-status {
  display: flex;
  gap: 8px;
}

.reader-status span {
  padding: 7px 10px;
  font-size: 12px;
}

.document-summary {
  justify-content: flex-start;
  margin: 16px 0;
  padding: 14px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
}

.document-badge {
  display: grid;
  place-items: center;
  width: 56px;
  height: 66px;
  border-radius: 8px;
  background: #dbeafe;
  color: #2563eb;
  font-weight: 900;
}

.document-badge--pdf { background: #dbeafe; color: #2563eb; }
.document-badge--web { background: #ccfbf1; color: #0f766e; }
.document-badge--text { background: #ffedd5; color: #c2410c; }
.document-badge--library { background: #ede9fe; color: #7c3aed; }

.document-summary h3 {
  margin: 0;
  color: var(--ide-text);
  font-size: 18px;
}

.document-summary p {
  margin: 5px 0 0;
  color: var(--ide-muted);
  font-weight: 700;
}

.block-list {
  display: grid;
  gap: 12px;
}

.document-block {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel);
  cursor: pointer;
}

.document-block.active {
  border-color: #6ee7dc;
  background: rgba(45, 212, 191, 0.1);
  box-shadow: 0 12px 30px rgba(15, 143, 137, 0.08);
}

.block-label span {
  display: block;
  color: #0f766e;
  font-size: 13px;
  font-weight: 900;
}

.block-label small {
  display: block;
  margin-top: 6px;
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
}

.source-text {
  margin: 0;
  color: var(--ide-text);
  font-size: 16px;
  line-height: 1.85;
}

.learning-layer {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.translation-card,
.insight-grid section,
.inline-note textarea,
.agent-answer,
.agent-card,
.agent-command textarea {
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel);
}

.translation-card {
  padding: 13px;
  background: rgba(45, 212, 191, 0.1);
}

.translation-card span,
.insight-grid span,
.inline-note span {
  display: inline-block;
  margin-bottom: 7px;
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 900;
}

.translation-card p {
  margin: 0;
  color: var(--ide-text);
  line-height: 1.7;
  font-weight: 700;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.insight-grid section {
  min-width: 0;
  padding: 12px;
}

.chip-list,
.agent-chip-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chip-list button,
.agent-chip-list span {
  min-height: 26px;
  padding: 0 9px;
  border: 0;
  border-radius: 999px;
  background: #e7f7f3;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.inline-note {
  display: grid;
}

.inline-note textarea,
.agent-command textarea {
  width: 100%;
  padding: 11px;
  color: #1f2937;
  line-height: 1.6;
  resize: vertical;
  box-sizing: border-box;
}

.inline-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.inline-actions button,
.agent-card-actions button {
  min-height: 32px;
  padding: 0 10px;
  cursor: pointer;
}

.agent-panel {
  position: sticky;
  top: 18px;
  display: grid;
  gap: 12px;
  max-height: calc(100vh - 120px);
  overflow: auto;
  padding: 16px;
}

.agent-panel--ide {
  display: grid;
  grid-template-rows: auto auto auto auto auto auto minmax(0, 1fr) auto;
  height: 100%;
  min-height: 0;
  max-height: none;
  padding: 0;
  background: var(--ide-panel);
  color: var(--ide-text);
  overflow: hidden;
}

.agent-header--ide,
.agent-context,
.agent-answer--ide,
.agent-card--ide,
.agent-command--ide {
  border-bottom: 1px solid #edf1f6;
}

.agent-header--ide {
  padding: 16px 18px;
  border-color: var(--ide-border);
  background: var(--ide-panel);
}

.agent-header--ide h2 {
  color: var(--ide-text);
}

.agent-header--ide span {
  color: var(--ide-muted);
}

.note-workbench-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  gap: 14px;
  min-height: 0;
  padding: 16px;
  overflow: auto;
}

.note-workbench-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.note-workbench-header p,
.note-workbench-header strong {
  margin: 0;
}

.note-workbench-header strong {
  color: var(--ide-text);
  font-size: 18px;
}

.note-workbench-header button,
.note-workbench-tabs button,
.ai-candidate-card button {
  min-height: 34px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-weight: 900;
  cursor: pointer;
}

.note-workbench-header button:hover,
.note-workbench-tabs button:hover,
.note-workbench-tabs button.active,
.ai-candidate-card button:hover:not(:disabled) {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
}

.note-workbench-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.note-workbench-composer {
  min-height: 0;
  padding: 0;
  overflow: visible;
}

.ai-candidate-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  background: #fff7ed;
}

.ai-candidate-card blockquote {
  margin: 0;
  color: #7c2d12;
  font-size: 13px;
  line-height: 1.6;
}

.ai-candidate-card button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.agent-context {
  padding: 13px 18px;
  background: #fbfffd;
}

.agent-context blockquote {
  display: -webkit-box;
  margin: 7px 0 0;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.agent-context small {
  display: block;
  margin-top: 5px;
  color: #667085;
  font-size: 11px;
  font-weight: 800;
}

.agent-answer--ide,
.agent-card--ide {
  padding: 13px 18px;
  border-right: 0;
  border-left: 0;
  border-radius: 0;
}

.study-note-panel {
  display: grid;
  gap: 10px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
}

.study-note-panel--composer-active {
  border-bottom-color: #99f6e4;
  background: linear-gradient(180deg, rgba(45, 212, 191, 0.12) 0%, var(--ide-panel) 100%);
  box-shadow: inset 3px 0 0 #14b8a6;
}

.study-note-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.study-note-panel__header strong {
  display: block;
  margin-top: 2px;
  color: var(--ide-text);
  font-size: 15px;
}

.study-note-panel__header button,
.study-note-composer__actions button,
.study-note-card__actions button {
  min-height: 30px;
  padding: 0 10px;
  cursor: pointer;
}

.study-note-composer {
  display: grid;
  gap: 9px;
}

.study-note-source {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 9px;
  border-radius: 8px;
  background: rgba(45, 212, 191, 0.1);
}

.study-note-source span {
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.study-note-source small {
  min-width: 0;
  overflow: hidden;
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.study-note-selected-text {
  max-height: 92px;
  margin: 0;
  overflow: auto;
  padding: 9px 10px;
  border: 1px solid #ccfbf1;
  border-left: 3px solid #14b8a6;
  border-radius: 8px;
  background: #ffffff;
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 800;
  line-height: 1.6;
}

.study-note-composer input,
.study-note-composer textarea {
  width: 100%;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  padding: 10px;
  background: #ffffff;
  color: var(--ide-text);
  font: inherit;
  line-height: 1.55;
}

.study-note-composer input {
  min-height: 38px;
  font-weight: 800;
}

.study-note-composer textarea {
  min-height: 104px;
  resize: vertical;
}

.study-note-panel--composer-active .study-note-composer textarea {
  min-height: 168px;
  background: #ffffff;
}

.note-agent-compose {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
}

.note-agent-compose__quick,
.note-agent-compose__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.note-agent-compose__quick {
  flex-wrap: wrap;
}

.note-agent-compose button {
  min-height: 28px;
  padding: 0 10px;
  border-color: var(--ide-border);
  background: var(--ide-panel-2);
  color: var(--ide-accent);
  font-size: 12px;
  font-weight: 900;
}

.note-agent-compose button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.note-agent-compose textarea {
  min-height: 58px;
  border-color: var(--ide-border);
  background: #ffffff;
  font-size: 12px;
}

.note-agent-compose__actions {
  justify-content: space-between;
}

.note-agent-compose__actions span {
  min-width: 0;
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
}

.study-note-composer__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.study-note-empty {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px dashed var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
}

.study-note-empty span {
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 900;
}

.study-note-empty small {
  color: var(--ide-muted);
  line-height: 1.5;
}

.study-note-list {
  display: grid;
  gap: 9px;
  max-height: 320px;
  overflow: auto;
}

.study-note-card {
  display: grid;
  gap: 7px;
  padding: 11px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
}

.study-note-card.active {
  border-color: #14b8a6;
  box-shadow: inset 3px 0 0 #14b8a6;
}

.study-note-card--draft {
  background: rgba(253, 230, 138, 0.08);
}

.study-note-card--reviewing {
  background: rgba(96, 165, 250, 0.08);
}

.study-note-card--mastered {
  background: rgba(45, 212, 191, 0.08);
}

.study-note-card__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.study-note-card__meta span,
.study-note-card__meta small,
.study-note-card__meta mark {
  font-size: 11px;
  font-weight: 900;
}

.study-note-card__meta span {
  color: var(--ide-accent);
}

.study-note-card__meta small {
  color: var(--ide-muted);
}

.study-note-card__meta mark {
  margin-left: auto;
  padding: 3px 6px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
}

.study-note-card h3 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  line-height: 1.35;
}

.study-note-card p {
  margin: 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.55;
}

.study-note-card blockquote {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  padding-left: 9px;
  border-left: 3px solid #f59e0b;
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.study-note-tags,
.study-note-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.study-note-tags span {
  padding: 3px 7px;
  border-radius: 999px;
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
  font-size: 11px;
  font-weight: 900;
}

.study-note-card__actions button {
  font-size: 12px;
}

.agent-answer--ide {
  background: var(--ide-panel);
}

.agent-toolbar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
}

.agent-toolbar button {
  padding: 0 10px;
}

.agent-conversation--ide {
  min-height: 0;
  overflow: auto;
  padding: 12px 18px;
  background: var(--ide-panel);
}

.agent-command--ide {
  padding: 12px 18px 16px;
  background: var(--ide-bg);
}

.agent-command--ide textarea {
  min-height: 96px;
  background: #ffffff;
}

.agent-header {
  align-items: flex-start;
}

.agent-header span {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
}

.agent-answer,
.agent-card {
  padding: 13px;
}

.agent-answer {
  background: var(--ide-panel);
}

.agent-answer h3 {
  margin: 7px 0;
  color: var(--ide-text);
  font-size: 15px;
  line-height: 1.45;
}

.agent-answer p,
.agent-card p {
  margin: 0;
  color: var(--ide-text);
  line-height: 1.65;
}

.capability-list {
  display: grid;
  gap: 8px;
}

.capability-list article {
  display: grid;
  gap: 3px;
  padding: 10px;
  border-radius: 8px;
  background: var(--ide-panel-2);
}

.capability-list strong {
  color: #111827;
  font-size: 13px;
}

.capability-list span {
  color: var(--ide-muted);
  font-size: 12px;
  line-height: 1.45;
}

.agent-card-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.agent-conversation {
  display: grid;
  gap: 8px;
}

.message {
  padding: 10px;
  border-radius: 8px;
  background: var(--ide-panel-2);
}

.message--assistant {
  background: rgba(45, 212, 191, 0.1);
}

.message strong {
  display: block;
  margin-bottom: 4px;
  color: #111827;
  font-size: 12px;
}

.message p {
  margin: 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.55;
}

.message-citations {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.message-citations button {
  min-height: 28px;
  padding: 5px 8px;
  border: 1px solid #b7e4db;
  border-radius: 8px;
  background: rgba(45, 212, 191, 0.1);
  color: var(--ide-accent);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.message-citations button:hover {
  border-color: #0f766e;
  background: #ccfbf1;
}

.message-save-note,
.message-append-note {
  justify-self: start;
  min-height: 28px;
  margin-top: 8px;
  padding: 5px 9px;
  color: #0f766e;
  font-size: 12px;
  cursor: pointer;
}

.message-append-note {
  margin-left: 6px;
  border-color: #99f6e4;
  background: rgba(45, 212, 191, 0.1);
}

.agent-command {
  display: grid;
  gap: 8px;
}

.agent-context,
.agent-answer--ide,
.agent-card--ide,
.study-note-panel,
.agent-toolbar,
.agent-conversation--ide,
.agent-command--ide,
.message,
.capability-list article,
.study-note-card,
.study-note-empty,
.note-agent-compose {
  border-color: var(--ide-border);
  background: var(--ide-panel);
  color: var(--ide-text);
}

.agent-context blockquote,
.agent-answer h3,
.agent-answer p,
.agent-card p,
.capability-list strong,
.message strong,
.message p,
.study-note-panel__header strong,
.study-note-empty span,
.study-note-card h3,
.study-note-card p {
  color: var(--ide-text);
}

.agent-context small,
.capability-list span,
.study-note-empty small,
.study-note-card blockquote,
.study-note-card__meta small,
.note-agent-compose__actions span {
  color: var(--ide-muted);
}

.study-note-panel--composer-active {
  border-bottom-color: rgba(45, 212, 191, 0.42);
  background: linear-gradient(180deg, rgba(45, 212, 191, 0.12) 0%, var(--ide-panel) 100%);
}

.study-note-source,
.study-note-selected-text,
.study-note-composer input,
.study-note-composer textarea,
.study-note-panel--composer-active .study-note-composer textarea,
.note-agent-compose textarea,
.agent-command--ide textarea,
.inline-note textarea,
.agent-command textarea {
  border-color: var(--ide-border);
  background: #ffffff;
  color: var(--ide-text);
}

.study-note-source span,
.study-note-card__meta span,
.study-note-tags span,
.message-citations button,
.message-save-note,
.message-append-note {
  color: var(--ide-accent);
}

.study-note-source small,
.study-note-selected-text {
  color: var(--ide-muted);
}

.note-agent-compose button,
.study-note-panel__header button,
.study-note-composer__actions button,
.study-note-card__actions button,
.agent-toolbar button,
.agent-card-actions button,
.message-citations button,
.message-save-note,
.message-append-note {
  border-color: var(--ide-border);
  background: var(--ide-panel-2);
}

.message--assistant,
.study-note-card--draft,
.study-note-card--reviewing,
.study-note-card--mastered,
.message-citations button,
.message-append-note,
.study-note-tags span {
  background: rgba(45, 212, 191, 0.1);
}

.agent-header span,
.study-note-card__meta mark {
  background: rgba(37, 99, 235, 0.18);
  color: #93c5fd;
}

.chip-list button,
.agent-chip-list span {
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
}

.workspace-status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
  padding: 0 10px;
  border: 0;
  border-radius: 0;
  background: #0f766e;
  color: #ffffff;
  box-shadow: none;
}

.workspace-status-bar span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  color: #ffffff;
  font-size: 12px;
  font-weight: 850;
  white-space: nowrap;
}

.workspace-status-bar span + span::before {
  width: 4px;
  height: 4px;
  margin-right: 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.24);
  content: '';
}

.workspace-status-bar button {
  min-height: 26px;
  margin-left: auto;
  padding: 0 10px;
  border-color: rgba(15, 143, 137, 0.28);
  background: rgba(255, 255, 255, 0.9);
  color: #0f766e;
  cursor: pointer;
}

.missing-state {
  width: min(620px, calc(100% - 48px));
  margin: 90px auto 0;
  padding: 34px;
  text-align: center;
}

.missing-state h1 {
  margin: 8px 0;
  color: #111827;
  font-size: 30px;
}

.missing-state span {
  display: block;
  margin-bottom: 18px;
  color: #667085;
  line-height: 1.6;
}

button:hover {
  transform: translateY(-1px);
}

.workspace-resizer:hover {
  transform: none;
}

button:focus-visible,
input:focus-visible,
textarea:focus-visible {
  outline: 3px solid rgba(20, 184, 166, 0.24);
  outline-offset: 2px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1440px) {
  .workspace-shell--ide {
    grid-template-columns:
      44px
      minmax(190px, 240px)
      6px
      minmax(360px, 1fr)
      6px
      minmax(300px, 340px);
  }

  .workspace-shell--outline-collapsed {
    grid-template-columns:
      44px
      0
      0
      minmax(360px, 1fr)
      6px
      minmax(300px, 340px);
  }

  .workspace-shell--agent-collapsed {
    grid-template-columns:
      44px
      minmax(190px, 240px)
      6px
      minmax(360px, 1fr)
      0
      40px;
  }

  .workspace-shell--outline-collapsed.workspace-shell--agent-collapsed {
    grid-template-columns: 44px 0 0 minmax(360px, 1fr) 0 40px;
  }

  .workspace-activity-bar {
    padding: 7px 5px;
  }

  .activity-button {
    width: 32px;
    height: 32px;
    min-height: 32px;
  }

  .workspace-tab {
    min-width: 120px;
  }

  .workspace-resizer {
    min-width: 6px;
  }

  .workspace-resizer::after {
    inset: 0 -4px;
  }

  .side-drawer-switcher {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    padding: 7px 8px;
  }

  .side-drawer-switcher button {
    padding: 0 4px;
    font-size: 11px;
  }

  .project-tree-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .project-tree {
    padding: 8px 6px 12px;
  }

  .project-tree-folder__body {
    padding-left: 14px;
  }

  .project-tree-resource {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .project-tree-resource mark {
    display: none;
  }

  .project-tree-outline .project-tree-folder__body {
    padding-left: 0;
  }

  .outline-controls {
    padding: 8px;
    gap: 7px;
  }

  .outline-filter-tabs {
    grid-template-columns: 1fr;
  }

  .outline-quick-actions {
    grid-template-columns: 1fr;
  }

  .outline-list {
    padding: 8px;
  }

  .outline-block-button {
    gap: 4px;
    padding: 7px 7px;
  }

  .outline-item-meta {
    justify-content: flex-start;
  }

  .agent-header--ide {
    padding: 10px 12px;
  }

  .agent-panel--ide {
    grid-template-rows: auto auto auto auto minmax(0, 1fr) auto;
  }
}

@media (max-width: 1180px) {
  .workspace-command-center {
    max-width: min(520px, 52vw);
  }

  .toolbar-actions {
    justify-content: flex-start;
  }

  .agent-panel {
    min-width: 0;
  }

}

@media (max-width: 760px) {
  .intensive-workspace-page {
    padding: 16px 14px 112px;
  }

  .workspace-toolbar,
  .workspace-shell {
    grid-template-columns: 1fr;
  }

  .workspace-resizer {
    display: none;
  }

  .workspace-outline-panel,
  .workspace-canvas-panel,
  .workspace-agent-panel {
    grid-column: 1;
  }

  .workspace-shell--ide,
  .workspace-shell--outline-collapsed,
  .workspace-shell--agent-collapsed,
  .workspace-shell--outline-collapsed.workspace-shell--agent-collapsed {
    grid-template-columns: 1fr;
  }

  .agent-panel {
    position: static;
    max-height: none;
  }

  .reader-heading,
  .document-summary,
  .agent-header,
  .command-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .document-block,
  .insight-grid {
    grid-template-columns: 1fr;
  }

  .workspace-status-bar {
    overflow-x: auto;
  }
}
</style>
