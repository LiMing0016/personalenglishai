import { onBeforeUnmount, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECBasicOption } from 'echarts/types/dist/shared'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

export function useAdminChart() {
  const instance = shallowRef<echarts.ECharts | null>(null)

  function mount(element: HTMLElement | null) {
    if (!element) return null
    if (instance.value) {
      instance.value.dispose()
    }
    instance.value = echarts.init(element)
    return instance.value
  }

  function setOption(option: ECBasicOption) {
    instance.value?.setOption(option)
  }

  function resize() {
    instance.value?.resize()
  }

  function dispose() {
    instance.value?.dispose()
    instance.value = null
  }

  onBeforeUnmount(dispose)

  return {
    mount,
    setOption,
    resize,
    dispose,
  }
}
