package com.jfrmerger.common;

import com.jfrmerger.backend.JfrMergerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
public class TestJfrMergerApplication {

    public static void main(String[] args) {
        SpringApplication.from(JfrMergerApplication::main).with(TestJfrMergerApplication.class).run(args);
    }

}
