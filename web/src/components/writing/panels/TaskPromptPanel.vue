<template>
  <div class="task-prompt-panel">
    <div class="paper-frame">
      <div class="paper-sheet paper-sheet--canvas">
        <template v-if="panelState.sheet">
          <div v-if="panelState.taskTypeLabel" class="paper-task-center">
            <p class="paper-task-title">{{ panelState.taskTypeLabel }}</p>
          </div>

          <h3 class="paper-directions">{{ panelState.sheet.directions }}</h3>

          <div class="paper-block">
            <p class="paper-prompt">{{ panelState.sheet.promptText }}</p>
            <ul v-if="panelState.sheet.requirements.length" class="paper-requirements">
              <li v-for="(requirement, index) in panelState.sheet.requirements" :key="`req-${index}`">{{ requirement }}</li>
            </ul>
            <p v-if="panelState.sheet.wordRange || panelState.sheet.score" class="paper-meta">
              <span v-if="panelState.sheet.wordRange">字数要求：{{ panelState.sheet.wordRange }} 词</span>
              <span v-if="panelState.sheet.score">满分：{{ panelState.sheet.score }} 分</span>
            </p>
          </div>

          <div
            v-if="panelState.sheet.attachmentType !== 'none'"
            class="paper-block paper-attachment"
            :class="{
              'paper-attachment--image': panelState.visualPreview.mode === 'image' && Boolean(panelState.visualPreview.imageUrl),
            }"
          >
            <p class="paper-attachment-heading">
              {{ panelState.visualPreview.title || (panelState.sheet.attachmentType === 'material' ? 'Material' : 'Visual Attachment') }}
            </p>

            <img
              v-if="panelState.visualPreview.mode === 'image' && panelState.visualPreview.imageUrl"
              :src="panelState.visualPreview.imageUrl"
              class="paper-attachment-image"
              alt="题目附件"
            />

            <div
              v-else-if="
                (panelState.visualPreview.mode === 'chart' || panelState.visualPreview.mode === 'table')
                  && panelState.visualPreview.chartSpec
              "
              class="paper-chart"
            >
              <div
                v-if="!shouldRenderChartAsTable(panelState.visualPreview.chartSpec) && panelChartFigure.series.length"
                class="paper-chart-figure"
              >
                <svg class="paper-chart-svg" viewBox="0 0 100 100" role="img" aria-label="图表预览">
                  <line x1="8" y1="86" x2="94" y2="86" class="paper-chart-axis" />
                  <line x1="10" y1="14" x2="10" y2="88" class="paper-chart-axis" />
                  <polyline
                    v-for="series in panelChartFigure.series"
                    :key="series.name"
                    class="paper-chart-line"
                    :points="series.polyline"
                    :stroke="series.color"
                  />
                  <g v-for="series in panelChartFigure.series" :key="`${series.name}-points`">
                    <circle
                      v-for="point in series.points"
                      :key="`${series.name}-${point.label}`"
                      class="paper-chart-point"
                      :cx="point.x"
                      :cy="point.y"
                      r="2.2"
                      :fill="series.color"
                    />
                  </g>
                </svg>
                <div class="paper-chart-x-labels">
                  <span v-for="label in panelChartFigure.labels" :key="label">{{ label }}</span>
                </div>
                <div class="paper-chart-legend">
                  <span v-for="series in panelChartFigure.series" :key="`${series.name}-legend`">
                    <i :style="{ backgroundColor: series.color }" />
                    {{ series.name }}
                  </span>
                </div>
              </div>

              <div
                v-else-if="shouldRenderChartAsTable(panelState.visualPreview.chartSpec) || panelState.visualPreview.chartSpec.rows.length > 0"
                class="paper-chart-table-wrap"
              >
                <table class="paper-chart-table">
                  <thead>
                    <tr>
                      <th v-for="column in panelState.visualPreview.chartSpec.columns" :key="column">{{ column }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="(row, rowIndex) in panelState.visualPreview.chartSpec.rows"
                      :key="`row-${rowIndex}`"
                    >
                      <td v-for="(cell, cellIndex) in row" :key="`cell-${rowIndex}-${cellIndex}`">{{ cell }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <p
                v-if="panelState.visualPreview.chartSpec.summary"
                class="paper-chart-summary"
              >
                {{ panelState.visualPreview.chartSpec.summary }}
              </p>
            </div>

            <div v-else-if="panelState.visualPreview.text" class="paper-attachment-content">
              {{ panelState.visualPreview.text }}
            </div>

            <div v-else-if="panelState.sheet.attachmentContent" class="paper-attachment-content">
              {{ panelState.sheet.attachmentContent }}
            </div>
          </div>
        </template>

        <template v-else>
          <div class="paper-empty">
            <p class="paper-empty-label">题单暂不可用</p>
            <p class="paper-empty-text">当前会话没有恢复出完整题单内容。</p>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { buildTaskPromptPanelState } from '../taskPromptPanelState'
import { buildChartPreviewFigure, shouldRenderChartAsTable } from '../../../pages/app/examPromptHelpers'

const props = defineProps<{
  writingMode: 'free' | 'exam'
  taskPrompt: string
  attachmentImageUrl?: string | null
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
  maxScore?: number | null
  studyStage?: string | null
}>()

const panelState = computed(() =>
  buildTaskPromptPanelState({
    writingMode: props.writingMode,
    taskPrompt: props.taskPrompt,
    attachmentImageUrl: props.attachmentImageUrl ?? null,
    taskType: props.taskType,
    minWords: props.minWords,
    recommendedMaxWords: props.recommendedMaxWords,
    maxScore: props.maxScore,
    studyStage: props.studyStage,
  }),
)

const panelChartFigure = computed(() =>
  buildChartPreviewFigure(panelState.value.visualPreview.chartSpec),
)
</script>

<style scoped>
.task-prompt-panel {
  flex: 1;
  min-height: 0;
  padding: 18px 16px 24px;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top left, rgba(122, 195, 175, 0.14), transparent 38%),
    linear-gradient(180deg, #f7fbfd 0%, #ffffff 52%);
}

.paper-frame {
  min-height: 100%;
  padding: 18px;
  border-radius: 28px;
  background: #f8fbfd;
}

.paper-sheet {
  min-height: 100%;
  padding: 32px 30px;
  border-radius: 18px;
  background: #fffefb;
  border: 1px solid #ebe4d8;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.paper-sheet--canvas {
  overflow-y: auto;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.55) transparent;
}

.paper-sheet--canvas::-webkit-scrollbar {
  width: 10px;
}

.paper-sheet--canvas::-webkit-scrollbar-track {
  background: transparent;
}

.paper-sheet--canvas::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.45);
  border-radius: 999px;
  border: 2px solid transparent;
  background-clip: padding-box;
}

.paper-task-center {
  display: flex;
  justify-content: center;
  margin-bottom: 18px;
}

.paper-task-title {
  margin: 0;
  color: #334155;
  font-size: 19px;
  font-weight: 800;
}

.paper-directions {
  margin: 0 0 22px;
  color: #0f172a;
  font-size: 32px;
  line-height: 1.15;
  font-family: Georgia, "Times New Roman", serif;
}

.paper-block {
  margin-top: 22px;
}

.paper-prompt,
.paper-meta,
.paper-attachment-heading,
.paper-attachment-content,
.paper-requirements li {
  font-family: Georgia, "Times New Roman", serif;
}

.paper-prompt,
.paper-attachment-content {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.paper-requirements {
  margin: 18px 0 0 24px;
  padding: 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 1.72;
}

.paper-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 18px;
  font-size: 18px;
  line-height: 1.6;
}

.paper-attachment {
  border-top: 1px solid #ebe4d8;
  padding-top: 26px;
}

.paper-attachment-heading {
  margin: 0 0 14px;
  color: #8a6d3b;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.paper-attachment-image {
  display: block;
  width: 100%;
  height: auto;
  object-fit: contain;
  border-radius: 12px;
  border: 1px solid #ebe4d8;
  background: #fff;
  margin-bottom: 14px;
}

.paper-attachment--image .paper-attachment-image {
  width: calc(100% + 60px);
  max-width: none;
  margin-left: -30px;
  margin-right: -30px;
}

.paper-chart {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.paper-chart-figure {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 16px 14px;
  border: 1px solid #ebe4d8;
  border-radius: 14px;
  background: #fff;
}

.paper-chart-svg {
  width: 100%;
  height: 220px;
  overflow: visible;
}

.paper-chart-axis {
  stroke: #cbd5e1;
  stroke-width: 0.8;
}

.paper-chart-line {
  fill: none;
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.paper-chart-point {
  stroke: #fff;
  stroke-width: 0.8;
}

.paper-chart-x-labels,
.paper-chart-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  color: #475569;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 14px;
  line-height: 1.5;
}

.paper-chart-x-labels {
  justify-content: space-between;
}

.paper-chart-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.paper-chart-legend i {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}

.paper-chart-table-wrap {
  overflow-x: auto;
  border: 1px solid #ebe4d8;
  border-radius: 14px;
  background: #fff;
}

.paper-chart-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 560px;
  font-family: Georgia, "Times New Roman", serif;
  color: #0f172a;
}

.paper-chart-table th,
.paper-chart-table td {
  padding: 14px 16px;
  border-bottom: 1px solid #ebe4d8;
  text-align: left;
  font-size: 16px;
  line-height: 1.5;
  white-space: nowrap;
}

.paper-chart-table thead th {
  background: #f8f4eb;
  font-weight: 700;
}

.paper-chart-table tbody tr:last-child td {
  border-bottom: none;
}

.paper-chart-summary {
  margin: 0;
  color: #475569;
  font-size: 16px;
  line-height: 1.8;
  font-family: Georgia, "Times New Roman", serif;
}

.paper-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  text-align: center;
  color: #64748b;
}

.paper-empty-label {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 24px;
  font-weight: 800;
}

.paper-empty-text {
  margin: 0;
  font-size: 16px;
  line-height: 1.7;
}

@media (max-width: 960px) {
  .paper-sheet {
    padding: 24px 20px;
  }

  .paper-directions {
    font-size: 26px;
  }

  .paper-prompt,
  .paper-attachment-content,
  .paper-requirements li {
    font-size: 18px;
  }
}
</style>
