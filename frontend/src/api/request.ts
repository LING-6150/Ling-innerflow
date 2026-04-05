import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const request = axios.create({
    baseURL: '',  // 通过Vite代理转发
    timeout: 30000
})

// 请求拦截器：自动加Token
request.interceptors.request.use((config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
        config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
})

// 响应拦截器：Token过期跳转登录
request.interceptors.response.use(
    (response) => response.data,
    (error) => {
        if (error.response?.status === 401 ||
            error.response?.status === 403) {
            const authStore = useAuthStore()
            authStore.logout()
            router.push('/login')
        }
        return Promise.reject(error)
    }
)

export default request