package com.example.jfrmerger;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.time.Instant;
import java.util.Arrays;

@Slf4j
public class JfrReader implements AutoCloseable {
    long counterBytes = 4; //init magic bytes

    private final InputStream input;
    private final File file;
    private long features;
    private long ticksPerSecond;
    private long startTicks;
    private long durationNanos;
    private long startTimeNanos;
    private long constantPoolOffset;
    private long chunkSize;
    private long metadataOffset;

    public JfrReader(File file) throws FileNotFoundException {
        this.file = file;
        this.input = new BufferedInputStream(new FileInputStream(file));
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

    private void readEvents() {

    }

    private void readConstantPool() {

    }

    private void readMetadata() throws IOException {
        skipBytes(metadataOffset - counterBytes);

        long size = getInt(); //87312
        long eventType = getLong();
        long start = getLong();
        long duration = getLong();
        long metadataId = getLong();
        long stringCount = getInt(); //1783

        log.info("size: {}, eventType: {}, start: {}, duration: {}, metadataId: {}, stringCount: {}", size, eventType, start, duration, metadataId, stringCount);
        long hz = getLong();

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

        log.info("major: " + major + ", minor: " + minor);
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
}
