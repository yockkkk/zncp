<template>
  <div class="login-container">
    <!-- 左侧品牌区 -->
    <div class="login-left">
      <div class="left-overlay"></div>
      <div class="left-content">
        <div class="brand-icon">
          <svg viewBox="0 0 80 80" width="80" height="80">
            <circle cx="40" cy="40" r="38" fill="none" stroke="rgba(255,255,255,0.4)" stroke-width="2"/>
            <path d="M25 35 Q40 15 55 35 Q60 45 50 55 Q40 65 30 55 Q20 45 25 35Z" fill="rgba(255,255,255,0.9)"/>
            <circle cx="40" cy="38" r="8" fill="#409eff"/>
            <line x1="30" y1="55" x2="25" y2="65" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/>
            <line x1="50" y1="55" x2="55" y2="65" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/>
          </svg>
        </div>
        <h1 class="brand-title">智能餐饮推荐系统</h1>
        <p class="brand-desc">
          基于多模态大模型与向量数据库<br/>
          一张图片读懂顾客，一套算法精准推荐
        </p>
        <div class="brand-features">
          <div class="feature-item">
            <span class="feature-dot"></span>5 Agent 多智能体协同
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>标签 + 场景双输入模式
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>AI 生成服务员推荐话术
          </div>
        </div>
      </div>
      <div class="left-bottom-text">© 2024 Smart F&B Recommendation · AI Driven</div>
    </div>

    <!-- 右侧表单区 -->
    <div class="login-right">
      <div class="login-form-wrapper">
        <div class="form-header">
          <h2 class="form-title">欢迎回来</h2>
      <p class="form-subtitle">登录您的账号以继续</p>
        </div>

        <!-- 登录方式切换 -->
        <div class="login-tabs">
          <div
            class="tab-item"
            :class="{ active: loginMode === 'password' }"
            @click="switchMode('password')"
          >密码登录</div>
          <div
            class="tab-item"
            :class="{ active: loginMode === 'sms' }"
            @click="switchMode('sms')"
          >验证码登录</div>
        </div>

        <!-- 密码登录表单 -->
        <el-form v-if="loginMode === 'password'" ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" size="large">
          <el-form-item prop="username">
            <el-input
              v-model="pwdForm.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="pwdForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handlePwdLogin"
            />
          </el-form-item>

          <!-- 滑块验证 -->
          <el-form-item prop="captcha">
            <SliderCaptcha ref="captchaRef" @verify="onCaptchaPass" />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              :disabled="!captchaOk"
              class="submit-btn"
              @click="handlePwdLogin"
            >登 录</el-button>
          </el-form-item>
        </el-form>

        <!-- 验证码登录表单 -->
        <el-form v-if="loginMode === 'sms'" ref="smsFormRef" :model="smsForm" :rules="smsRules" size="large">
          <el-form-item prop="phone">
            <el-input
              v-model="smsForm.phone"
              placeholder="请输入手机号"
              :prefix-icon="Phone"
              clearable
            />
          </el-form-item>
          <el-form-item prop="code">
            <div class="sms-row">
              <el-input
                v-model="smsForm.code"
                placeholder="请输入验证码"
                :prefix-icon="Message"
                class="sms-input"
              />
              <el-button
                type="primary"
                :disabled="codeCountdown > 0"
                class="sms-btn"
                plain
                @click="sendCode"
              >
                {{ codeCountdown > 0 ? codeCountdown + 's' : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item>
            <SliderCaptcha ref="captchaRef2" @verify="onCaptchaPass" />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              :disabled="!captchaOk"
              class="submit-btn"
              @click="handleSmsLogin"
            >登 录</el-button>
          </el-form-item>
        </el-form>

        <div class="form-footer">
          <span>测试账号：admin / admin123</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Phone, Message } from '@element-plus/icons-vue'
import SliderCaptcha from '../components/SliderCaptcha.vue'
import api from '../api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const loginMode = ref('password')
const captchaOk = ref(false)
const captchaRef = ref(null)
const captchaRef2 = ref(null)

// 密码登录表单
const pwdForm = reactive({ username: '', password: '' })
const pwdRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 短信登录表单
const smsForm = reactive({ phone: '', code: '' })
const smsRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}
const codeCountdown = ref(0)

function switchMode(mode) {
  loginMode.value = mode
  captchaOk.value = false
  if (captchaRef.value) captchaRef.value.reset()
  if (captchaRef2.value) captchaRef2.value.reset()
}

function onCaptchaPass() {
  captchaOk.value = true
}

function sendCode() {
  if (!smsForm.phone || !/^1[3-9]\d{9}$/.test(smsForm.phone)) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }
  // 模拟发送验证码
  ElMessage.success('验证码已发送（演示模式，请输入 888888）')
  codeCountdown.value = 60
  const timer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function handlePwdLogin() {
  if (!pwdForm.username || !pwdForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (!captchaOk.value) {
    ElMessage.warning('请先完成滑块验证')
    return
  }
  await doLogin(pwdForm.username, pwdForm.password)
}

async function handleSmsLogin() {
  if (!smsForm.phone || !smsForm.code) {
    ElMessage.warning('请输入手机号和验证码')
    return
  }
  if (!captchaOk.value) {
    ElMessage.warning('请先完成滑块验证')
    return
  }
  if (smsForm.code !== '888888') {
    ElMessage.error('验证码错误（演示模式请输入 888888）')
    return
  }
  // 短信登录：用固定测试账号
  await doLogin('admin', 'admin123')
}

async function doLogin(username, password) {
  loading.value = true
  try {
    const res = await api.post('/auth/login', { username, password })
    auth.setAuth(res.data.token, res.data)
    ElMessage.success('登录成功，欢迎回来！')
    router.push(auth.getDefaultPath())
  } catch (e) {
    // 重置滑块
    captchaOk.value = false
    if (captchaRef.value) captchaRef.value.reset()
    if (captchaRef2.value) captchaRef2.value.reset()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  min-height: 100vh;
}
/* ========== 左侧品牌区 ========== */
.login-left {
  flex: 1;
  position: relative;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 40%, #0f3460 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  min-width: 420px;
}
.left-overlay {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 30%, rgba(64,158,255,0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 70%, rgba(103,194,58,0.1) 0%, transparent 50%);
}
.left-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 40px;
  max-width: 460px;
}
.brand-icon { margin-bottom: 28px; }
.brand-title {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 16px;
  letter-spacing: 2px;
}
.brand-desc {
  font-size: 15px;
  color: rgba(255,255,255,0.65);
  line-height: 1.8;
  margin: 0 0 40px;
}
.brand-features {
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.feature-item {
  color: rgba(255,255,255,0.75);
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.feature-dot {
  width: 6px; height: 6px;
  background: #409eff;
  border-radius: 50%;
  flex-shrink: 0;
}
.left-bottom-text {
  position: absolute;
  bottom: 24px;
  left: 0; right: 0;
  text-align: center;
  color: rgba(255,255,255,0.3);
  font-size: 12px;
}
/* ========== 右侧表单区 ========== */
.login-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fb;
  min-width: 400px;
}
.login-form-wrapper {
  width: 400px;
  padding: 48px 40px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.06);
}
.form-header { text-align: center; margin-bottom: 32px; }
.form-title {
  font-size: 26px; font-weight: 700; color: #1a1a2e; margin: 0 0 8px;
}
.form-subtitle { font-size: 14px; color: #909399; margin: 0; }

/* 登录方式切换 */
.login-tabs {
  display: flex;
  border-bottom: 2px solid #eceff4;
  margin-bottom: 28px;
}
.tab-item {
  flex: 1; text-align: center; padding: 12px 0;
  font-size: 14px; color: #909399; cursor: pointer;
  position: relative; transition: color 0.3s;
}
.tab-item.active {
  color: #409eff; font-weight: 600;
}
.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: -2px; left: 20%; right: 20%; height: 2px;
  background: #409eff; border-radius: 1px;
}

/* 表单 */
.submit-btn {
  width: 100%; height: 44px; font-size: 16px;
  letter-spacing: 4px; border-radius: 8px;
}

.sms-row {
  display: flex; gap: 10px; width: 100%;
}
.sms-input { flex: 1; }
.sms-btn { width: 110px; flex-shrink: 0; }

.form-footer {
  text-align: center; margin-top: 24px;
  font-size: 12px; color: #c0c4cc;
}

/* 响应式 */
@media (max-width: 860px) {
  .login-left { display: none; }
  .login-right { min-width: auto; padding: 20px; }
  .login-form-wrapper { width: 100%; max-width: 400px; }
}
</style>
