<template>
  <div ref="container" class="lottie-pet"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import lottie, { type AnimationItem } from 'lottie-web'

const props = withDefaults(defineProps<{
  animationData: unknown
  speed?: number
  loop?: boolean
}>(), { speed: 1, loop: true })

const emit = defineEmits<{
  (e: 'ready'): void
  (e: 'error'): void
}>()

const container = ref<HTMLElement | null>(null)
let anim: AnimationItem | null = null

function destroy() {
  if (anim) {
    anim.destroy()
    anim = null
  }
}

function build() {
  destroy()
  if (!container.value || !props.animationData) {
    emit('error')
    return
  }
  try {
    anim = lottie.loadAnimation({
      container: container.value,
      renderer: 'svg',
      loop: props.loop,
      autoplay: true,
      animationData: props.animationData as object,
    })
    anim.setSpeed(props.speed)
    anim.addEventListener('DOMLoaded', () => emit('ready'))
    anim.addEventListener('data_failed', () => emit('error'))
  } catch {
    emit('error')
  }
}

onMounted(build)
onBeforeUnmount(destroy)
// 切换情绪 → 换动画时重建
watch(() => props.animationData, build)
watch(() => props.speed, (s) => anim?.setSpeed(s))
</script>

<style scoped>
.lottie-pet {
  width: 100%;
  height: 100%;
}
.lottie-pet :deep(svg) {
  width: 100% !important;
  height: 100% !important;
}
</style>
