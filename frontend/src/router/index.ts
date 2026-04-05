import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/login',
            name: 'login',
            component: () => import('@/views/LoginView.vue'),
            meta: { requiresAuth: false }
        },
        {
            path: '/',
            name: 'chat',
            component: () => import('@/views/ChatView.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/tap',
            name: 'tap',
            component: () => import('@/views/TapView.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/wall',
            name: 'wall',
            component: () => import('@/views/WallView.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/profile',
            name: 'profile',
            component: () => import('@/views/ProfileView.vue'),
            meta: { requiresAuth: true }
        }
    ]
})

// 路由守卫：未登录跳转到登录页
router.beforeEach((to) => {
    const authStore = useAuthStore()
    if (to.meta.requiresAuth && !authStore.isLoggedIn) {
        return { name: 'login' }
    }
})

export default router