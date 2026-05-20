package com.ling.linginnerflow.user;

import com.ling.linginnerflow.exception.AccountDisabledException;
import com.ling.linginnerflow.exception.AuthenticationFailedException;
import com.ling.linginnerflow.exception.DuplicateEmailException;
import com.ling.linginnerflow.exception.DuplicateUsernameException;
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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUsernameException(request.getUsername());
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
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(AuthenticationFailedException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthenticationFailedException();
        }

        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }

        log.info("用户登录成功: userId={}", user.getId());

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId().toString(),
                user.getUsername(), user.getEmail());
    }
}