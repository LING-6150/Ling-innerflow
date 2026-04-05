<template>
  <div class="login-container">
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>
    <div class="bg-orb bg-orb-3"></div>

    <div class="login-card glass-card-strong">
      <div class="logo-area">
        <div class="logo-icon">🌸</div>
        <h1 class="logo-text">InnerFlow</h1>
        <p class="logo-slogan">在这里，你不需要变好，只需要存在</p>
      </div>

      <div class="tab-switch">
        <button
            :class="['tab-btn', { active: mode === 'login' }]"
            @click="mode = 'login'"
        >登录</button>
        <button
            :class="['tab-btn', { active: mode === 'register' }]"
            @click="mode = 'register'"
        >注册</button>
      </div>

      <div class="form-area">
        <div v-if="mode === 'register'" class="input-group">
          <input
              v-model="form.username"
              type="text"
              placeholder="你的昵称"
              class="input-field"
          />
        </div>

        <div class="input-group">
          <input
              v-model="form.email"
              type="email"
              placeholder="邮箱"
              class="input-field"
          />
        </div>

        <div class="input-group">
          <input
              v-model="form.password"
              type="password"
              placeholder="密码"
              class="input-field"
          />
        </div>

        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

        <button
            class="submit-btn"
            :disabled="loading"
            @click="handleSubmit"
        >
          <span v-if="loading">进入中...</span>
          <span v-else>{{ mode === 'login' ? '进入 InnerFlow' : '创建我的空间' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { login, register } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: '',
  email: '',
  password: ''
})

async function handleSubmit() {
  errorMsg.value = ''
  loading.value = true

  try {
    let res
    if (mode.value === 'login') {
      res = await login({ email: form.email, password: form.password })
    } else {
      res = await register({
        username: form.username,
        email: form.email,
        password: form.password
      })
    }

    authStore.setAuth({
      token: res.token,
      userId: res.userId,
      username: res.username
    })

    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '操作失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient-bg);
  position: relative;
  overflow: hidden;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  animation: float var(--duration-breath) ease-in-out infinite alternate;
  pointer-events: none;
}

.bg-orb-1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, #b8e0ff, #d4b8ff);
  top: -100px;
  left: -100px;
  opacity: 0.5;
  animation-duration: 4s;
}

.bg-orb-2 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, #b8f0e0, #c8d8ff);
  bottom: -50px;
  right: -50px;
  opacity: 0.4;
  animation-duration: 5s;
  animation-delay: 1s;
}

.bg-orb-3 {
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, #d4b8ff, #b8e0ff);
  top: 50%;
  left: 50%;
  opacity: 0.3;
  animation-duration: 6s;
  animation-delay: 2s;
}

@keyframes float {
  from { transform: translate(0, 0) scale(1); }
  to { transform: translate(20px, -20px) scale(1.1); }
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 40px 32px;
  position: relative;
  z-index: 10;
  margin: 20px;
}

.logo-area {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  font-size: 48px;
  margin-bottom: 8px;
  display: block;
  animation: logoFloat 3s ease-in-out infinite alternate;
}

@keyframes logoFloat {
  from { transform: translateY(0); }
  to { transform: translateY(-8px); }
}

.logo-text {
  font-size: 28px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 8px;
}

.logo-slogan {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.6;
}

.tab-switch {
  display: flex;
  background: rgba(255, 255, 255, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: var(--radius-sm);
  padding: 4px;
  margin-bottom: 24px;
}

.tab-btn {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-soft);
}

.tab-btn.active {
  background: var(--gradient-primary);
  color: white;
  box-shadow: 0 2px 12px rgba(240, 147, 251, 0.3);
}

.input-group {
  margin-bottom: 16px;
}

.input-field {
  width: 100%;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 15px;
  outline: none;
  transition: all var(--duration-fast) var(--ease-soft);
}

.input-field::placeholder {
  color: var(--text-muted);
}

.input-field:focus {
  border-color: rgba(240, 147, 251, 0.5);
  box-shadow: 0 0 0 3px rgba(240, 147, 251, 0.1);
  background: rgba(255, 255, 255, 0.8);
}

.error-msg {
  color: #e05a6a;
  font-size: 13px;
  margin-bottom: 12px;
  text-align: center;
}

.submit-btn {
  width: 100%;
  padding: 14px;
  background: var(--gradient-primary);
  border: none;
  border-radius: var(--radius-md);
  color: white;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-soft);
  box-shadow: 0 4px 20px rgba(240, 147, 251, 0.3);
  margin-top: 8px;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(240, 147, 251, 0.4);
}

.submit-btn:active {
  transform: translateY(0);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}
</style>