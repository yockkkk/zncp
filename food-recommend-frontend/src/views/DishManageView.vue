<template>
  <div class="dish-page">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="search" placeholder="搜索菜品..." clearable style="width:240px" :prefix-icon="Search" />
        <el-select v-model="filterCategory" placeholder="分类筛选" clearable style="width:140px">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" :icon="Plus" @click="openAdd">新增菜品</el-button>
        <el-button :icon="RefreshRight" @click="fetchDishes">刷新</el-button>
      </div>
    </div>

    <el-table :data="filteredDishes" stripe v-loading="loadingTable" style="width:100%" row-key="id">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="菜品名称" width="140" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="price" label="价格" width="80">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>
      <el-table-column prop="calories" label="热量" width="80">
        <template #default="{ row }">{{ row.calories }} kcal</template>
      </el-table-column>
      <el-table-column prop="protein" label="蛋白质" width="80">
        <template #default="{ row }">{{ row.protein }}g</template>
      </el-table-column>
      <el-table-column prop="taste" label="口味" width="80" />
      <el-table-column prop="sales" label="销量" width="70" />
      <el-table-column label="向量" width="80">
        <template #default="{ row }">
          <el-tag :type="row.vectorStatus === 1 ? 'success' : 'info'" size="small">
            {{ row.vectorStatus === 1 ? '已生成' : '未生成' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确定删除该菜品？" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button size="small" text type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑菜品' : '新增菜品'" width="600px" destroy-on-close>
      <el-form :model="form" label-width="90px" label-position="left">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类"><el-input v-model="form.category" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="价格"><el-input-number v-model="form.price" :precision="2" :min="0" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="热量"><el-input-number v-model="form.calories" :min="0" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="蛋白质"><el-input-number v-model="form.protein" :precision="1" :min="0" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="脂肪"><el-input-number v-model="form.fat" :precision="1" :min="0" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="碳水"><el-input-number v-model="form.carbohydrate" :precision="1" :min="0" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销量"><el-input-number v-model="form.sales" :min="0" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="口味"><el-input v-model="form.taste" /></el-form-item>
        <el-form-item label="适合人群"><el-input v-model="form.suitablePeople" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="推荐场景"><el-input v-model="form.scene" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">
          {{ editingId ? '保存修改' : '确认新增' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, RefreshRight } from '@element-plus/icons-vue'
import api from '../api'

const dishes = ref([])
const loadingTable = ref(false)
const saving = ref(false)
const search = ref('')
const filterCategory = ref('')
const dialogVisible = ref(false)
const editingId = ref(null)

const form = ref({})

const categories = computed(() => [...new Set(dishes.value.map(d => d.category).filter(Boolean))])
const filteredDishes = computed(() => {
  let list = dishes.value
  if (search.value) list = list.filter(d => d.name?.includes(search.value) || d.description?.includes(search.value))
  if (filterCategory.value) list = list.filter(d => d.category === filterCategory.value)
  return list
})

async function fetchDishes() {
  loadingTable.value = true
  try {
    const res = await api.get('/admin/dish')
    dishes.value = res.data || []
  } catch (e) {
    ElMessage.error('加载菜品失败')
  } finally { loadingTable.value = false }
}

function openAdd() {
  editingId.value = null
  form.value = { price: 0, calories: 0, protein: 0, fat: 0, carbohydrate: 0, sales: 0 }
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

async function doSave() {
  saving.value = true
  try {
    if (editingId.value) {
      await api.put('/admin/dish/' + editingId.value, form.value)
      ElMessage.success('修改成功')
    } else {
      await api.post('/admin/dish', form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchDishes()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally { saving.value = false }
}

async function doDelete(id) {
  try {
    await api.delete('/admin/dish/' + id)
    ElMessage.success('已删除')
    fetchDishes()
  } catch (e) { ElMessage.error('删除失败') }
}

onMounted(fetchDishes)
</script>

<style scoped>
.dish-page { width: 100%; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; }
.toolbar-left { display: flex; gap: 12px; }
.toolbar-right { display: flex; gap: 8px; }
</style>
