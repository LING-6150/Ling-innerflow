<template>
  <div class="chat-container" :class="`mood-${currentMood}`">
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>

    <!-- 顶部导航 -->
    <div class="top-bar glass-card">
      <div class="top-left">
        <span class="logo-text">🌸 InnerFlow</span>
      </div>
      <div class="top-center">
        <span class="mood-indicator" @click="showPersonaMenu = !showPersonaMenu" style="cursor:pointer">
          {{ moodText }} {{ personaEmoji }}
        </span>
        <div v-if="showPersonaMenu" class="persona-menu glass-card">
          <button @click="switchPersona('WARM')">🌸 Warm</button>
          <button @click="switchPersona('QUIET')">🌙 Quiet</button>
          <button @click="switchPersona('RATIONAL')">🧠 Rational</button>
        </div>
      </div>
      <div class="top-right">
        <button class="nav-btn" @click="goTo('/tap')">🎯</button>
        <button class="nav-btn" @click="goTo('/wall')">🌿</button>
        <button class="nav-btn" @click="goTo('/pet')">✨</button>
        <button class="nav-btn" @click="goTo('/profile')">👤</button>
      </div>
    </div>

    <!-- 对话区域 -->
    <div v-if="messages.length === 0" class="welcome-msg">
      <div class="welcome-icon">🌸</div>
      <p>Hello, {{ authStore.username }}</p>
      <p class="welcome-sub">What's on your mind today?</p>
    </div>

      <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role]"
      >
        <div class="bubble" :class="msg.role">
          <img v-if="msg.isImage"
               :src="msg.content"
               style="max-width:200px; border-radius:12px; display:block"
               alt="Shared image" />
          <span v-else>{{ msg.content }}</span>
        </div>

        <div v-if="msg.companionText" class="emotion-tag">
          {{ msg.companionText }}
        </div>
        <div v-else-if="msg.emotionLevel" class="emotion-tag">
          {{ emotionLevelText(msg.emotionLevel) }}
        </div>
      </div>

      <!-- 情绪画像 -->
      <div v-if="latestImage" class="emotion-image-card glass-card">
        <p class="image-label">🎨 Emotional Canvas</p>
        <img
            :src="`data:image/png;base64,${latestImage}`"
            class="emotion-image"
            alt="Emotion Canvas"
        />
      </div>

      <!-- 画像加载中 -->
      <div v-if="imageLoading" class="image-loading">
        🎨 Painting your inner flow...
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

    <!-- 录音提示 -->
    <div v-if="isRecording" class="recording-hint">
      🎙️ Release to Send
    </div>

    <!-- 转录中提示 -->
    <div v-if="isTranscribing" class="recording-hint" style="background: rgba(102,126,234,0.9)">
      ✨ Transcribing...
    </div>

    <!-- 底部输入区 -->
    <div class="input-area glass-card">
      <!-- 麦克风按钮 -->
      <button
          class="mic-btn"
          :class="{ recording: isRecording }"
          @mousedown="startRecording"
          @mouseup="stopRecording"
          @touchstart.prevent="startRecording"
          @touchend.prevent="stopRecording"
          :disabled="isTyping || isTranscribing"
          title="Hold to Speak"
      >
        {{ isRecording ? '🔴' : '🎙️' }}
      </button>

      <!-- 图片上传按钮 -->
      <label class="img-btn" :class="{ disabled: isTyping }">
        📷
        <input
            type="file"
            accept="image/*"
            style="display:none"
            @change="handleImageUpload"
            ref="imageInputRef"
        />
      </label>

      <textarea
          v-model="inputText"
          placeholder="Share your feelings..."
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
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import request from '@/api/request'

const router = useRouter()
const authStore = useAuthStore()
const voiceConfidence = ref(0.0)
const imageConfidence = ref(0.0)

interface Message {
  role: 'user' | 'assistant'
  content: string
  emotionLevel?: number
  companionText?: string
  isImage?: boolean  // 加这行
}

const messages = ref<Message[]>([])
const inputText = ref('')
const isTyping = ref(false)
const messagesRef = ref<HTMLElement>()
const textareaRef = ref<HTMLTextAreaElement>()
const currentMood = ref('calm')
const showPersonaMenu = ref(false)
const currentPersona = ref('WARM')
const latestImage = ref<string | null>(null)
const imageLoading = ref(false)

//加载图片
const imageInputRef = ref<HTMLInputElement>()
const imageEmotionLevel = ref(-1)

async function handleImageUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return

  isTranscribing.value = true
  try {
    const formData = new FormData()
    formData.append('image', file)

    const token = authStore.token
    const res = await fetch('http://localhost:8080/api/emotion/analyze-image', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` },
      body: formData
    })
    const data = await res.json()
    imageEmotionLevel.value = data.emotionLevel || -1

    // 显示图片预览消息
    const reader = new FileReader()
    reader.onload = (e) => {
      const base64 = e.target?.result as string
      messages.value.push({
        role: 'user',
        content: base64,  // 存base64
        isImage: true,    // 标记是图片
        emotionLevel: imageEmotionLevel.value
      })
      scrollToBottom()
    }
    reader.readAsDataURL(file)

    // 自动发一条消息触发AI回复
    inputText.value = 'I Shared an image'
    await sendMessage()

  } catch (e) {
    console.error('Image upload failed', e)
  } finally {
    isTranscribing.value = false
    if (imageInputRef.value) imageInputRef.value.value = ''
  }
}
// 录音相关
const isRecording = ref(false)
const isTranscribing = ref(false)
const voiceEmotionLevel = ref(-1)  // 加在这里

let mediaRecorder: MediaRecorder | null = null
let audioChunks: Blob[] = []

let ws: WebSocket | null = null
let pollTimer: ReturnType<typeof setTimeout> | null = null

const moodTextMap: Record<string, string> = {
  calm: '🌙 Feeling Calm',
  anxious: '💜 Feeling Anxious',
  tired: '🌫️ Feeling Tired'
}
const moodText = computed(() => moodTextMap[currentMood.value])

const personaEmoji = computed(() =>
    ({'WARM': '🌸', 'QUIET': '🌙', 'RATIONAL': '🧠'} as Record<string, string>)[currentPersona.value] || '🌸'
)

function emotionLevelText(level: number): string {
  const map: Record<number, string> = {
    1: '🌿 I hear you.',
    2: "🤍 I'm here with you.",
    3: "💜 I can see how hard this is.",
    4: "🫂 I'm here. You're not alone.",
    5: "🆘 I'm very concerned about you."
  }
  return map[level] || ''
}

function goTo(path: string) { router.push(path) }

// ===== 加载历史对话 =====
async function loadHistory() {
  try {
    const res = await request.get('/api/chat/history') as any[]
    if (res && res.length > 0) {
      messages.value = res.map((msg: any) => ({
        role: msg.role as 'user' | 'assistant',
        content: msg.content,
        emotionLevel: msg.emotionLevel ?? undefined,
        companionText: undefined
      }))
      await scrollToBottom()
    }
  } catch (e) {
    console.error('Failed to load history', e)
  }
}

// ===== 加载人格偏好 =====
async function loadPersona() {
  try {
    const res = await request.get('/api/emotion/persona') as any
    currentPersona.value = res.persona
  } catch (e) {}
}

// ===== 切换人格 =====
async function switchPersona(p: string) {
  try {
    await request.post('/api/emotion/persona', { persona: p })
    currentPersona.value = p
  } catch (e) {
    console.error('Failed to switch persona', e)
  } finally {
    showPersonaMenu.value = false
  }
}

// ===== 拉取最新画像 =====
async function loadLatestImage() {
  try {
    const res = await request.get('/api/emotion-image/latest') as any
    if (res.imageBase64) {
      latestImage.value = res.imageBase64
      imageLoading.value = false
      return true
    }
  } catch (e) {}
  return false
}

// ===== 轮询画像 =====
function pollForImage(attempts = 0) {
  if (attempts >= 6) {
    imageLoading.value = false
    return
  }
  pollTimer = setTimeout(async () => {
    const success = await loadLatestImage()
    if (!success) {
      pollForImage(attempts + 1)
    } else {
      await scrollToBottom()
    }
  }, 10000)
}

// ===== 录音开始 =====
async function startRecording() {
  if (isTyping.value || isTranscribing.value) return
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    audioChunks = []

    // Chrome优先用webm，Safari用mp4
    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
        ? 'audio/webm;codecs=opus'
        : MediaRecorder.isTypeSupported('audio/webm')
            ? 'audio/webm'
            : MediaRecorder.isTypeSupported('audio/mp4')
                ? 'audio/mp4'
                : ''

    mediaRecorder = mimeType
        ? new MediaRecorder(stream, { mimeType })
        : new MediaRecorder(stream)

    mediaRecorder.ondataavailable = (e) => {
      console.log('Audio chunk received:', e.data.size)  // 加这行
      if (e.data.size > 0) audioChunks.push(e.data)
    }

    mediaRecorder.start(100)  // 每100ms收集一次数据
    isRecording.value = true
    console.log('Recording started, mimeType:', mimeType)  // 加这行
  } catch (e) {
    console.error('Audio capture started', e)
  }
}

// ===== 录音结束，上传Whisper =====
async function stopRecording() {
  if (!mediaRecorder || !isRecording.value) return
  isRecording.value = false

  mediaRecorder.stop()
  mediaRecorder.onstop = async () => {
    mediaRecorder!.stream.getTracks().forEach(t => t.stop())

    const mimeType = mediaRecorder?.mimeType || 'audio/webm'
    const audioBlob = new Blob(audioChunks, { type: mimeType })

    isTranscribing.value = true
    try {
      // 用AudioContext把webm转成wav
      const arrayBuffer = await audioBlob.arrayBuffer()
      const audioContext = new AudioContext()
      const audioBuffer = await audioContext.decodeAudioData(arrayBuffer)

      // 转wav
      const wavBlob = audioBufferToWav(audioBuffer)

      const formData = new FormData()
      formData.append('audio', wavBlob, 'audio.wav')

      const token = authStore.token
      const res = await fetch('http://localhost:8080/api/whisper/transcribe', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
        body: formData
      })
      const data = await res.json()

      if (data.text && data.text.trim()) {
        inputText.value = data.text.trim()
        isTranscribing.value = false

        // 把语音情绪等级存起来，发消息时带上
        voiceEmotionLevel.value = data.voiceEmotionLevel || -1
        voiceConfidence.value = data.voiceConfidence || 0.0  // 加这行

        await sendMessage()
      } else {
        isTranscribing.value = false
      }
    } catch (e) {
      console.error('Voice recognition failed', e)
      isTranscribing.value = false
    } finally {
      isTranscribing.value = false
    }
  }
}

// wav转换函数（放在script里，函数外面）
function audioBufferToWav(buffer: AudioBuffer): Blob {
  const numChannels = buffer.numberOfChannels
  const sampleRate = buffer.sampleRate
  const format = 1 // PCM
  const bitDepth = 16

  const dataLength = buffer.length * numChannels * (bitDepth / 8)
  const bufferLength = 44 + dataLength
  const arrayBuffer = new ArrayBuffer(bufferLength)
  const view = new DataView(arrayBuffer)

  // WAV header
  const writeString = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i))
    }
  }

  writeString(0, 'RIFF')
  view.setUint32(4, 36 + dataLength, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, format, true)
  view.setUint16(22, numChannels, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * numChannels * (bitDepth / 8), true)
  view.setUint16(32, numChannels * (bitDepth / 8), true)
  view.setUint16(34, bitDepth, true)
  writeString(36, 'data')
  view.setUint32(40, dataLength, true)

  // PCM数据
  let offset = 44
  for (let i = 0; i < buffer.length; i++) {
    for (let ch = 0; ch < numChannels; ch++) {
      const sample = Math.max(-1, Math.min(1, buffer.getChannelData(ch)[i] ?? 0))
      view.setInt16(offset, sample < 0 ? sample * 32768 : sample * 32767, true)
      offset += 2
    }
  }

  return new Blob([arrayBuffer], { type: 'audio/wav' })
}

// ===== WebSocket连接 =====
function connectWS() {
  const token = authStore.token
  ws = new WebSocket(`ws://localhost:8080/ws/emotion?token=${token}`)

  ws.onopen = () => console.log('WebSocket connected')

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data)

    if (data.type === 'emotion') {
      const level = data.level
      currentMood.value = level >= 4 ? 'anxious' : level >= 2 ? 'tired' : 'calm'
      const lastUserMsg = [...messages.value].reverse().find(m => m.role === 'user')
      if (lastUserMsg) {
        lastUserMsg.emotionLevel = level
        lastUserMsg.companionText = data.companion
      }
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
    imageLoading.value = true
    pollForImage()
  }

  ws.onerror = (err) => console.error('WebSocket error', err)
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isTyping.value) return
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  isTyping.value = true
  if (textareaRef.value) textareaRef.value.style.height = 'auto'
  scrollToBottom()
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({
      text: text,
      voiceEmotionLevel: voiceEmotionLevel.value,
      voiceConfidence: voiceConfidence.value,   // 加这行
      imageEmotionLevel: imageEmotionLevel.value,  // 加这行
      imageConfidence: imageConfidence.value    // 加这行
    }))
    voiceEmotionLevel.value = -1 // 重置
    imageEmotionLevel.value = -1  // 加这行
  }
}

function autoResize(e: Event) {
  const target = e.target as HTMLTextAreaElement
  target.style.height = 'auto'
  target.style.height = Math.min(target.scrollHeight, 120) + 'px'
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
}

onMounted(async () => {
  await loadHistory()
  await loadPersona()
  await loadLatestImage()
  connectWS()
})

onUnmounted(() => {
  ws?.close()
  if (pollTimer) clearTimeout(pollTimer)
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
  pointer-events: none;
}

.bg-orb-1 {
  width: 500px; height: 500px;
  background: radial-gradient(circle, #b8e0ff, #d4b8ff);
  top: -200px; right: -150px; opacity: 0.5;
}

.bg-orb-2 {
  width: 400px; height: 400px;
  background: radial-gradient(circle, #b8f0e0, #c8d8ff);
  bottom: 0; left: -150px; opacity: 0.4;
}

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

.top-center { position: relative; }

.mood-indicator {
  font-size: 13px;
  color: var(--text-secondary);
  user-select: none;
}

.persona-menu {
  position: absolute;
  top: 32px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  border-radius: 20px;
  z-index: 20;
  white-space: nowrap;
}

.persona-menu button {
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 12px;
  transition: all 0.2s;
}

.persona-menu button:hover {
  background: var(--glass-strong);
  color: var(--text-primary);
}

.top-right { display: flex; gap: 8px; }

.nav-btn {
  width: 36px; height: 36px;
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

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.messages-area::-webkit-scrollbar { width: 4px; }
.messages-area::-webkit-scrollbar-thumb {
  background: var(--glass-strong);
  border-radius: 2px;
}

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

.message {
  display: flex;
  flex-direction: column;
  max-width: 80%;
}

.message.user { align-self: flex-end; align-items: flex-end; }
.message.assistant { align-self: flex-start; align-items: flex-start; }

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

.bubble.typing {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 16px;
}

.dot {
  width: 8px; height: 8px;
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

.emotion-tag {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  padding: 0 4px;
}

.emotion-image-card {
  padding: 16px;
  text-align: center;
  margin: 8px 0;
}

.image-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 10px;
}

.emotion-image {
  width: 100%;
  max-width: 300px;
  border-radius: 16px;
  opacity: 0.9;
}

.image-loading {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  padding: 16px;
  font-style: italic;
}

/* 录音提示 */
.recording-hint {
  position: fixed;
  bottom: 100px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(255, 80, 80, 0.9);
  color: white;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  z-index: 20;
}

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

/* 麦크风按钮 */
.mic-btn {
  width: 40px; height: 40px;
  border-radius: 50%;
  background: var(--glass-light);
  border: none;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.mic-btn.recording {
  background: rgba(255, 80, 80, 0.15);
  border: 1px solid rgba(255, 80, 80, 0.3);
  animation: pulse 1s ease-in-out infinite;
}

.mic-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
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

.input-box::placeholder { color: var(--text-muted); }

.send-btn {
  width: 40px; height: 40px;
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

.img-btn {
  width: 40px; height: 40px;
  border-radius: 50%;
  background: var(--glass-light);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.img-btn:hover { background: var(--glass-strong); }
.img-btn.disabled { opacity: 0.4; pointer-events: none; }

.send-btn:hover:not(:disabled) { transform: scale(1.1); }
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
</style>