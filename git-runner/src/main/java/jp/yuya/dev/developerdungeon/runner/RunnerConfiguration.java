package jp.yuya.dev.developerdungeon.runner;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RunnerProperties.class)
class RunnerConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
}
