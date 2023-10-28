package com.jfrmerger.backend;

import com.jfrmerger.common.JfrMergerCommonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;


@SpringBootApplication
@Import(JfrMergerCommonConfig.class)
public class JfrMergerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JfrMergerApplication.class, args);
    }

}
