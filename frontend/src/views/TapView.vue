<template>
  <div class="tap-container" :class="`mood-${mode}`">
    <!-- 背景光晕 -->
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>

    <!-- 顶部 -->
    <div class="top-bar">
      <button class="back-btn" @click="goBack">←</button>
      <div class="mode-switch">
        <button
            :class="['mode-btn', { active: mode === 'relief' }]"
            @click="setMode('relief')"
        >解压</button>
        <button
            :class="['mode-btn', { active: mode === 'meditation' }]"
            @click="setMode('meditation')"
        >冥想</button>
      </div>
      <div style="width:40px"></div>
    </div>

    <!-- 提示语 -->
    <div class="hint-text">{{ hintText }}</div>

    <!-- 计数 -->
    <div class="count-display">
      <span class="count-number">{{ count }}</span>
      <span class="count-label">次</span>
    </div>

    <!-- BPM -->
    <div class="bpm-display">BPM {{ bpm }}</div>

    <!-- Emoji -->
    <div class="emoji-display">{{ currentEmoji }}</div>

    <!-- 主按钮 -->
    <div class="tap-btn-wrapper">
      <div class="tap-ripple" :class="{ animate: isRippling }"></div>
      <div class="tap-ripple-2" :class="{ animate: isRippling }"></div>
      <button
          class="tap-btn"
          @click="handleTap"
          @touchstart.prevent="handleTap"
      >
        <span class="tap-text">TAP</span>
      </button>
    </div>

    <!-- 鼓励话语 -->
    <div class="message-display" :class="{ show: showMessage }">
      {{ currentMessage }}
    </div>

    <!-- 音乐推荐 -->
    <div v-if="musicInfo" class="music-card glass-card">
      <span>🎵 {{ musicInfo.title }}</span>
      <audio :src="musicInfo.url" controls></audio>
    </div>

    <!-- 继续上次提示 -->
    <div v-if="showContinue" class="continue-prompt glass-card">
      <span>上次tap了 <b>{{ lastCount }}</b> 下</span>
      <div class="continue-btns">
        <button class="continue-yes" @click="continueSession">继续上次</button>
        <button class="continue-no" @click="showContinue = false">重新开始</button>
      </div>
    </div>

    <!-- 底部导航 -->
    <div class="bottom-nav glass-card">
      <button @click="goTo('/')">💬</button>
      <button @click="goTo('/tap')" class="active">🎯</button>
      <button @click="goTo('/wall')">🌿</button>
      <button @click="goTo('/pet')">✨</button>
      <button @click="goTo('/profile')">👤</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
// 在import区域加
import request from '@/api/request'

const router = useRouter()
const authStore = useAuthStore()

const mode = ref<'relief' | 'meditation'>('relief')
const count = ref(0)
const bpm = ref(0)
const currentEmoji = ref('🌸')
const currentMessage = ref('')
const showMessage = ref(false)
const isRippling = ref(false)
const musicInfo = ref<{ title: string; url: string } | null>(null)
const showContinue = ref(false)
const lastCount = ref(0)

let ws: WebSocket | null = null

const hintText = computed(() => {
  return mode.value === 'relief'
      ? '让情绪出来吧，这里很安全'
      : '跟着节奏，慢慢呼吸'
})

function goBack() {
  router.push('/')
}

function goTo(path: string) {
  router.push(path)
}

function setMode(newMode: 'relief' | 'meditation') {
  mode.value = newMode
  ws?.send(JSON.stringify({ type: 'mode_change', mode: newMode }))
}

// 在count.value等变量定义处加一个本地tap计数
const localTapCount = ref(0)

function handleTap() {
  if (navigator.vibrate) navigator.vibrate(15)

  isRippling.value = false
  setTimeout(() => { isRippling.value = true }, 10)
  ws?.send(JSON.stringify({ type: 'tap', mode: mode.value }))

  // 本地计数，每10次通知宠物
  localTapCount.value++
  if (localTapCount.value % 10 === 0) {
    request.post('/api/pet/tap', { count: 10 }).catch(() => {})
  }
}

function continueSession() {
  count.value = lastCount.value
  showContinue.value = false
  ws?.send(JSON.stringify({ type: 'continue', count: lastCount.value }))
}

function connectWS() {
  ws = new WebSocket(
      `ws://localhost:8080/ws/tap?userId=${authStore.userId}`
  )

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data)

    if (data.type === 'connected') {
      if (data.hasLastSession && data.lastCount > 0) {
        lastCount.value = data.lastCount
        showContinue.value = true
      }
    } else if (data.type === 'feedback') {
      count.value = data.count
      bpm.value = data.bpm
      currentEmoji.value = data.emoji
      currentMessage.value = data.message
      showMessage.value = true
      setTimeout(() => { showMessage.value = false }, 2000)

      if (data.music) {
        musicInfo.value = JSON.parse(data.music)
      }
    } else if (data.type === 'continued') {
      count.value = data.count
    } else if (data.type === 'reset') {
      count.value = 0
      bpm.value = 0
    }
  }
}

// 自动保存
let saveInterval: ReturnType<typeof setInterval>

onMounted(() => {
  connectWS()
  saveInterval = setInterval(() => {
    ws?.send(JSON.stringify({ type: 'save' }))
  }, 5000)
})

onUnmounted(() => {
  ws?.send(JSON.stringify({ type: 'save' }))
  ws?.close()
  clearInterval(saveInterval)
})
</script>

<style scoped>
.tap-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(--gradient-bg);
  position: relative;
  overflow: hidden;
  padding-bottom: 20px;
  padding-right: 60px;  /* 给右侧导航留空间 */
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.bg-orb-1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, #d4b8ff, #b8e0ff);
  top: -100px;
  right: -100px;
  opacity: 0.5;
}

.bg-orb-2 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, #b8f0e0, #c8d8ff);
  bottom: 100px;
  left: -80px;
  opacity: 0.4;
}

/* 顶部 */
.top-bar {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  position: relative;
  z-index: 10;
}

.back-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: var(--glass-light);
  backdrop-filter: blur(10px);
  border-radius: 50%;
  cursor: pointer;
  font-size: 18px;
  color: var(--text-primary);
}

.mode-switch {
  display: flex;
  background: var(--glass-light);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 4px;
  border: 1px solid rgba(255,255,255,0.6);
}

.mode-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 16px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-soft);
}

.mode-btn.active {
  background: var(--gradient-primary);
  color: white;
  box-shadow: 0 2px 12px rgba(240, 147, 251, 0.3);
}

/* 提示语 */
.hint-text {
  font-size: 14px;
  color: var(--text-muted);
  margin-top: 8px;
  margin-bottom: 4px;
}

/* 计数 */
.count-display {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin: 8px 0;
}

.count-number {
  font-size: 56px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.count-label {
  font-size: 18px;
  color: var(--text-secondary);
}

.bpm-display {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.emoji-display {
  font-size: 40px;
  margin-bottom: 16px;
}

/* 主按钮 */
.tap-btn-wrapper {
  position: relative;
  width: 220px;
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}

.tap-ripple,
.tap-ripple-2 {
  position: absolute;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: var(--gradient-primary);
  opacity: 0;
}

.tap-ripple.animate {
  animation: ripple 0.6s ease-out;
}

.tap-ripple-2.animate {
  animation: ripple 0.6s ease-out 0.15s;
}

@keyframes ripple {
  from {
    transform: scale(1);
    opacity: 0.3;
  }
  to {
    transform: scale(1.8);
    opacity: 0;
  }
}

.tap-btn {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: var(--gradient-primary);
  border: none;
  cursor: pointer;
  position: relative;
  z-index: 1;
  box-shadow:
      0 0 40px rgba(240, 147, 251, 0.4),
      0 8px 32px rgba(102, 126, 234, 0.3);
  transition: transform var(--duration-fast) var(--ease-soft);
  animation: breathe var(--duration-breath) ease-in-out infinite alternate;
}

.tap-btn:active {
  transform: scale(0.93);
}

@keyframes breathe {
  from { transform: scale(1); box-shadow: 0 0 40px rgba(240,147,251,0.4); }
  to { transform: scale(1.06); box-shadow: 0 0 60px rgba(240,147,251,0.6); }
}

.tap-text {
  color: white;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 4px;
  pointer-events: none;
}

/* 鼓励话语 */
.message-display {
  font-size: 15px;
  color: var(--text-secondary);
  text-align: center;
  max-width: 280px;
  min-height: 24px;
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.3s var(--ease-soft);
}

.message-display.show {
  opacity: 1;
  transform: translateY(0);
}

.music-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  margin-top: 16px;
  margin-bottom: 100px;  /* 加这行 */
  font-size: 13px;
  color: var(--text-secondary);
  width: 280px;
}

/* 继续上次 */
.continue-prompt {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  padding: 16px 24px;
  text-align: center;
  z-index: 100;
  font-size: 14px;
  color: var(--text-primary);
  min-width: 260px;
}

.continue-btns {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  justify-content: center;
}

.continue-yes {
  padding: 8px 20px;
  background: var(--gradient-primary);
  border: none;
  border-radius: 20px;
  color: white;
  cursor: pointer;
  font-size: 13px;
}

.continue-no {
  padding: 8px 20px;
  background: var(--glass-light);
  border: 1px solid rgba(255,255,255,0.6);
  border-radius: 20px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 13px;
}

/* 删掉底部导航样式，改成右侧 */
.bottom-nav {
  position: fixed;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  border-radius: 30px;
  z-index: 10;
}

.bottom-nav button {
  width: 44px;
  height: 44px;
  border: none;
  background: transparent;
  border-radius: 50%;
  font-size: 20px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-soft);
}

.bottom-nav button:hover,
.bottom-nav button.active {
  background: var(--glass-strong);
  transform: scale(1.1);
}
</style>