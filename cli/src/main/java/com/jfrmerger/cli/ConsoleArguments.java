package com.jfrmerger.cli;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@Slf4j
public class ConsoleArguments {
    private List<String> files = new ArrayList<>();
    private Long from;
    private Long to;
    private boolean dryRun = false;

    public void addFile(String file) {
        files.add(file);
    }

    public static ConsoleArguments of(String[] args) {
        ConsoleArguments result = new ConsoleArguments();
        int start = 0;
        try {
            while (start != args.length) {
                String arg = args[start];
                if ("--jfr".equals(arg)) {
                    start++;
                    while (!args[start].startsWith("--")) {
                        result.addFile(args[start]);
                        start++;
                    }
                } else if ("--from".equals(arg)) {
                    start++;
                    result.setFrom(Long.parseLong(args[start]));
                    start++;
                } else if ("--to".equals(arg)) {
                    start++;
                    result.setTo(Long.parseLong(args[start]));
                    start++;
                } else if ("--dryrun".equals(arg)) {
                    start++;
                    result.setDryRun(true);
                } else {
                    String error = "Illegal argument " + arg;
                    log.warn(error);
                    throw new IllegalArgumentException(error);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Exception during parse args[{}] = {}", start, args[start]);
            throw e;
        }

        return result;
    }
}
