package jp.yuya.dev.developerdungeon.app;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RunnerClientProperties.class)
class AppConfiguration { }
