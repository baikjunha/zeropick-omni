package com.zeropick.stockservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 기동 직후 카탈로그 초기 적재를 비동기로 돌리기 위한 설정 (기동 블로킹 방지). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
