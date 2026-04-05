<template>
  <div class="chat-container" :class="`mood-${currentMood}`">
    <!-- 背景光晕 -->
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>

    <!-- 顶部导航 -->
    <div class="top-bar glass-card">
      <div class="top-left">
        <span class="logo-text">🌸 InnerFlow</span>
      </div>
      <div class="top-center">
        <span class="mood-indicator">{{ moodText }}</span>
      </div>
      <div class="top-right">
        <button class="nav-btn" @click="goTo('/tap')">🎯</button>
        <button class="nav-btn" @click="goTo('/wall')">🌿</button>
        <button class="nav-btn" @click="goTo('/profile')">👤</button>
      </div>
    </div>

    <!-- 对话区域 -->
    <div class="messages-area" ref="messagesRef">
      <!-- 欢迎消息 -->
      <div v-if="messages.length === 0" class="welcome-msg">
        <div class="welcome-icon">🌸</div>
        <p>你好，{{ authStore.username }}</p>
        <p class="welcome-sub">今天想聊聊什么？</p>
      </div>

      <!-- 消息列表 -->
      <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role]"
      >
        <div class="bubble" :class="msg.role">
          <span>{{ msg.content }}</span>
        </div>
        <!-- 情绪标签 -->
        <div v-if="msg.emotionLevel" class="emotion-tag">
          {{ emotionLevelText(msg.emotionLevel) }}
        </div>
      </div>

      <!-- AI正在输入 -->
      <div v-if="isTyping" class="message assistant">
        <div class="bubble assistant typing">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
        </div>
      </div>
    </div>

    <!-- 底部输入区 -->
    <div class="input-area glass-card">
      <textarea
          v-model="inputText"
          placeholder="说说你的感受..."
          class="input-box"
          rows="1"
          @keydown.enter.prevent="sendMessage"
          @input="autoResize"
          ref="textareaRef"
      ></textarea>
      <button
          class="send-btn"
          :disabled="!inputText.trim() || isTyping"
          @click="sendMessage"
      >
        <span>→</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

interface Message {
  role: 'user' | 'assistant'
  content: string
  emotionLevel?: number
}

const messages = ref<Message[]>([])
const inputText = ref('')
const isTyping = ref(false)
const messagesRef = ref<HTMLElement>()
const textareaRef = ref<HTMLTextAreaElement>()
const currentMood = ref('calm')

let ws: WebSocket | null = null

const moodTextMap: Record<string, string> = {
  calm: '🌙 平静中',
  anxious: '💜 有些焦虑',
  tired: '🌫️ 有些疲惫'
}

const moodText = computed(() => moodTextMap[currentMood.value])

function emotionLevelText(level: number): string {
  const map: Record<number, string> = {
    1: '🌱 平静',
    2: '💙 轻度焦虑',
    3: '💜 中度困扰',
    4: '🖤 需要支持',
    5: '🆘 危机'
  }
  return map[level] || ''
}

function goTo(path: string) {
  router.push(path)
}

function connectWS() {
  ws = new WebSocket(
      `ws://localhost:8080/ws/emotion?userId=${authStore.userId}`
  )

  ws.onopen = () => {
    console.log('WebSocket connected')
  }

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data)

    if (data.type === 'emotion') {
      const level = data.level
      if (level >= 4) currentMood.value = 'anxious'
      else if (level >= 3) currentMood.value = 'calm'
      else currentMood.value = 'calm'

      const lastUserMsg = [...messages.value]
          .reverse()
          .find(m => m.role === 'user')
      if (lastUserMsg) lastUserMsg.emotionLevel = level
      isTyping.value = true

    } else if (data.type === 'chunk') {
      isTyping.value = false
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.content += data.content
      } else {
        messages.value.push({ role: 'assistant', content: data.content })
      }
      scrollToBottom()

    } else if (data.type === 'done') {
      isTyping.value = false
      scrollToBottom()

    } else if (data.type === 'response') {
      isTyping.value = false
      messages.value.push({ role: 'assistant', content: data.content })
      scrollToBottom()
    }
  }

  ws.onclose = () => {
    console.log('WebSocket disconnected')
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isTyping.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  isTyping.value = true

  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
  }

  scrollToBottom()

  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(text)
  }
}

function autoResize(e: Event) {
  const target = e.target as HTMLTextAreaElement
  target.style.height = 'auto'
  target.style.height = Math.min(target.scrollHeight, 120) + 'px'
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

onMounted(() => {
  connectWS()
})

onUnmounted(() => {
  ws?.close()
})
</script>

<style scoped>
.chat-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--gradient-bg);
  position: relative;
  overflow: hidden;
  transition: background var(--duration-breath) var(--ease-soft);
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;  /* 从0.3改成0.5 */
  pointer-events: none;
}

.bg-orb-1 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, #b8e0ff, #d4b8ff);
  top: -200px;
  right: -150px;
  opacity: 0.5;
}

.bg-orb-2 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, #b8f0e0, #c8d8ff);
  bottom: 0;
  left: -150px;
  opacity: 0.4;
}

/* 顶部导航 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  margin: 12px;
  border-radius: var(--radius-md);
  position: relative;
  z-index: 10;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.mood-indicator {
  font-size: 13px;
  color: var(--text-secondary);
}

.top-right {
  display: flex;
  gap: 8px;
}

.nav-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: var(--glass-light);
  border-radius: 50%;
  cursor: pointer;
  font-size: 16px;
  transition: all var(--duration-fast) var(--ease-soft);
}

.nav-btn:hover {
  background: var(--glass-strong);
  transform: scale(1.1);
}

/* 消息区域 */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.messages-area::-webkit-scrollbar {
  width: 4px;
}

.messages-area::-webkit-scrollbar-thumb {
  background: var(--glass-strong);
  border-radius: 2px;
}

/* 欢迎消息 */
.welcome-msg {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-secondary);
}

.welcome-icon {
  font-size: 48px;
  margin-bottom: 16px;
  animation: float 3s ease-in-out infinite alternate;
}

.welcome-msg p {
  font-size: 20px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.welcome-sub {
  font-size: 14px;
  color: var(--text-muted) !important;
}

@keyframes float {
  from { transform: translateY(0); }
  to { transform: translateY(-8px); }
}

/* 消息气泡 */
.message {
  display: flex;
  flex-direction: column;
  max-width: 80%;
}

.message.user {
  align-self: flex-end;
  align-items: flex-end;
}

.message.assistant {
  align-self: flex-start;
  align-items: flex-start;
}

.bubble {
  padding: 12px 16px;
  border-radius: 20px;
  font-size: 15px;
  line-height: 1.6;
  max-width: 100%;
  word-break: break-word;
}

.bubble.user {
  background: var(--gradient-primary);
  color: white;
  border-bottom-right-radius: 6px;
}

.bubble.assistant {
  background: var(--glass-strong);
  color: var(--text-primary);
  border-bottom-left-radius: 6px;
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

/* 打字动画 */
.bubble.typing {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 16px;
}

.dot {
  width: 8px;
  height: 8px;
  background: var(--text-secondary);
  border-radius: 50%;
  animation: bounce 1.2s ease-in-out infinite;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-8px); }
}

/* 情绪标签 */
.emotion-tag {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  padding: 0 4px;
}

/* 输入区域 */
.input-area {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 16px;
  margin: 12px;
  border-radius: var(--radius-lg);
  position: relative;
  z-index: 10;
}

.input-box {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 15px;
  resize: none;
  line-height: 1.5;
  max-height: 120px;
  font-family: inherit;
}

.input-box::placeholder {
  color: var(--text-muted);
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--gradient-primary);
  border: none;
  color: white;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast) var(--ease-soft);
  flex-shrink: 0;
  box-shadow: 0 4px 15px rgba(240, 147, 251, 0.3);
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.1);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>