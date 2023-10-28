package com.jfrmerger.cli;

import com.jfrmerger.common.JfrReaderService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@SpringBootTest
class JfrMergerApplicationTests {

    @Test
    //-Duser.timezone="UTC"
    void contextLoads() {
        System.out.println(OffsetDateTime.now(ZoneOffset.UTC));
        System.out.println(LocalDateTime.now().toInstant(ZoneOffset.UTC));
    }
}
