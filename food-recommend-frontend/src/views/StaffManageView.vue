<template>
  <div class="staff-page">
    <div class="page-header">
      <div class="header-info">
        <h2 class="page-title">员工管理</h2>
        <span class="staff-count">共 {{ staff.length }} 名员工</span>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">添加员工</el-button>
    </div>

    <!-- 空状态 -->
    <el-empty v-if="!loading && staff.length === 0" description="暂无员工，点击上方按钮添加" />

    <!-- 员工表格 -->
    <el-table v-else :data="staff" stripe v-loading="loading" row-key="id" style="width:100%">
      <el-table-column type="index" label="#" width="60" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="realName" label="姓名" min-width="120" />
      <el-table-column prop="phone" label="手机号" min-width="140">
        <template #default="{ row }">{{ row.phone || '未填写' }}</template>
      </el-table-column>
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'OWNER' ? 'warning' : 'info'" size="small" effect="plain">
            {{ row.role === 'OWNER' ? '管理员' : '服务员' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" plain
            @click="toggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-popconfirm title="确定删除该员工？" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger" plain>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加对话框 -->
    <el-dialog v-model="dialogVisible" title="添加员工" width="480px" destroy-on-close>
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="80px" status-icon>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="登录账号" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="至少6位" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '../api'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const staff = ref([])
const form = reactive({ username: '', password: '', realName: '', phone: '' })
const formRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名3-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

async function fetchStaff() {
  loading.value = true
  try {
    const res = await api.get('/owner/staff')
    staff.value = res.data || []
  } catch (e) { /* handled */ }
  finally { loading.value = false }
}

function openAdd() {
  form.username = ''; form.password = ''; form.realName = ''; form.phone = ''
  dialogVisible.value = true
}

async function doSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await api.post('/owner/staff', { ...form })
    ElMessage.success('员工创建成功')
    dialogVisible.value = false
    fetchStaff()
  } catch (e) { /* handled */ }
  finally { saving.value = false }
}

async function toggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定要${action}员工「${row.realName}」吗？`,
      `${action}确认`,
      { confirmButtonText: `确认${action}`, cancelButtonText: '取消', type: 'warning' }
    )
    await api.put(`/owner/staff/${row.id}/status`, { status: newStatus })
    row.status = newStatus
    ElMessage.success(`已${action}`)
  } catch (e) {
    if (e !== 'cancel') { /* API error handled by interceptor */ }
  }
}

async function doDelete(id) {
  // el-popconfirm already handles the confirmation UI
  try {
    await api.delete(`/owner/staff/${id}`)
    ElMessage.success('删除成功')
    fetchStaff()
  } catch (e) { /* handled */ }
}

onMounted(fetchStaff)
</script>

<style scoped>
.staff-page { width: 100%; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 24px;
}
.header-info { display: flex; align-items: baseline; gap: 12px; }
.page-title { font-size: 20px; margin: 0; font-weight: 600; }
.staff-count { font-size: 13px; color: #909399; }
</style>
