const api = require('../../utils/api')
const auth = require('../../utils/auth')

Page({
  data: {
    records: [],
    loading: false,
    revenue: '0.00',
    totalAdopted: 0,
    adoptionRate: 0,
    showDetailModal: false,
    detail: null,
    detailProfile: null,
    detailRecommendations: []
  },

  onShow() {
    const user = auth.checkSession()
    if (!user) {
      wx.reLaunch({
        url: '/pages/login/login'
      })
      return
    }
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
          const recList = Array.isArray(resultObj) ? resultObj : (resultObj.recommendations || [])
          if (Array.isArray(recList)) {
            dishes = recList.slice(0, 5).map(d => ({ name: d.name }))
          }
        } catch (e) {}

        return { ...r, _tags: tags, _dishes: dishes }
      })

      const totalAdopted = enriched.filter(r => r.adopted === 1).length
      const adoptionRate = enriched.length === 0 ? 0 : Math.round((totalAdopted / enriched.length) * 100)

      this.setData({
        records: enriched,
        totalAdopted,
        adoptionRate
      })

      await this.fetchRevenue()
    } catch (e) {
      console.error('加载记录失败:', e)
    } finally {
      this.setData({ loading: false })
    }
  },

  async fetchRevenue() {
    try {
      const res = await api.get('/waiter/revenue')
      const rev = res.revenue !== undefined ? parseFloat(res.revenue).toFixed(2) : '0.00'
      this.setData({ revenue: rev })
    } catch (e) {
      console.error('获取个人营业额失败', e)
    }
  },

  async openDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.showLoading({ title: '加载详情...' })
    try {
      const detail = await api.get(`/waiter/history/${id}`)

      // 解析用户画像
      let detailProfile = {}
      try {
        detailProfile = typeof detail.userProfileJson === 'string'
          ? JSON.parse(detail.userProfileJson)
          : (detail.userProfileJson || {})
        
        if (detailProfile.tastePreferences && Array.isArray(detailProfile.tastePreferences)) {
          detailProfile.tastePreferences = detailProfile.tastePreferences.join(', ')
        } else if (detailProfile.possiblePreferences && Array.isArray(detailProfile.possiblePreferences)) {
          detailProfile.tastePreferences = detailProfile.possiblePreferences.join(', ')
        }
      } catch (ex) {}

      // 解析菜品推荐列表
      let detailRecommendations = []
      try {
        const resultObj = typeof detail.resultJson === 'string'
          ? JSON.parse(detail.resultJson)
          : (detail.resultJson || {})
        const recs = Array.isArray(resultObj) ? resultObj : (resultObj.recommendations || [])
        
        const adoptedDishIds = new Set((detail.feedbacks || []).map(f => f.adoptedDishId).filter(Boolean))

        detailRecommendations = recs.map(r => {
          const isAdopted = adoptedDishIds.has(r.dishId)
          const fb = (detail.feedbacks || []).find(f => f.adoptedDishId === r.dishId)
          const qty = fb ? fb.quantity || 1 : 1
          const rankEmojis = ['🥇', '🥈', '🥉']

          return {
            ...r,
            _isAdopted: isAdopted,
            _adoptQty: qty,
            _rankEmoji: rankEmojis[r.rank - 1] || `#${r.rank}`
          }
        })
      } catch (ex) {}

      // 对 feedbacks 关联菜品名字
      if (detail.feedbacks) {
        detail.feedbacks = detail.feedbacks.map(fb => {
          const found = detailRecommendations.find(x => x.dishId === fb.adoptedDishId)
          return {
            ...fb,
            _dishName: found ? found.name : `菜品#${fb.adoptedDishId}`
          }
        })
      }

      this.setData({
        detail,
        detailProfile,
        detailRecommendations,
        showDetailModal: true
      })
    } catch (err) {
      console.error('获取详情失败:', err)
      wx.showToast({ title: '加载详情失败', icon: 'none' })
    } finally {
      wx.hideLoading()
    }
  },

  closeDetailModal() {
    this.setData({ showDetailModal: false })
  }
})
