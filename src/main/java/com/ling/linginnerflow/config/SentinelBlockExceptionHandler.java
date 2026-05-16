package com.ling.linginnerflow.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SentinelBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String message = (e instanceof FlowException)
                ? "请求频率超限，请稍后重试"
                : (e instanceof DegradeException)
                        ? "服务降级中，请稍后重试"
                        : "请求被限流，请稍后重试";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 429);
        result.put("message", message);
        result.put("path", request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
