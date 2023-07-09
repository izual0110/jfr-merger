package com.example.jfrmerger;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@SpringBootTest
class JfrMergerApplicationTests {

    @Autowired
    private JfrReaderService readerService;
    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    //-Duser.timezone="UTC"
    void contextLoads() {
        System.out.println(OffsetDateTime.now(ZoneOffset.UTC));
        System.out.println(LocalDateTime.now().toInstant(ZoneOffset.UTC));
    }

    @Test
    @SneakyThrows
    void readJFR() {
        readerService.merge(List.of(resourceLoader.getResource("classpath:test.jfr").getFile()), null);
    }

}
