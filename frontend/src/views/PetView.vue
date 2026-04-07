<template>
  <div class="pet-container" :style="{ '--pet-color': pet.primaryColor }">
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>

    <!-- 顶部 -->
    <div class="top-bar glass-card">
      <h2 class="page-title">✨ Flowy</h2>
      <div class="level-badge">
        {{ levelName }}
      </div>
    </div>

    <!-- 精灵主体 -->
    <div class="sprite-area">
      <!-- 外层光环（level 4-5才显示） -->
      <div v-if="pet.level >= 4" class="aura-ring"></div>

      <!-- 精灵本体 -->
      <div class="sprite-wrapper" :class="`emotion-${pet.currentEmotion}`">
        <svg class="sprite-svg" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <filter id="glow">
              <feGaussianBlur stdDeviation="8" result="coloredBlur"/>
              <feMerge>
                <feMergeNode in="coloredBlur"/>
                <feMergeNode in="SourceGraphic"/>
              </feMerge>
            </filter>
            <filter id="blob">
              <feGaussianBlur in="SourceGraphic" stdDeviation="10" result="blur"/>
              <feColorMatrix in="blur" mode="matrix"
                             values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 20 -10" result="blob"/>
            </filter>
            <radialGradient id="spriteGrad" cx="40%" cy="35%">
              <stop offset="0%" :stop-color="pet.primaryColor" stop-opacity="0.9"/>
              <stop offset="60%" :stop-color="pet.primaryColor" stop-opacity="0.5"/>
              <stop offset="100%" :stop-color="pet.primaryColor" stop-opacity="0.1"/>
            </radialGradient>
          </defs>

          <!-- 雾气态：模糊的不规则形状 -->
          <g v-if="pet.level === 1" filter="url(#blob)" opacity="0.8">
            <circle cx="100" cy="100" r="50" fill="url(#spriteGrad)"/>
            <circle cx="85" cy="90" r="30" fill="url(#spriteGrad)"/>
            <circle cx="115" cy="110" r="35" fill="url(#spriteGrad)"/>
          </g>

          <!-- 成形态：有轮廓的球体 -->
          <g v-else-if="pet.level === 2" filter="url(#glow)">
            <circle cx="100" cy="100" r="55" fill="url(#spriteGrad)" opacity="0.7"/>
            <circle cx="100" cy="100" r="40" fill="url(#spriteGrad)" opacity="0.5"/>
            <circle cx="85" cy="85" r="10" fill="white" opacity="0.3"/>
          </g>

          <!-- 稳定态：清晰球体 -->
          <g v-else-if="pet.level === 3" filter="url(#glow)">
            <circle cx="100" cy="100" r="58" fill="url(#spriteGrad)" opacity="0.6"/>
            <circle cx="100" cy="100" r="42" fill="url(#spriteGrad)" opacity="0.8"/>
            <circle cx="83" cy="83" r="12" fill="white" opacity="0.4"/>
            <circle cx="95" cy="78" r="5" fill="white" opacity="0.3"/>
          </g>

          <!-- 光环态：外围光环 -->
          <g v-else-if="pet.level === 4" filter="url(#glow)">
            <circle cx="100" cy="100" r="65" :stroke="pet.primaryColor"
                    stroke-width="2" fill="none" opacity="0.4"/>
            <circle cx="100" cy="100" r="55" fill="url(#spriteGrad)" opacity="0.6"/>
            <circle cx="100" cy="100" r="40" fill="url(#spriteGrad)" opacity="0.9"/>
            <circle cx="82" cy="82" r="13" fill="white" opacity="0.5"/>
            <circle cx="96" cy="76" r="6" fill="white" opacity="0.3"/>
          </g>

          <!-- 晶核态：有核心晶体 -->
          <g v-else filter="url(#glow)">
            <circle cx="100" cy="100" r="70" :stroke="pet.primaryColor"
                    stroke-width="1.5" fill="none" opacity="0.3"/>
            <circle cx="100" cy="100" r="58" :stroke="pet.primaryColor"
                    stroke-width="1" fill="none" opacity="0.2"/>
            <circle cx="100" cy="100" r="50" fill="url(#spriteGrad)" opacity="0.7"/>
            <circle cx="100" cy="100" r="35" fill="url(#spriteGrad)" opacity="0.9"/>
            <!-- 核心晶体 -->
            <polygon points="100,75 112,95 100,115 88,95"
                     fill="white" opacity="0.6"/>
            <circle cx="82" cy="82" r="14" fill="white" opacity="0.5"/>
            <circle cx="96" cy="75" r="6" fill="white" opacity="0.4"/>
          </g>
        </svg>

        <!-- 粒子效果 -->
        <div class="particles">
          <div v-for="i in particleCount" :key="i"
               class="particle"
               :style="particleStyle(i)">
          </div>
        </div>
      </div>

      <!-- 精灵名字和状态 -->
      <div class="sprite-name">Flowy</div>
      <div class="sprite-state">{{ stateName }}</div>
    </div>

    <!-- 凝聚度进度条 -->
    <div class="cohesion-section glass-card">
      <div class="cohesion-header">
        <span class="cohesion-label">凝聚度</span>
        <span class="cohesion-value">{{ pet.cohesion }}/100</span>
      </div>
      <div class="cohesion-bar-bg">
        <div class="cohesion-bar-fill"
             :style="{ width: pet.cohesion + '%' }">
        </div>
      </div>
      <div class="next-level-hint" v-if="pet.level < 5">
        还差 {{ nextLevelThreshold - pet.cohesion }} 点进化为{{ nextLevelName }}
      </div>
    </div>

    <!-- 三元能量 -->
    <div class="energy-cards">
      <div class="energy-card glass-card">
        <span class="energy-icon">🧠</span>
        <span class="energy-value">{{ formatEnergy(pet.awareness) }}</span>
        <span class="energy-label">感知力</span>
        <span class="energy-hint">对话获得</span>
      </div>
      <div class="energy-card glass-card">
        <span class="energy-icon">🎯</span>
        <span class="energy-value">{{ formatEnergy(pet.vitality) }}</span>
        <span class="energy-label">生命力</span>
        <span class="energy-hint">Tap获得</span>
      </div>
      <div class="energy-card glass-card">
        <span class="energy-icon">🌿</span>
        <span class="energy-value">{{ formatEnergy(pet.stability) }}</span>
        <span class="energy-label">稳定性</span>
        <span class="energy-hint">打卡获得</span>
      </div>
    </div>

    <!-- 成长值 -->
    <div class="growth-card glass-card">
      <span class="growth-label">✨ 累计成长值</span>
      <span class="growth-value">{{ pet.growthPoints }}</span>
    </div>

    <!-- 底部导航 -->
    <div class="bottom-nav glass-card">
      <button @click="goTo('/')">💬</button>
      <button @click="goTo('/tap')">🎯</button>
      <button @click="goTo('/wall')">🌿</button>
      <button @click="goTo('/pet')" class="active">✨</button>
      <button @click="goTo('/profile')">👤</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'

const router = useRouter()

interface Pet {
  level: number
  cohesion: number
  awareness: number
  vitality: number
  stability: number
  growthPoints: number
  primaryColor: string
  currentEmotion: number
}

const pet = ref<Pet>({
  level: 1,
  cohesion: 0,
  awareness: 0,
  vitality: 0,
  stability: 0,
  growthPoints: 0,
  primaryColor: '#b8f0e0',
  currentEmotion: 1
})

function goTo(path: string) { router.push(path) }

const levelName = computed(() => {
  const map: Record<number, string> = {
    1: 'Lv.1 雾气态',
    2: 'Lv.2 成形态',
    3: 'Lv.3 稳定态',
    4: 'Lv.4 光环态',
    5: 'Lv.5 晶核态'
  }
  return map[pet.value.level] || 'Lv.1'
})

const stateName = computed(() => {
  const emotion = pet.value.currentEmotion
  const map: Record<number, string> = {
    1: '平静地漂浮着',
    2: '轻轻晃动着',
    3: '有些不安地收缩',
    4: '缩在角落，但还在',
    5: '静静地陪着你'
  }
  return map[emotion] || '平静地漂浮着'
})

const particleCount = computed(() => pet.value.level * 3)

const nextLevelThreshold = computed(() => {
  const map: Record<number, number> = { 1: 20, 2: 40, 3: 60, 4: 80, 5: 100 }
  return map[pet.value.level] || 100
})

const nextLevelName = computed(() => {
  const map: Record<number, string> = {
    1: '成形态', 2: '稳定态', 3: '光环态', 4: '晶核态', 5: '满级'
  }
  return map[pet.value.level] || ''
})

function formatEnergy(val: number): string {
  if (val === undefined || val === null) return '0'
  return Number(val).toFixed(1)
}

function particleStyle(i: number) {
  const angle = (i / particleCount.value) * 360
  const delay = (i * 0.3) % 3
  const size = 4 + (i % 3) * 2
  return {
    '--angle': angle + 'deg',
    '--delay': delay + 's',
    '--size': size + 'px',
    '--color': pet.value.primaryColor
  }
}

async function loadPet() {
  try {
    const res = await request.get('/api/pet') as any
    pet.value = {
      level: res.level || 1,
      cohesion: res.cohesion || 0,
      awareness: res.awareness || 0,
      vitality: res.vitality || 0,
      stability: res.stability || 0,
      growthPoints: res.growthPoints || 0,
      primaryColor: res.primaryColor || '#b8f0e0',
      currentEmotion: res.currentEmotion || 1
    }
  } catch (e) {
    console.error('加载宠物失败', e)
  }
}

onMounted(() => {
  loadPet()
})
</script>

<style scoped>
.pet-container {
  min-height: 100vh;
  background: var(--gradient-bg);
  padding: 12px 12px 100px;
  position: relative;
  overflow-x: hidden;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.bg-orb-1 {
  width: 400px; height: 400px;
  background: radial-gradient(circle, var(--pet-color, #b8f0e0), #d4b8ff);
  top: -100px; right: -100px; opacity: 0.3;
  transition: background 1s ease;
}

.bg-orb-2 {
  width: 300px; height: 300px;
  background: radial-gradient(circle, #b8e0ff, var(--pet-color, #b8f0e0));
  bottom: 100px; left: -80px; opacity: 0.25;
  transition: background 1s ease;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.level-badge {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--glass-light);
  padding: 4px 12px;
  border-radius: 12px;
}

/* 精灵区域 */
.sprite-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0 10px;
  position: relative;
}

.aura-ring {
  position: absolute;
  width: 220px; height: 220px;
  border-radius: 50%;
  border: 1px solid var(--pet-color, #b8f0e0);
  opacity: 0.4;
  animation: aura-pulse 3s ease-in-out infinite;
}

@keyframes aura-pulse {
  0%, 100% { transform: scale(1); opacity: 0.4; }
  50% { transform: scale(1.1); opacity: 0.2; }
}

.sprite-wrapper {
  width: 180px; height: 180px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 情绪动效 */
.emotion-1 { animation: float-calm 4s ease-in-out infinite alternate; }
.emotion-2 { animation: float-gentle 3s ease-in-out infinite alternate; }
.emotion-3 { animation: shrink-anxious 2s ease-in-out infinite alternate; }
.emotion-4 { animation: hide-corner 3s ease-in-out infinite alternate; }
.emotion-5 { animation: still-crisis 5s ease-in-out infinite alternate; }

@keyframes float-calm {
  from { transform: translateY(0) scale(1); }
  to { transform: translateY(-12px) scale(1.03); }
}

@keyframes float-gentle {
  from { transform: translateY(0) rotate(-2deg); }
  to { transform: translateY(-8px) rotate(2deg); }
}

@keyframes shrink-anxious {
  from { transform: scale(0.95); }
  to { transform: scale(1.05); filter: brightness(0.9); }
}

@keyframes hide-corner {
  from { transform: translate(0, 0) scale(0.9); }
  to { transform: translate(8px, 8px) scale(0.85); filter: brightness(0.7); }
}

@keyframes still-crisis {
  from { transform: scale(1); filter: brightness(0.6); }
  to { transform: scale(1.02); filter: brightness(0.7); }
}

.sprite-svg {
  width: 100%; height: 100%;
  filter: drop-shadow(0 0 20px var(--pet-color, #b8f0e0));
  transition: filter 1s ease;
}

/* 粒子 */
.particles {
  position: absolute;
  width: 100%; height: 100%;
  pointer-events: none;
}

.particle {
  position: absolute;
  width: var(--size, 4px);
  height: var(--size, 4px);
  border-radius: 50%;
  background: var(--color, #b8f0e0);
  top: 50%; left: 50%;
  opacity: 0;
  animation: orbit 3s var(--delay, 0s) ease-in-out infinite;
}

@keyframes orbit {
  0% {
    transform: rotate(var(--angle)) translateX(70px) scale(0);
    opacity: 0;
  }
  30% { opacity: 0.8; }
  70% { opacity: 0.4; }
  100% {
    transform: rotate(calc(var(--angle) + 360deg)) translateX(90px) scale(0);
    opacity: 0;
  }
}

.sprite-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 12px;
}

.sprite-state {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 4px;
  font-style: italic;
}

/* 凝聚度 */
.cohesion-section {
  padding: 16px;
  margin-bottom: 12px;
}

.cohesion-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.cohesion-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.cohesion-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.cohesion-bar-bg {
  height: 8px;
  background: rgba(100,80,150,0.1);
  border-radius: 4px;
  overflow: hidden;
}

.cohesion-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--pet-color, #b8f0e0), #667eea);
  border-radius: 4px;
  transition: width 1s ease;
}

.next-level-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 6px;
  text-align: right;
}

/* 三元能量 */
.energy-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.energy-card {
  padding: 14px 10px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.energy-icon { font-size: 20px; }

.energy-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.energy-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.energy-hint {
  font-size: 10px;
  color: var(--text-muted);
}

/* 成长值 */
.growth-card {
  padding: 14px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.growth-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.growth-value {
  font-size: 20px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

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
  width: 44px; height: 44px;
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