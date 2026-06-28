// 宠物动画注册表 —— 把后端的 currentEmotion(1-5) / level 映射到具体 Lottie 动画。
// 以后要换素材 / 加情绪专属动画，只改这个文件即可。
import flowyCat from './flowy-cat.json'

export interface PetAnimationConfig {
  data: unknown   // Lottie JSON
  speed: number   // 播放速度
  loop: boolean
}

// 情绪 → 播放速度。
// 角色形象保持一致（同一只 Flowy），情绪主要由速度 + PetView 里的
// 光晕/背景球/粒子/漂浮动效来承载，所以单一动画也能表达 5 种情绪。
const EMOTION_SPEED: Record<number, number> = {
  1: 0.8,  // 平静：呼吸般缓慢
  2: 1.0,  // 轻柔：正常
  3: 1.3,  // 困扰：略快、躁动
  4: 0.7,  // 退缩：放慢
  5: 0.5,  // 危机：几乎静止、安静陪伴
}

// 可选：情绪专属动画覆盖。以后做好对应素材后这样填即可：
//   import flowySad from './flowy-sad.json'
//   const EMOTION_ANIM: Record<number, unknown> = { 4: flowySad, 5: flowySad }
const EMOTION_ANIM: Record<number, unknown> = {}

export function getPetAnimation(emotion: number, _level: number): PetAnimationConfig {
  return {
    data: EMOTION_ANIM[emotion] ?? flowyCat,
    speed: EMOTION_SPEED[emotion] ?? 1.0,
    loop: true,
  }
}
