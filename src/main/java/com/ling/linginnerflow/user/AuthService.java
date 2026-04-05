package com.ling.linginnerflow.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务
 * 负责注册、登录逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * 注册
     */
    public AuthResponse register(RegisterRequest request) {
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已被使用");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        // 密码加密存储
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User saved = userRepository.save(user);
        log.info("新用户注册成功: userId={}, email={}",
                saved.getId(), saved.getEmail());

        // 生成Token返回
        String token = jwtService.generateToken(saved);
        return new AuthResponse(token, saved.getId().toString(),
                saved.getUsername(), saved.getEmail());
    }

    /**
     * 登录
     */
    public AuthResponse login(LoginRequest request) {
        // 根据邮箱查用户
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("邮箱或密码错误"));

        // 验证密码
        if (!passwordEncoder.matches(
                request.getPassword(), user.getPassword())) {
            throw new RuntimeException("邮箱或密码错误");
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("账号已被禁用");
        }

        log.info("用户登录成功: userId={}", user.getId());

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId().toString(),
                user.getUsername(), user.getEmail());
    }
}