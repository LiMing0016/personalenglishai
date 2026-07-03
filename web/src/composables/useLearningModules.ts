import { computed, type Ref } from 'vue'

import type {
  LearningModule,
  LearningModuleGroup,
  LearningModuleGroupId,
} from '../types/learningIde'

const groupLabels: Record<LearningModuleGroupId, Pick<LearningModuleGroup, 'id' | 'label' | 'description'>> = {
  base: {
    id: 'base',
    label: '基础工具',
    description: '默认适合所有学习场景的模块。',
  },
  language: {
    id: 'language',
    label: '语言学习',
    description: '按需添加单词卡、表达卡和语言材料。',
  },
  practice: {
    id: 'practice',
    label: '应试学习',
    description: '按需添加错题、题库和复习训练。',
  },
  research: {
    id: 'research',
    label: '高阶学习',
    description: '按需添加论文卡和专题研究工具。',
  },
}

export function useLearningModules(modules: Ref<LearningModule[]>) {
  const enabledModules = computed(() => modules.value.filter((module) => module.status === 'enabled'))
  const availableModules = computed(() => modules.value.filter((module) => module.status !== 'enabled'))
  const groups = computed(() => buildLearningModuleGroups(modules.value))

  return {
    availableModules,
    enabledModules,
    groups,
  }
}

export function buildLearningModuleGroups(modules: LearningModule[]): LearningModuleGroup[] {
  return (Object.keys(groupLabels) as LearningModuleGroupId[])
    .map((groupId) => ({
      ...groupLabels[groupId],
      modules: modules.filter((module) => module.groupId === groupId),
    }))
    .filter((group) => group.modules.length > 0)
}
