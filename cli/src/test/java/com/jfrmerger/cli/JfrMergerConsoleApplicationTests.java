package com.jfrmerger.cli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@SpringBootTest
class JfrMergerConsoleApplicationTests {

    @Test
    //-Duser.timezone="UTC"
    void contextLoads() {
        System.out.println(OffsetDateTime.now(ZoneOffset.UTC));
        System.out.println(LocalDateTime.now().toInstant(ZoneOffset.UTC));
    }
}
