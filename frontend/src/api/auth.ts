import request from './request'

export interface LoginParams {
    email: string
    password: string
}

export interface RegisterParams {
    username: string
    email: string
    password: string
}

export interface AuthResponse {
    token: string
    userId: string
    username: string
    email: string
}

export const login = (params: LoginParams): Promise<AuthResponse> => {
    return request.post('/api/auth/login', params)
}

export const register = (params: RegisterParams): Promise<AuthResponse> => {
    return request.post('/api/auth/register', params)
}