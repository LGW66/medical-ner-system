<template>
  <div style="padding: 20px; font-family: Arial;">
    <h1>医疗NER可视化系统</h1>

    <textarea
      v-model="inputText"
      rows="5"
      cols="70"
      placeholder="请输入医疗文本，例如：患者因发热就诊，诊断为肺炎，给予阿莫西林。"
      style="padding: 10px; font-size: 14px;"
    ></textarea>
    <br /><br />

    <button @click="submitText" style="padding: 8px 20px; font-size: 16px;">
      识别
    </button>

    <h3>识别结果：</h3>
    <table v-if="entities.length > 0" border="1" cellpadding="8" style="border-collapse: collapse;">
      <thead>
        <tr>
          <th>实体</th>
          <th>类型</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(entity, index) in entities" :key="index">
          <td>{{ entity.text }}</td>
          <td>{{ entity.type }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else>暂无识别结果</p>

    <!-- 图表区域 -->
    <div v-if="entities.length > 0" style="display: flex; gap: 40px; margin-top: 30px;">
      <div>
        <h3>实体类型统计</h3>
        <div ref="barChartRef" style="width: 400px; height: 300px;"></div>
      </div>
      <div>
        <h3>实体类型占比</h3>
        <div ref="pieChartRef" style="width: 400px; height: 300px;"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import axios from 'axios'
import * as echarts from 'echarts'

const inputText = ref('')
const entities = ref([])
const barChartRef = ref(null)
const pieChartRef = ref(null)

const submitText = async () => {
  if (!inputText.value.trim()) {
    alert('请输入医疗文本')
    return
  }
  try {
    const res = await axios.post('http://localhost:8080/api/ner/recognize', {
      text: inputText.value
    })
    const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
    entities.value = data.entities || []

    // 等 DOM 更新后再渲染图表
    await nextTick()
    renderCharts()
  } catch (err) {
    alert('请求失败：' + err.message)
  }
}

const renderCharts = () => {
  // 统计每种类型的数量
  const typeCount = {}
  entities.value.forEach(e => {
    typeCount[e.type] = (typeCount[e.type] || 0) + 1
  })
  const types = Object.keys(typeCount)
  const counts = Object.values(typeCount)

  // 柱状图
  const barChart = echarts.init(barChartRef.value)
  barChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: types },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      data: counts,
      itemStyle: { color: '#5470c6' }
    }]
  })

  // 饼图
  const pieChart = echarts.init(pieChartRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: '60%',
      data: types.map((type, i) => ({ name: type, value: counts[i] })),
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' }
      }
    }]
  })
}
</script>