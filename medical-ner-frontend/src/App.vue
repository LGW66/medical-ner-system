<template>
  <div style="padding: 20px; font-family: Arial;">
    <h1>医疗NER与知识图谱问答系统</h1>

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

    <div v-if="entities.length > 0" style="margin-top: 30px;">
      <h3>知识图谱</h3>
      <div ref="graphChartRef" style="width: 800px; height: 500px;"></div>
    </div>

    <!-- 智能问答区域 -->
    <div style="margin-top: 40px;">
      <h3>智能问答</h3>
      <input
        v-model="question"
        placeholder="请输入问题，例如：阿莫西林治什么？"
        style="width: 400px; padding: 8px; font-size: 14px;"
      />
      <button @click="askQuestion" style="padding: 8px 20px; margin-left: 10px;">
        提问
      </button>
      <div
        v-if="answer"
        style="margin-top: 15px; padding: 15px; background: #f5f5f5; border-radius: 8px; max-width: 700px; white-space: pre-wrap;"
      >
        <strong>回答：</strong>
        <p>{{ answer }}</p>
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
const graphChartRef = ref(null)

const question = ref('')
const answer = ref('')

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
    const content = typeof data.content === 'string' ? JSON.parse(data.content) : data.content
    entities.value = content.entities || []

    await nextTick()
    renderCharts()

    const graphRes = await axios.get(`http://localhost:8080/api/ner/graph/${data.recordId}`)
    await nextTick()
    renderGraph(graphRes.data)
  } catch (err) {
    alert('请求失败：' + err.message)
  }
}

const askQuestion = async () => {
  if (!question.value.trim()) {
    alert('请输入问题')
    return
  }
  try {
    const res = await axios.post('http://localhost:8080/api/ner/rag/ask', {
      question: question.value
    })
    answer.value = res.data
  } catch (err) {
    alert('请求失败：' + err.message)
  }
}

const renderCharts = () => {
  if (!barChartRef.value || !pieChartRef.value) {
    console.error('柱状图或饼图容器还没渲染出来')
    return
  }

  const typeCount = {}
  entities.value.forEach(e => {
    typeCount[e.type] = (typeCount[e.type] || 0) + 1
  })
  const types = Object.keys(typeCount)
  const counts = Object.values(typeCount)

  const barChart = echarts.init(barChartRef.value)
  barChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: types },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: counts, itemStyle: { color: '#5470c6' } }]
  })

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

const renderGraph = (graphData) => {
  if (!graphChartRef.value) {
    console.error('知识图谱容器还没渲染出来')
    return
  }

  const graphChart = echarts.init(graphChartRef.value)

  const categories = [...new Set(graphData.nodes.map(n => n.category))]
  const categoryIndex = {}
  categories.forEach((c, i) => { categoryIndex[c] = i })

  graphChart.setOption({
    tooltip: {
      formatter: (params) => {
        if (params.dataType === 'node') {
          return `实体：${params.data.name}<br/>类型：${params.data.category}`
        } else {
          return `关系：${params.data.relation}`
        }
      }
    },
    legend: [{ data: categories }],
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      label: { show: true },
      force: { repulsion: 300, edgeLength: 150 },
      data: graphData.nodes.map(n => ({
        name: n.name,
        category: categoryIndex[n.category],
        symbolSize: 50
      })),
      links: graphData.links.map(l => ({
        source: l.source,
        target: l.target,
        relation: l.relation,
        label: { show: true, formatter: l.relation }
      })),
      categories: categories.map(c => ({ name: c }))
    }]
  })
}
</script>