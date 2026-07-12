package jp.yuya.dev.developerdungeon.runner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GitRunnerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GitRunnerApplication.class, args);
    }
}
