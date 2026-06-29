<!--
  PetAvatar — 聊天页角落的"见证者"宠物。
  复用共享的 petAnimations 注册表 + LottiePet 渲染器(与 PetView 同源,不重复造)。
  只吃一个 emotion(1-5)prop,做非语言反应:情绪由 Lottie 速度 + 环境动效承载。
  设计:见证而非拯救 —— L5 时主动"退场"(变静、变淡),把舞台让给危机流程。
-->
<template>
  <div class="pet-avatar" :class="`emotion-${emotion}`" aria-hidden="true">
    <LottiePet
      v-if="lottieOk"
      class="pet-avatar__lottie"
      :animation-data="anim.data"
      :speed="anim.speed"
      :loop="anim.loop"
      @error="lottieOk = false"
    />
    <div v-else class="pet-avatar__dot"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import LottiePet from './LottiePet.vue'
import { getPetAnimation } from '@/assets/pet/petAnimations'

const props = withDefaults(defineProps<{ emotion: number }>(), { emotion: 1 })

const lottieOk = ref(true)
const anim = computed(() => getPetAnimation(props.emotion, 0))
</script>

<style scoped>
.pet-avatar {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.6s ease, transform 0.6s ease;
}
.pet-avatar__lottie { width: 100%; height: 100%; }
.pet-avatar__dot {
  width: 40%;
  height: 40%;
  border-radius: 50%;
  background: radial-gradient(circle at 40% 35%, #ffe08a, #f3a712 70%, transparent);
}

/* 情绪 → 环境动效(姿态,非语言)。L1 平静漂浮 … L5 几乎静止、退场。 */
.emotion-1 { animation: pa-float 4s ease-in-out infinite alternate; }
.emotion-2 { animation: pa-sway 3s ease-in-out infinite alternate; }
.emotion-3 { animation: pa-lean 2s ease-in-out infinite alternate; }   /* 微微前倾、专注 */
.emotion-4 { animation: pa-settle 3.5s ease-in-out infinite alternate; opacity: 0.9; } /* 静下来、收敛 */
.emotion-5 { animation: pa-still 6s ease-in-out infinite alternate; opacity: 0.55; }    /* 退场:静、淡 */

@keyframes pa-float { from { transform: translateY(0); } to { transform: translateY(-6px); } }
@keyframes pa-sway  { from { transform: translateX(-3px) rotate(-2deg); } to { transform: translateX(3px) rotate(2deg); } }
@keyframes pa-lean  { from { transform: translateY(0) scale(1); } to { transform: translateY(-2px) scale(1.04); } }
@keyframes pa-settle{ from { transform: scale(0.98); } to { transform: scale(0.94); } }
@keyframes pa-still { from { transform: scale(0.92); } to { transform: scale(0.9); } }

@media (prefers-reduced-motion: reduce) {
  .pet-avatar, .pet-avatar * { animation: none !important; }
}
</style>
