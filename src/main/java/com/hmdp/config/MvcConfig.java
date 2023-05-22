package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import javax.annotation.Resource;

@Configuration
public class MvcConfig extends DelegatingWebMvcConfiguration {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    protected void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/upload/**",
                "/shop-type/**",
                "/shop/**",
                "/voucher/**"
        ).order(1);
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).
                addPathPatterns("/**").order(0);
    }
}
