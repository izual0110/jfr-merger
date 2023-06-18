package com.example.jfrmerger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class JfrReaderService implements InitializingBean {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-hhmmss");
    private File root;
    @Value("${tmp.dir}")
    private String rootDirectory;

    @Override
    public void afterPropertiesSet() {
        root = new File(rootDirectory);
        if (!root.exists() && !root.mkdirs()) {
            throw new RuntimeException("root was not created");
        }
    }


    public File merge(List<File> files) {
        LocalDateTime now = LocalDateTime.now();

        File output = new File(root.getAbsolutePath() + File.separator + formatter.format(now) + ".jfr");
        try {
            output.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        files.forEach(file -> readJfr(file, output));

        return output;
    }

    private void readJfr(File file, File output) {
        try (var jfr = new JfrReader(file)) {
            jfr.readChunk();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
