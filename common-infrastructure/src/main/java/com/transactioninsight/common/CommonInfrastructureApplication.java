package com.transactioninsight.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 公共基础设施模块的启动入口，方便在本地验证配置。
 */
@SpringBootApplication(scanBasePackages = "com.transactioninsight")
public class CommonInfrastructureApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommonInfrastructureApplication.class, args);
    }
}
