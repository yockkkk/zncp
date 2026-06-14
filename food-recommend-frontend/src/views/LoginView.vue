<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">智能餐饮推荐系统</h1>
      <p class="login-subtitle">餐厅智能推荐助手</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import api from '../api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await api.post('/auth/login', {
      username: form.username,
      password: form.password
    })
    auth.setAuth(res.data.token, res.data)
    ElMessage.success('登录成功')
    router.push(auth.getDefaultPath())
  } catch (e) {
    // 错误消息已由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #1d1e2c 0%, #2d3a4a 100%);
}
.login-card {
  width: 400px;
  padding: 48px 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.login-title {
  text-align: center;
  font-size: 24px;
  color: #303133;
  margin-bottom: 8px;
}
.login-subtitle {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin-bottom: 40px;
}
.login-btn {
  width: 100%;
}
</style>
