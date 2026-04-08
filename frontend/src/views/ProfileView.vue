<template>
  <div class="profile-container">
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>

    <!-- User Info -->
    <div class="user-card glass-card-strong">
      <div class="avatar">{{ usernameInitial }}</div>
      <div class="user-info">
        <h2 class="username">{{ authStore.username }}</h2>
        <p class="user-sub">{{ overview.total || 0 }} emotional check-ins</p>
      </div>
      <button class="logout-btn" @click="logout">Sign Out</button>
    </div>

    <!-- Emotional Insight -->
    <div v-if="insight.summary" class="insight-card" :class="`tone-${insight.tone}`">
      <div class="insight-icon">🌸</div>
      <div class="insight-content">
        <p class="insight-summary">{{ insight.summary }}</p>
        <p class="insight-pattern">{{ insight.pattern }}</p>
        <p class="insight-suggestion">{{ insight.suggestion }}</p>
      </div>
    </div>

    <!-- Overview Cards -->
    <div class="overview-cards">
      <div class="ov-card glass-card">
        <span class="ov-value">{{ overview.avgLevel || '-' }}</span>
        <span class="ov-label">Avg Mood</span>
      </div>
      <div class="ov-card glass-card">
        <span class="ov-value">{{ overview.total || 0 }}</span>
        <span class="ov-label">7-Day Records</span>
      </div>
      <div class="ov-card glass-card">
        <span class="ov-value">{{ emotionEmoji(overview.latestLevel) }}</span>
        <span class="ov-label">Latest Mood</span>
      </div>
    </div>

    <!-- Mood Trend Chart -->
    <div class="chart-card glass-card">
      <h3 class="chart-title">Mood Trend (Last 7 Days)</h3>
      <div v-if="trendData.length === 0" class="chart-empty">
        No data yet. Start a conversation to see your trend.
      </div>
      <div v-else class="chart-area">
        <svg :width="chartWidth" height="120" class="trend-svg">
          <line
              v-for="i in 5" :key="i"
              x1="0" :y1="(i-1) * 25"
              :x2="chartWidth" :y2="(i-1) * 25"
              stroke="rgba(100,80,150,0.1)" stroke-width="1"
          />
          <defs>
            <linearGradient id="lineGrad" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stop-color="#f093fb"/>
              <stop offset="100%" stop-color="#667eea"/>
            </linearGradient>
            <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#f093fb" stop-opacity="0.3"/>
              <stop offset="100%" stop-color="#667eea" stop-opacity="0"/>
            </linearGradient>
          </defs>
          <path :d="areaPath" fill="url(#areaGrad)"/>
          <path :d="linePath" fill="none" stroke="url(#lineGrad)"
                stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <circle v-for="(point, i) in chartPoints" :key="i"
                  :cx="point.x" :cy="point.y" r="4"
                  fill="white" stroke="#f093fb" stroke-width="2"/>
        </svg>
        <div class="chart-labels">
          <span v-for="(item, i) in trendData" :key="i" class="chart-label">
            {{ formatDate(item.date) }}
          </span>
        </div>
        <p v-if="insight.pattern" class="trend-summary">{{ insight.pattern }}</p>
      </div>
    </div>

    <!-- Mood Distribution -->
    <div class="dist-card glass-card">
      <h3 class="chart-title">Mood Distribution (Last 30 Days)</h3>
      <div class="dist-bars">
        <div v-for="(count, level) in distribution" :key="level" class="dist-row">
          <span class="dist-label">{{ emotionLevelLabel(level as string) }}</span>
          <div class="dist-bar-bg">
            <div class="dist-bar-fill" :style="{ width: barWidth(count) + '%' }"></div>
          </div>
          <span class="dist-count">{{ count }}</span>
        </div>
      </div>
    </div>

    <!-- Emotional Canvas Gallery -->
    <div class="gallery-card glass-card">
      <div class="gallery-header">
        <h3 class="chart-title" style="margin-bottom:0">🎨 Emotional Canvas</h3>
        <span class="gallery-sub">Generated after each conversation</span>
      </div>

      <div v-if="recentImages.length === 0" class="chart-empty">
        No canvas yet. Finish a conversation to generate one.
      </div>

      <div v-else class="gallery-grid">
        <div
            v-for="img in recentImages"
            :key="img.id"
            class="gallery-item"
            @click="openImage(img)"
        >
          <img
              :src="`data:image/png;base64,${img.imageBase64}`"
              class="gallery-img"
              :alt="`Emotional Canvas Level ${img.emotionLevel}`"
          />
          <div class="gallery-meta">
            <span class="gallery-emoji">{{ emotionEmoji(img.emotionLevel) }}</span>
            <span class="gallery-time">{{ formatTime(img.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Full Screen Preview -->
    <div v-if="selectedImage" class="image-overlay" @click="selectedImage = null">
      <div class="image-preview-card" @click.stop>
        <img
            :src="`data:image/png;base64,${selectedImage.imageBase64}`"
            class="image-preview-full"
            alt="Emotional Canvas"
        />
        <div class="image-preview-meta">
          <span>{{ emotionEmoji(selectedImage.emotionLevel) }} {{ emotionLevelLabel2(selectedImage.emotionLevel) }}</span>
          <span>{{ formatTime(selectedImage.createdAt) }}</span>
        </div>
        <button class="close-btn" @click="selectedImage = null">✕</button>
      </div>
    </div>

    <!-- Quick Menu -->
    <div class="menu-list glass-card">
      <div class="menu-item" @click="goTo('/')">
        <span>💬</span><span>Start Conversation</span><span class="arrow">→</span>
      </div>
      <div class="menu-item" @click="goTo('/tap')">
        <span>🎯</span><span>Tap Release</span><span class="arrow">→</span>
      </div>
      <div class="menu-item" @click="goTo('/wall')">
        <span>🌿</span><span>Journal Wall</span><span class="arrow">→</span>
      </div>
    </div>

    <!-- Bottom Nav -->
    <div class="bottom-nav glass-card">
      <button @click="goTo('/')">💬</button>
      <button @click="goTo('/tap')">🎯</button>
      <button @click="goTo('/wall')">🌿</button>
      <button @click="goTo('/pet')">✨</button>
      <button @click="goTo('/profile')" class="active">👤</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import request from '@/api/request'

const router = useRouter()
const authStore = useAuthStore()

const overview = ref<any>({})
const trendData = ref<any[]>([])
const distribution = ref<Record<string, number>>({})
const insight = ref<any>({})
const chartWidth = 320

// 画像相关
interface EmotionImage {
  id: number
  imageBase64: string
  emotionLevel: number
  createdAt: string
}
const recentImages = ref<EmotionImage[]>([])
const selectedImage = ref<EmotionImage | null>(null)

const usernameInitial = computed(() =>
    authStore.username?.charAt(0).toUpperCase() || '?'
)

function goTo(path: string) { router.push(path) }

function logout() {
  authStore.logout()
  router.push('/login')
}

function emotionEmoji(level: number): string {
  const map: Record<number, string> = {
    1: '🌱', 2: '💙', 3: '💜', 4: '🖤', 5: '🆘'
  }
  return map[level] || '🌸'
}

function emotionLevelLabel(level: string): string {
  const map: Record<string, string> = {
    'L1': '🌱 Calm', 'L2': '💙 Low',
    'L3': '💜 Troubled', 'L4': '🖤 Heavy', 'L5': '🆘 Crisis'
  }
  return map[level] || level
}

function emotionLevelLabel2(level: number): string {
  const map: Record<number, string> = {
    1: 'Calm', 2: 'Low', 3: 'Troubled', 4: 'Heavy', 5: 'Crisis'
  }
  return map[level] || ''
}

function formatDate(date: string): string {
  return date?.slice(5) || ''
}

function formatTime(time: string): string {
  const d = new Date(time)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const hours = Math.floor(diff / 3600000)
  if (hours < 1) return 'Just now'
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

function openImage(img: EmotionImage) {
  selectedImage.value = img
}

const chartPoints = computed(() => {
  if (!trendData.value.length) return []
  const maxLevel = 5
  const padding = 20
  const w = chartWidth - padding * 2
  const h = 100
  return trendData.value.map((item, i) => ({
    x: padding + (i / Math.max(trendData.value.length - 1, 1)) * w,
    y: h - ((item.avgLevel / maxLevel) * h)
  }))
})

const linePath = computed(() => {
  if (!chartPoints.value.length) return ''
  return chartPoints.value
      .map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`)
      .join(' ')
})

const areaPath = computed(() => {
  if (!chartPoints.value.length) return ''
  const pts = chartPoints.value
  const lastPt = pts[pts.length - 1]!
  const firstPt = pts[0]!
  return `${linePath.value} L ${lastPt.x} 100 L ${firstPt.x} 100 Z`
})

function barWidth(count: number): number {
  const max = Math.max(...Object.values(distribution.value), 1)
  return (count / max) * 100
}

async function loadData() {
  try {
    const [ov, trend, dist, ins] = await Promise.all([
      request.get('/api/emotion-log/overview'),
      request.get('/api/emotion-log/trend?days=7'),
      request.get('/api/emotion-log/distribution?days=30'),
      request.get('/api/emotion-log/insight')
    ])
    overview.value = ov
    trendData.value = (trend as unknown) as any[]
    distribution.value = ((dist as unknown) as any).distribution || {}
    insight.value = ins
  } catch (e) {
    console.error(e)
  }
}

async function loadImages() {
  try {
    const res = await request.get('/api/emotion-image/recent') as any[]
    recentImages.value = res || []
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  loadData()
  loadImages()
})
</script>

<style scoped>
.profile-container {
  min-height: 100vh;
  background: var(--gradient-bg);
  padding: 12px 12px 100px;
  position: relative;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.bg-orb-1 {
  width: 300px; height: 300px;
  background: radial-gradient(circle, #d4b8ff, #b8e0ff);
  top: -80px; right: -60px; opacity: 0.4;
}

.bg-orb-2 {
  width: 250px; height: 250px;
  background: radial-gradient(circle, #b8f0e0, #c8d8ff);
  bottom: 100px; left: -60px; opacity: 0.3;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  margin-bottom: 12px;
}

.avatar {
  width: 56px; height: 56px;
  border-radius: 50%;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
  font-weight: 700;
  flex-shrink: 0;
}

.user-info { flex: 1; }

.username {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.user-sub {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 2px;
}

.logout-btn {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid rgba(45,27,78,0.2);
  border-radius: 16px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.insight-card {
  padding: 20px;
  margin-bottom: 12px;
  border-radius: var(--radius-lg);
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.tone-calm {
  background: linear-gradient(135deg, rgba(184,224,255,0.4), rgba(200,216,255,0.3));
  border: 1px solid rgba(184,224,255,0.6);
}
.tone-gentle {
  background: linear-gradient(135deg, rgba(240,147,251,0.1), rgba(200,216,255,0.2));
  border: 1px solid rgba(240,147,251,0.2);
}
.tone-supportive {
  background: linear-gradient(135deg, rgba(196,113,237,0.1), rgba(102,126,234,0.15));
  border: 1px solid rgba(196,113,237,0.2);
}
.tone-caring {
  background: linear-gradient(135deg, rgba(240,147,251,0.15), rgba(118,75,162,0.1));
  border: 1px solid rgba(240,147,251,0.3);
}

.insight-icon {
  font-size: 32px;
  flex-shrink: 0;
  animation: float 3s ease-in-out infinite alternate;
}

@keyframes float {
  from { transform: translateY(0); }
  to { transform: translateY(-6px); }
}

.insight-content { flex: 1; }

.insight-summary {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
  line-height: 1.5;
}

.insight-pattern {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  line-height: 1.5;
}

.insight-suggestion {
  font-size: 13px;
  color: var(--text-muted);
  font-style: italic;
  padding: 8px 12px;
  background: rgba(255,255,255,0.4);
  border-radius: var(--radius-sm);
  border-left: 3px solid rgba(240,147,251,0.4);
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.ov-card { padding: 16px 12px; text-align: center; }

.ov-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.ov-label {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

.chart-card { padding: 16px; margin-bottom: 12px; }

.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.chart-empty {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  padding: 20px 0;
}

.chart-area { overflow-x: auto; }
.trend-svg { display: block; }

.chart-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
}

.chart-label { font-size: 10px; color: var(--text-muted); }

.trend-summary {
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  margin-top: 8px;
  font-style: italic;
}

.dist-card { padding: 16px; margin-bottom: 12px; }

.dist-bars { display: flex; flex-direction: column; gap: 10px; }

.dist-row { display: flex; align-items: center; gap: 10px; }

.dist-label {
  font-size: 12px;
  color: var(--text-secondary);
  width: 60px;
  flex-shrink: 0;
}

.dist-bar-bg {
  flex: 1;
  height: 8px;
  background: rgba(100,80,150,0.1);
  border-radius: 4px;
  overflow: hidden;
}

.dist-bar-fill {
  height: 100%;
  background: var(--gradient-primary);
  border-radius: 4px;
  transition: width 0.8s var(--ease-soft);
}

.dist-count {
  font-size: 12px;
  color: var(--text-muted);
  width: 20px;
  text-align: right;
}

/* ===== 情绪画像墙 ===== */
.gallery-card {
  padding: 16px;
  margin-bottom: 12px;
}

.gallery-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.gallery-sub {
  font-size: 11px;
  color: var(--text-muted);
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.gallery-item {
  cursor: pointer;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(255,255,255,0.3);
  transition: transform 0.2s;
}

.gallery-item:hover {
  transform: scale(1.02);
}

.gallery-img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  display: block;
}

.gallery-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
}

.gallery-emoji { font-size: 14px; }

.gallery-time {
  font-size: 10px;
  color: var(--text-muted);
}

/* 全屏预览 */
.image-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(8px);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.image-preview-card {
  background: var(--glass-strong);
  border-radius: 20px;
  overflow: hidden;
  max-width: 360px;
  width: 100%;
  position: relative;
}

.image-preview-full {
  width: 100%;
  display: block;
}

.image-preview-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--text-secondary);
}

.close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(0,0,0,0.4);
  border: none;
  color: white;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 菜单 */
.menu-list { margin-bottom: 12px; overflow: hidden; }

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-soft);
  border-bottom: 1px solid rgba(100,80,150,0.08);
  font-size: 15px;
  color: var(--text-primary);
}

.menu-item:last-child { border-bottom: none; }
.menu-item:hover { background: rgba(240,147,251,0.05); }
.arrow { margin-left: auto; color: var(--text-muted); }

/* 底部导航 */
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
  width: 48px; height: 48px;
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