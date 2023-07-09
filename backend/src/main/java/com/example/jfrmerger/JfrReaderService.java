package com.example.jfrmerger;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class JfrReaderService {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-hhmmss");
    private final File root;

    public JfrReaderService(@Value("${tmp.dir}") String directory) {
        root = new File(directory);
        if (!root.exists() && !root.mkdirs()) {
            throw new RuntimeException("root was not created");
        }
    }

    public File merge(List<File> files) {
        LocalDateTime now = LocalDateTime.now();

        File output = new File(root.getAbsolutePath() + File.separator + formatter.format(now) + ".jfr");
        try {
            if (!output.createNewFile()) {
                throw new RuntimeException("File already exists");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        files.forEach(file -> readJfr(file, output));

        return output;
    }

    private void oldReadJfr(File file, File output) {
        try (var jfr = new JfrReader(file)) {
            while (jfr.readChunk()) {
                log.info("chunk was read");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readJfr(File input, File output) {
        try (RecordingFile recording = new RecordingFile(input.toPath())) {
            while (recording.hasMoreEvents()) {
                RecordedEvent e = recording.readEvent();
//                e.getStartTime()
                System.out.println(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeJfr(File input, File output) {
//        FlightRecorder.getFlightRecorder().getRecordings().forEach(it->it.dump());

//        try (Recording r = new Recording()) {
//            r.dump();
//        }


        try (RecordingFile recording = new RecordingFile(input.toPath())) {
            recording.write(output.toPath(), e -> e.getThread() != null);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
