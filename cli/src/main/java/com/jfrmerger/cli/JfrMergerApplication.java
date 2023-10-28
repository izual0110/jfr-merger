package com.jfrmerger.cli;

import com.jfrmerger.common.JfrMergerCommonConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(JfrMergerCommonConfig.class)
public class JfrMergerApplication implements CommandLineRunner {


    @Override
    public void run(String... args) throws Exception {

    }
}
