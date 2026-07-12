package jp.yuya.dev.developerdungeon.app;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RunnerClientProperties.class)
class AppConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
}
