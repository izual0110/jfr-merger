package com.jfrmerger.cli;

import com.jfrmerger.common.JfrMergerCommonConfig;
import com.jfrmerger.common.JfrReaderService;
import com.jfrmerger.common.model.TimeRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.util.List;

import static com.jfrmerger.cli.ConsoleArguments.of;

@Slf4j
@SpringBootApplication
@Import(JfrMergerCommonConfig.class)
@RequiredArgsConstructor
public class JfrMergerConsoleApplication implements CommandLineRunner {

    @Value("${record.dir}")
    String string;

    @Value("${tmp.dir}")
    String outputDir;

    private final JfrReaderService service;


    public static void main(String[] args) {
        log.info("start");
        SpringApplication.run(JfrMergerConsoleApplication.class, args);
        log.info("finish");
    }

    @Override
    public void run(String... args) {
        printProperties();
        ConsoleArguments arguments = of(args);
        log.info("arguments: " + arguments);


        if (arguments.isDryRun()) {
            arguments.getFiles().stream().map(File::new).forEach(service::readJfr);
        } else {
            List<File> files = arguments.getFiles().stream().map(File::new).toList();
            File outputFile = service.merge(files, TimeRange.of(arguments.getFrom(), arguments.getTo()));
            log.info("result: {}", outputFile.getAbsolutePath());
        }
    }

    private void printProperties() {
        log.info("Output directory: {}", outputDir);
    }


}
