package com.jfrmerger.common;

import jdk.jfr.consumer.RecordedEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class JfrReader implements AutoCloseable {
    long counterBytes = 4; //init magic bytes

    private final File file;
    private InputStream input;
    private long features;
    private long ticksPerSecond;
    private long startTicks;
    private long durationNanos;
    private long startTimeNanos;
    private long constantPoolOffset;
    private long chunkSize;
    private long metadataOffset;

    public JfrReader(File file) throws IOException {
        this.file = file;
        initBuffer();
    }

    private void initBuffer() throws IOException {
        if (input != null) {
            input.close();
        }
        input = new BufferedInputStream(new FileInputStream(file));
    }

    public boolean readChunk() {
        try {
            readHeader();
            readMetadata();
            readConstantPool();
            readEvents();
        } catch (Exception e) {
            log.error("Error during reading", e);
            return false;
        }

        return true;
    }

    private void readEvents() throws IOException {
        initBuffer();
        skipBytes(68); //skip header

        long size = getLeb128Int();
        long eventType = getLeb128long();
        long start = getLeb128long();
        long duration = getLeb128long();


        log.info("Events size: {}, eventType: {}, start: {}, duration: {}", size, eventType, fromNanos(startTimeNanos+start), duration);
    }

    private void readConstantPool() throws IOException {
        skipBytes(constantPoolOffset - counterBytes);

        long size = getLeb128Int();
        long eventType = getLeb128long();
        long start = getLeb128long();
        long duration = getLeb128long();
        long delta = getLeb128long();
        long checkpointTypeMask = getByte();
        long poolCount = getLeb128Int();

        log.info("Constant pool size: {}, eventType: {}, start: {}, duration: {}", size, eventType, fromNanos(startTimeNanos+start), duration);

    }

    private void readMetadata() throws IOException {
        skipBytes(metadataOffset - counterBytes);

        long size = getLeb128Int();
        long eventType = getLeb128long();
        long start = getLeb128long();
        long duration = getLeb128long();
        long metadataId = getLeb128long();
        long stringCount = getLeb128Int();

        log.info("Metadata size: {}, eventType: {}, start: {}, duration: {}, metadataId: {}, stringCount: {}", size, eventType, fromNanos(startTimeNanos + start), duration, metadataId, stringCount);

        for (int i = 0; i < stringCount; i++) {
            //skip
            //log.info(getString());
        }

        getAttributes(List.of());
    }

    private void getAttributes(List<String> strings) {
        //skip
    }

    private void skipBytes(long size) throws IOException {
        counterBytes += size;
        input.skipNBytes(size);
    }

    private void readHeader() throws IOException {
        byte[] flro = input.readNBytes(4);
        if (flro[0] != 'F' || flro[1] != 'L' || flro[2] != 'R' || flro[3] != 0) {
            throw new RuntimeException("File [" + file.getAbsolutePath() + "] is corrupted, FRL0: [" + Arrays.toString(flro) + "]");
        }


        long major = getShort();
        long minor = getShort();


        this.chunkSize = getLong();
        this.constantPoolOffset = getLong();
        this.metadataOffset = getLong();
        this.startTimeNanos = getLong();
        this.durationNanos = getLong();
        this.startTicks = getLong();
        this.ticksPerSecond = getLong();
        this.features = getInt();

        log.info("major: {}, minor: {}, count bytes: {}", major, minor, counterBytes);
        log.info(toString());
    }

    private long getShort() throws IOException {
        counterBytes += 2;
        return bytesToLong(input.readNBytes(2));
    }

    private long getInt() throws IOException {
        counterBytes += 4;
        return bytesToLong(input.readNBytes(4));
    }


    private long getLong() throws IOException {
        counterBytes += 8;
        return bytesToLong(input.readNBytes(8));
    }

    private static long bytesToLong(byte[] bytes) {
        long result = 0;
        for (byte b : bytes) {
            result = ((result << 8) | (b & 0xff));
        }
        return result;
    }

    @Override
    public String toString() {
        return "JfrReader{" + "features=" + features + ", ticksPerSecond=" + ticksPerSecond + ", startTicks=" + startTicks + ", durationNanos=" + durationNanos + ", startTimeNanos=" + fromNanos(startTimeNanos) + ", constantPoolOffset=" + constantPoolOffset + ", chunkSize=" + chunkSize + ", metadataOffset=" + metadataOffset + '}';
    }

    private static Instant fromNanos(long input) {
        long seconds = input / 1_000_000_000;
        long nanos = input % 1_000_000_000;

        return Instant.ofEpochSecond(seconds, nanos);
    }

    @Override
    @SneakyThrows
    public void close() {
        input.close();
    }

    private int getLeb128Int() throws IOException {
        int result = 0;
        for (int shift = 0; ; shift += 7) {
            byte b = getByte();
            result |= (b & 0x7f) << shift;
            if (b >= 0) {
                return result;
            }
        }
    }

    private long getLeb128long() throws IOException {
        long result = 0;
        for (int shift = 0; shift < 56; shift += 7) {
            byte b = getByte();
            result |= (b & 0x7fL) << shift;
            if (b >= 0) {
                return result;
            }
        }
        return result | (getByte() & 0xffL) << 56;
    }

    private byte getByte() throws IOException {

        RecordedEvent e;

        counterBytes++;
        return (byte) input.read();
    }

    private byte[] getBytes() throws IOException {
        byte count = getByte();
        counterBytes += count;
        return input.readNBytes(count);
    }

    private String getString() throws IOException {
        switch (getByte()) {
            case 0 -> {
                return null;
            }
            case 1 -> {
                return "";
            }
            case 3 -> {
                return new String(getBytes(), StandardCharsets.UTF_8);
            }
            case 4 -> {
                char[] chars = new char[getLeb128Int()];
                for (int i = 0; i < chars.length; i++) {
                    chars[i] = (char) getLeb128Int();
                }
                return new String(chars);
            }
            case 5 -> {
                return new String(getBytes(), StandardCharsets.ISO_8859_1);
            }
            default -> throw new IllegalArgumentException("String is corrupted");
        }
    }
}
