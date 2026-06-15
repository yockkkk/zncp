import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useRecommendStore = defineStore('recommend', () => {
  const file = ref(null)
  const previewUrl = ref('')
  const result = ref(null)
  const loading = ref(false)
  const remark = ref('')
  const finalRemark = ref('')
  const selectedTags = ref([])
  const currentStep = ref(0)

  function reset() {
    file.value = null
    previewUrl.value = ''
    result.value = null
    loading.value = false
    remark.value = ''
    finalRemark.value = ''
    selectedTags.value = []
    currentStep.value = 0
  }

  return {
    file, previewUrl, result, loading,
    remark, finalRemark, selectedTags, currentStep,
    reset
  }
})
