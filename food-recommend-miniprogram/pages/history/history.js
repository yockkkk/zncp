const api = require('../../utils/api')

Page({
  data: {
    records: [],
    loading: false
  },

  onShow() {
    this.fetchHistory()
  },

  async fetchHistory() {
    this.setData({ loading: true })
    try {
      const records = await api.get('/waiter/history')
      // 解析 JSON 字段，简化展示
      const enriched = records.map(r => {
        let tags = []
        let dishes = []
        try {
          const tagObj = JSON.parse(r.tagInputJson || '{}')
          if (tagObj.peopleCount) tags.push(tagObj.peopleCount + '人')
          if (tagObj.diningScene) tags.push(tagObj.diningScene)
          if (tagObj.budgetLevel) tags.push(tagObj.budgetLevel)
          if (tagObj.mealTime) tags.push(tagObj.mealTime)
        } catch (e) {}

        try {
          const resultObj = JSON.parse(r.resultJson || '[]')
          if (Array.isArray(resultObj)) {
            dishes = resultObj.slice(0, 5).map(d => ({ name: d.name }))
          }
        } catch (e) {}

        return { ...r, _tags: tags, _dishes: dishes }
      })
      this.setData({ records: enriched })
    } catch (e) {
      console.error('加载记录失败:', e)
    } finally {
      this.setData({ loading: false })
    }
  }
})
