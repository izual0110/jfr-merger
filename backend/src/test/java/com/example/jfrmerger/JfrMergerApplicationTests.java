package com.example.jfrmerger;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

import java.util.List;

@SpringBootTest
class JfrMergerApplicationTests {

    @Autowired
    private JfrReaderService readerService;
    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    void contextLoads() {
    }

    @Test
    @SneakyThrows
    void readJFR() {
        readerService.merge(List.of(resourceLoader.getResource("classpath:test.jfr").getFile()));
    }

}
