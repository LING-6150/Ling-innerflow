<template>
  <div class="wall-container">
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>

    <!-- 顶部 -->
    <div class="top-bar glass-card">
      <h2 class="page-title">🌿 Space</h2>
      <button class="write-btn" @click="showInput = !showInput">
        {{ showInput ? 'Cancel' : '+ Write it down' }}
      </button>
    </div>

    <!-- 输入区域 -->
    <div v-if="showInput" class="input-card glass-card-strong">
      <textarea
          v-model="newContent"
          placeholder="What’s on your mind today..."
          class="wall-input"
          rows="3"
      ></textarea>
      <div class="input-footer">
        <div class="visibility-switch">
          <button
              :class="['vis-btn', { active: visibility === 'public' }]"
              @click="visibility = 'public'"
          >🌍 Public</button>
          <button
              :class="['vis-btn', { active: visibility === 'private' }]"
              @click="visibility = 'private'"
          >🔒 Private</button>
        </div>
        <!-- AI开关 -->
        <div class="ai-switch">
          <span class="ai-switch-label">🤖 AI Insights</span>
          <button
              :class="['ai-toggle', { active: needAI }]"
              @click="needAI = !needAI"
          >{{ needAI ? 'On' : 'Off' }}</button>
        </div>
        <button
            class="submit-btn"
            :disabled="!newContent.trim() || submitting"
            @click="submitCheckIn"
        >
          {{ submitting ? 'Sending...' : 'Sent' }}
        </button>
      </div>
    </div>

    <!-- 成功提示 -->
    <div v-if="showSuccess" class="success-toast">
      🌸 Saved {{ needAI ? '，AI is analyzing...' : '' }}
    </div>

    <!-- Tab切换 -->
    <div class="tab-bar">
      <button
          :class="['tab-btn', { active: activeTab === 'wall' }]"
          @click="activeTab = 'wall'"
      >Open Space</button>
      <button
          :class="['tab-btn', { active: activeTab === 'mine' }]"
          @click="loadMyHistory"
      >My History</button>
    </div>

    <!-- 内容列表 -->
    <div class="cards-area" @scroll="onWallScroll">
      <div v-if="loading" class="loading-text">Loading...</div>

      <div
          v-for="item in displayList"
          :key="item.id"
          class="wall-card glass-card"
      >
        <!-- 情绪等级标签 -->
        <div class="card-header">
          <span class="emotion-badge">
            {{ emotionEmoji(item.emotionLevel) }}
          </span>
          <span class="card-time">{{ formatTime(item.createdAt) }}</span>
        </div>

        <!-- 内容 -->
        <p class="card-content">{{ item.content }}</p>

        <!-- AI回复 -->
        <div v-if="item.aiResponse && item.emotionLevel > 0" class="ai-reply">
          <span class="ai-label">🌸 InnerFlow</span>
          <p>{{ item.aiResponse }}</p>
        </div>

        <!-- 等待AI -->
        <div v-else-if="item.emotionLevel === 0" class="ai-pending">
          AI is reflecting...
        </div>

        <!-- 抱抱按钮 -->
        <div class="card-footer">
          <button
              class="hug-btn"
              :class="{ hugged: item.hugged }"
              @click="toggleHug(item)"
          >
            {{ item.hugged ? '💜 Sent a hug' : '🤍 Give a hug' }}
            <span v-if="item.hugCount && item.hugCount > 0">
              {{ item.hugCount }}
            </span>
          </button>
        </div>
      </div>

      <!-- 新增这两行 -->
      <div v-if="loadingMore" class="loading-text" style="padding: 12px 0">Loading more...</div>
      <div v-if="!hasMore && wallList.length > 0" class="loading-text" style="padding: 12px 0">You've reached the end 🌿</div>

      <div v-if="!loading && displayList.length === 0" class="empty-text">
        A blank canvas... Share your first words today.
      </div>
    </div>

    <!-- 底部导航 -->
    <div class="bottom-nav glass-card">
      <button @click="goTo('/')">💬</button>
      <button @click="goTo('/tap')">🎯</button>
      <button @click="goTo('/wall')" class="active">🌿</button>
      <button @click="goTo('/pet')">✨</button>
      <button @click="goTo('/profile')">👤</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'

const router = useRouter()

interface CheckIn {
  id: number
  userId: string
  content: string
  emotionLevel: number
  aiResponse: string
  visibility: string
  createdAt: string
  hugCount?: number
  hugged?: boolean
}

const activeTab = ref<'wall' | 'mine'>('wall')
const wallList = ref<CheckIn[]>([])
const myList = ref<CheckIn[]>([])
const loading = ref(false)
const showInput = ref(false)
const newContent = ref('')
const visibility = ref<'public' | 'private'>('public')
const needAI = ref(true)
const submitting = ref(false)
const showSuccess = ref(false)
const page = ref(0)
const hasMore = ref(true)
const loadingMore = ref(false)

const displayList = computed(() =>
    activeTab.value === 'wall' ? wallList.value : myList.value
)

function goTo(path: string) {
  router.push(path)
}

function emotionEmoji(level: number): string {
  const map: Record<number, string> = {
    0: '⏳', 1: '🌱', 2: '💙', 3: '💜', 4: '🖤', 5: '🆘', [-1]: '✍️'
  }
  return map[level] || '🌱'
}

function formatTime(time: string): string {
  const d = new Date(time)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const hours = Math.floor(diff / 3600000)
  if (hours < 1) return 'Just now'
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}days ago`
}

async function loadReactions(list: CheckIn[]) {
  for (const item of list) {
    try {
      const res = await request.get(`/api/checkin/${item.id}/react`) as any
      item.hugged = res.hugged
      item.hugCount = res.count
    } catch (e) {
      item.hugged = false
      item.hugCount = 0
    }
  }
}

async function loadWall(reset = true) {
  if (reset) {
    wallList.value = []
  }
  loading.value = true

  try {
    const res = await request.get('/api/checkin/wall') as any
    const newItems = res as CheckIn[]
    await loadReactions(newItems)
    wallList.value = reset ? newItems : [...wallList.value, ...newItems]
    hasMore.value = false  // 暂时关闭分页
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadMyHistory() {
  activeTab.value = 'mine'
  loading.value = true
  try {
    const res = await request.get('/api/checkin/history')
    myList.value = res as unknown as CheckIn[]
    await loadReactions(myList.value)
  } finally {
    loading.value = false
  }
}

async function toggleHug(item: CheckIn) {
  try {
    const res = await request.post(`/api/checkin/${item.id}/react`) as any
    item.hugged = res.hugged
    item.hugCount = res.count
  } catch (e) {
    console.error(e)
  }
}

async function submitCheckIn() {
  if (!newContent.value.trim()) return
  submitting.value = true
  try {
    await request.post('/api/checkin', {
      content: newContent.value,
      visibility: visibility.value,
      needAI: needAI.value
    })
    newContent.value = ''
    showInput.value = false
    showSuccess.value = true
    setTimeout(() => { showSuccess.value = false }, 3000)
    await loadWall()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadWall()
})

function onWallScroll(e: Event) {
  if (activeTab.value !== 'wall') return
  const el = e.target as HTMLElement
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 100
  if (nearBottom && hasMore.value && !loadingMore.value) {
    loadWall(false)
  }
}

</script>

<style scoped>
.wall-container {
  min-height: 100vh;
  background: var(--gradient-bg);
  position: relative;
  overflow-x: hidden;
  padding-bottom: 100px;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.bg-orb-1 {
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, #c8e6ff, #e0c8ff);
  top: -80px;
  right: -80px;
  opacity: 0.5;
}

.bg-orb-2 {
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, #b8f0e0, #d4e8ff);
  bottom: 100px;
  left: -60px;
  opacity: 0.4;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  margin: 12px;
  position: sticky;
  top: 12px;
  z-index: 10;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.write-btn {
  padding: 8px 18px;
  background: var(--gradient-primary);
  border: none;
  border-radius: 20px;
  color: white;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 4px 15px rgba(240,147,251,0.3);
}

.input-card {
  margin: 0 12px 12px;
  padding: 16px;
}

.wall-input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 15px;
  resize: none;
  line-height: 1.6;
  font-family: inherit;
}

.wall-input::placeholder {
  color: var(--text-muted);
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(255,255,255,0.3);
  gap: 8px;
  flex-wrap: wrap;
}

.visibility-switch {
  display: flex;
  gap: 8px;
}

.vis-btn {
  padding: 6px 14px;
  border: 1px solid rgba(45,27,78,0.2);
  border-radius: 16px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-soft);
}

.vis-btn.active {
  background: var(--gradient-primary);
  color: white;
  border-color: transparent;
}

/* AI开关 */
.ai-switch {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-switch-label {
  font-size: 12px;
  color: var(--text-muted);
}

.ai-toggle {
  padding: 4px 12px;
  border: 1px solid rgba(240, 147, 251, 0.3);
  border-radius: 12px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-soft);
}

.ai-toggle.active {
  background: rgba(240, 147, 251, 0.15);
  border-color: rgba(240, 147, 251, 0.4);
  color: #c471ed;
}

.submit-btn {
  padding: 8px 20px;
  background: var(--gradient-primary);
  border: none;
  border-radius: 20px;
  color: white;
  font-size: 14px;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.success-toast {
  margin: 0 12px 8px;
  padding: 10px 16px;
  background: rgba(100, 200, 150, 0.15);
  border: 1px solid rgba(100, 200, 150, 0.3);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: #2d7a5a;
  text-align: center;
}

.tab-bar {
  display: flex;
  margin: 0 12px 12px;
  background: var(--glass-light);
  backdrop-filter: blur(10px);
  border-radius: var(--radius-sm);
  padding: 4px;
  border: 1px solid rgba(255,255,255,0.6);
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
}

.cards-area {
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;                    /* 加这行 */
  max-height: calc(100vh - 260px);     /* 加这行 */
}

.wall-card {
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.emotion-badge {
  font-size: 20px;
}

.card-time {
  font-size: 12px;
  color: var(--text-muted);
}

.card-content {
  font-size: 15px;
  color: var(--text-primary);
  line-height: 1.6;
  margin-bottom: 12px;
}

.ai-reply {
  background: rgba(240, 147, 251, 0.08);
  border-left: 3px solid #f093fb;
  padding: 10px 12px;
  border-radius: 0 8px 8px 0;
  margin-bottom: 8px;
}

.ai-label {
  font-size: 12px;
  color: #c471ed;
  font-weight: 600;
  display: block;
  margin-bottom: 4px;
}

.ai-reply p {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.ai-pending {
  font-size: 12px;
  color: var(--text-muted);
  font-style: italic;
  margin-bottom: 8px;
}

.card-footer {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.hug-btn {
  padding: 6px 16px;
  border: 1px solid rgba(240, 147, 251, 0.3);
  border-radius: 20px;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-soft);
  display: flex;
  align-items: center;
  gap: 4px;
}

.hug-btn:hover {
  background: rgba(240, 147, 251, 0.1);
  border-color: rgba(240, 147, 251, 0.5);
  color: var(--text-secondary);
}

.hug-btn.hugged {
  background: rgba(240, 147, 251, 0.15);
  border-color: rgba(240, 147, 251, 0.4);
  color: #c471ed;
}

.loading-text,
.empty-text {
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
  padding: 40px 0;
}

.bottom-nav {
  position: fixed;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 30px;
  z-index: 10;
}

.bottom-nav button {
  width: 48px;
  height: 48px;
  border: none;
  background: transparent;
  border-radius: 50%;
  font-size: 22px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-soft);
}

.bottom-nav button:hover,
.bottom-nav button.active {
  background: var(--glass-strong);
  transform: scale(1.1);
}
</style>