<template>
  <div class="staff-page">
    <div class="toolbar">
      <h2 class="page-title">员工管理</h2>
      <el-button type="primary" @click="openAdd">+ 添加员工</el-button>
    </div>

    <el-table :data="staff" stripe v-loading="loading" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-popconfirm title="确定删除该员工？" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加对话框 -->
    <el-dialog v-model="dialogVisible" title="添加员工" width="450px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">确认添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const staff = ref([])
const form = reactive({ username: '', password: '', realName: '', phone: '' })

async function fetchStaff() {
  loading.value = true
  try {
    const res = await api.get('/owner/staff')
    staff.value = res.data
  } catch (e) { /* handled */ }
  finally { loading.value = false }
}

function openAdd() {
  form.username = ''; form.password = ''; form.realName = ''; form.phone = ''
  dialogVisible.value = true
}

async function doSave() {
  if (!form.username || !form.password) {
    ElMessage.warning('用户名和密码不能为空')
    return
  }
  saving.value = true
  try {
    await api.post('/owner/staff', { ...form })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchStaff()
  } catch (e) { /* handled */ }
  finally { saving.value = false }
}

async function toggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await api.put(`/owner/staff/${row.id}/status`, { status: newStatus })
    row.status = newStatus
    ElMessage.success('操作成功')
  } catch (e) { /* handled */ }
}

async function doDelete(id) {
  try {
    await api.delete(`/owner/staff/${id}`)
    ElMessage.success('删除成功')
    fetchStaff()
  } catch (e) { /* handled */ }
}

onMounted(fetchStaff)
</script>

<style scoped>
.staff-page { max-width: 1000px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { font-size: 20px; margin: 0; }
</style>
