package com.jfrmerger.common;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@SpringBootTest
@SpringBootConfiguration
@Import(JfrMergerCommonConfig.class)
class JfrMergerCommonTests {

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
    void mergeJFR() {
        File file = resourceLoader.getResource("classpath:test.jfr").getFile();
        File merge = readerService.merge(List.of(file), null);
        Assertions.assertNotNull(merge);
    }

    @Test
    @SneakyThrows
    void readJFR() {
        File file = resourceLoader.getResource("classpath:test.jfr").getFile();
        readerService.readJfr(file);
    }

}
