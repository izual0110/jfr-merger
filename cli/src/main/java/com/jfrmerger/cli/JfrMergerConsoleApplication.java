package com.jfrmerger.cli;

import com.jfrmerger.common.JfrMergerCommonConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Slf4j
@SpringBootApplication
@Import(JfrMergerCommonConfig.class)
public class JfrMergerConsoleApplication implements CommandLineRunner {


    public static void main(String[] args) {
        log.info("start");
        SpringApplication.run(JfrMergerConsoleApplication.class, args);
        log.info("finish");
    }

    @Override
    public void run(String... args) throws Exception {

    }
}
