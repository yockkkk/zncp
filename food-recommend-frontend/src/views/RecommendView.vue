<template>
  <div class="recommend-page">
    <div class="content-grid">
      <!-- 左侧：上传区域 -->
      <div class="left-panel">
        <el-card shadow="never" class="upload-card">
          <template #header>
            <div class="card-hd">
              <el-icon :size="20"><Camera /></el-icon>
              <span>上传顾客图片</span>
            </div>
          </template>

          <div class="upload-zone" :class="{ 'has-image': previewUrl }">
            <input
              ref="fileInput"
              type="file"
              accept="image/*"
              style="display:none"
              @change="onFilePicked"
            />
            <div v-if="!previewUrl" class="upload-empty" @click="$refs.fileInput.click()">
              <el-icon :size="56" color="#c0c4cc"><Plus /></el-icon>
              <p>点击或拖拽上传图片</p>
              <span>支持 JPG、PNG，建议清晰照片</span>
            </div>
            <div v-else class="upload-preview" @click="$refs.fileInput.click()">
              <img :src="previewUrl" alt="预览" />
              <div class="preview-mask">
                <el-icon :size="28"><Edit /></el-icon>
                <span>点击更换</span>
              </div>
            </div>
          </div>

          <el-button
            type="primary"
            size="large"
            :loading="loading"
            :disabled="!file"
            class="btn-recommend"
            @click="startRecommend"
          >
            <el-icon v-if="!loading"><MagicStick /></el-icon>
            {{ loading ? 'AI 正在分析推荐...' : '开始智能推荐' }}
          </el-button>
          <el-button v-if="previewUrl && !loading" size="default" text @click="resetAll">
            清除重选
          </el-button>
        </el-card>
      </div>

      <!-- 右侧：结果 -->
      <div class="right-panel">
        <template v-if="!result && !loading">
          <el-empty description="上传图片后点击「开始智能推荐」获取结果" :image-size="120" />
        </template>

        <div v-if="result" class="result-area">
          <!-- 步骤流程 -->
          <el-steps :active="2" align-center class="flow-steps">
            <el-step title="图片上传" description="OSS云端存储" />
            <el-step title="AI场景分析" description="多模态模型识别" />
            <el-step title="智能推荐" description="向量召回+AI排序" />
          </el-steps>

          <!-- 顾客画像 -->
          <el-card shadow="never" class="section-card profile-section">
            <template #header>
              <div class="card-hd">
                <el-icon :size="18" color="#409eff"><User /></el-icon>
                <span>顾客画像</span>
              </div>
            </template>
            <div class="profile-grid">
              <div class="p-item">
                <span class="p-label">用餐人数</span>
                <span class="p-val">{{ result.userProfile.peopleCount || '-' }} 人</span>
              </div>
              <div class="p-item">
                <span class="p-label">年龄段</span>
                <span class="p-val">{{ result.userProfile.ageRange || '-' }}</span>
              </div>
              <div class="p-item">
                <span class="p-label">用餐场景</span>
                <span class="p-val">{{ result.userProfile.diningScene || '-' }}</span>
              </div>
              <div class="p-item">
                <span class="p-label">消费能力</span>
                <span class="p-val">{{ result.userProfile.estimatedConsumptionLevel || '-' }}</span>
              </div>
              <div class="p-item">
                <span class="p-label">健康目标</span>
                <span class="p-val">{{ result.userProfile.healthGoal || '-' }}</span>
              </div>
              <div class="p-item">
                <span class="p-label">偏好标签</span>
                <span class="p-tags">
                  <template v-if="result.userProfile.possiblePreferences?.length">
                    <el-tag v-for="t in result.userProfile.possiblePreferences" :key="t" size="small" round>{{ t }}</el-tag>
                  </template>
                  <span v-else class="p-val">-</span>
                </span>
              </div>
            </div>
          </el-card>

          <!-- 推荐总结 -->
          <el-alert
            v-if="result.summary"
            :title="result.summary"
            type="success"
            :closable="false"
            show-icon
          />

          <!-- 推荐菜品 -->
          <el-card shadow="never" class="section-card">
            <template #header>
              <div class="card-hd">
                <el-icon :size="18" color="#e6a23c"><Star /></el-icon>
                <span>推荐菜品（{{ result.recommendations?.length || 0 }}道）</span>
              </div>
            </template>

            <div v-for="(dish, i) in result.recommendations" :key="dish.dishId" class="dish-row">
              <div class="dish-rank" :class="'rank-' + (i + 1)">
                <span v-if="i === 0">🥇</span>
                <span v-else-if="i === 1">🥈</span>
                <span v-else-if="i === 2">🥉</span>
                <span v-else>#{{ i + 1 }}</span>
              </div>
              <div class="dish-body">
                <div class="dish-top">
                  <h3>{{ dish.name }}</h3>
                  <div class="dish-score">{{ dish.score }}<small> 分</small></div>
                </div>
                <div class="dish-tags">
                  <el-tag type="warning" size="small" round>¥{{ dish.price }}</el-tag>
                  <el-tag size="small" round>{{ dish.calories }} kcal</el-tag>
                  <el-tag type="success" size="small" round>蛋白 {{ dish.protein }}g</el-tag>
                </div>
                <ul class="dish-reasons">
                  <li v-if="dish.reason"><i>推荐：</i>{{ dish.reason }}</li>
                  <li v-if="dish.nutritionComment"><i>营养：</i>{{ dish.nutritionComment }}</li>
                  <li v-if="dish.costPerformanceComment"><i>性价比：</i>{{ dish.costPerformanceComment }}</li>
                </ul>
              </div>
            </div>
          </el-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, MagicStick, Plus, Edit, User, Star } from '@element-plus/icons-vue'
import api from '../api'

const fileInput = ref(null)
const file = ref(null)
const previewUrl = ref('')
const loading = ref(false)
const result = ref(null)

function onFilePicked(e) {
  const f = e.target.files[0]
  if (!f) return
  file.value = f
  previewUrl.value = URL.createObjectURL(f)
  result.value = null
}

function resetAll() {
  file.value = null
  previewUrl.value = ''
  result.value = null
  if (fileInput.value) fileInput.value.value = ''
}

async function startRecommend() {
  if (!file.value) {
    ElMessage.warning('请先选择图片')
    return
  }
  loading.value = true
  result.value = null
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    const res = await api.post('/recommend/image', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    result.value = res.data
    ElMessage.success('推荐完成')
  } catch (e) {
    ElMessage.error(e.message || '推荐失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.recommend-page { max-width: 1200px; margin: 0 auto; }
.content-grid { display: grid; grid-template-columns: 380px 1fr; gap: 24px; align-items: start; }

.upload-card { border-radius: 12px; }
.upload-zone { border: 2px dashed #dcdfe6; border-radius: 12px; cursor: pointer; overflow: hidden; transition: border-color .2s; }
.upload-zone:hover { border-color: #409eff; }
.upload-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; color: #909399; }
.upload-empty p { margin: 12px 0 4px; font-size: 14px; color: #606266; }
.upload-empty span { font-size: 12px; color: #c0c4cc; }
.upload-preview { position: relative; }
.upload-preview img { width: 100%; max-height: 260px; object-fit: cover; display: block; }
.preview-mask { position: absolute; inset: 0; background: rgba(0,0,0,.4); display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; opacity: 0; transition: opacity .2s; }
.upload-preview:hover .preview-mask { opacity: 1; }

.btn-recommend { width: 100%; margin-top: 16px; height: 44px; font-size: 16px; }

.card-hd { display: flex; align-items: center; gap: 8px; font-weight: 600; }

.flow-steps { margin-bottom: 24px; }

.section-card { border-radius: 12px; margin-bottom: 16px; }

.profile-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.p-item { display:flex; flex-direction:column; gap:2px; }
.p-label { font-size: 12px; color: #909399; }
.p-val { font-size: 14px; font-weight: 500; color: #303133; }
.p-tags { display:flex; flex-wrap:wrap; gap:4px; }

.dish-row { display:flex; gap:16px; padding:16px; border:1px solid #ebeef5; border-radius:10px; margin-bottom:12px; transition: box-shadow .2s; }
.dish-row:hover { box-shadow: 0 2px 12px rgba(0,0,0,.06); }
.dish-rank { font-size:24px; min-width:40px; text-align:center; line-height:1.4; }
.rank-1 { color: #f56c6c; }
.rank-2 { color: #e6a23c; }
.rank-3 { color: #67c23a; }
.dish-body { flex:1; }
.dish-top { display:flex; justify-content:space-between; align-items:center; margin-bottom:6px; }
.dish-top h3 { margin:0; font-size:15px; }
.dish-score { font-size:20px; font-weight:700; color:#e6a23c; }
.dish-score small { font-size:11px; color:#909399; }
.dish-tags { display:flex; gap:6px; margin-bottom:8px; }
.dish-reasons { list-style:none; padding:0; margin:0; font-size:13px; color:#606266; line-height:1.8; }
.dish-reasons i { color:#909399; font-style:normal; }

@media (max-width: 860px) {
  .content-grid { grid-template-columns: 1fr; }
  .profile-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
