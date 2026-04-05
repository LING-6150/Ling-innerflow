import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
    const token = ref<string>(localStorage.getItem('token') || '')
    const userId = ref<string>(localStorage.getItem('userId') || '')
    const username = ref<string>(localStorage.getItem('username') || '')

    const isLoggedIn = computed(() => !!token.value)

    function setAuth(data: {
        token: string
        userId: string
        username: string
    }) {
        token.value = data.token
        userId.value = data.userId
        username.value = data.username
        localStorage.setItem('token', data.token)
        localStorage.setItem('userId', data.userId)
        localStorage.setItem('username', data.username)
    }

    function logout() {
        token.value = ''
        userId.value = ''
        username.value = ''
        localStorage.removeItem('token')
        localStorage.removeItem('userId')
        localStorage.removeItem('username')
    }

    return { token, userId, username, isLoggedIn, setAuth, logout }
})