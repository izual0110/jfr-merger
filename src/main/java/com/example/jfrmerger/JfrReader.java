package com.example.jfrmerger;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
public class JfrReader implements AutoCloseable {

    private final InputStream input;
    private final File file;
    private int features;
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

    public void readChunk() throws IOException {
        readHeader();
        readMetadata();
        readConstantPool();
        readEvents();
    }

    private void readEvents() {

    }

    private void readConstantPool() {

    }

    private void readMetadata() {

    }

    private void readHeader() throws IOException {
        byte[] flro = input.readNBytes(4);
        if (flro[0] != 'F' || flro[1] != 'L' || flro[2] != 'R' || flro[3] != 0) {
            log.error(Arrays.toString(flro));
            throw new RuntimeException("File [" + file.getAbsolutePath() + "] is corrupted");
        }

        ;
        int major = ByteBuffer.wrap(input.readNBytes(2)).getShort();
        int minor = ByteBuffer.wrap(input.readNBytes(2)).getShort();


        this.chunkSize = ByteBuffer.wrap(input.readNBytes(8)).getLong();
        this.constantPoolOffset = ByteBuffer.wrap(input.readNBytes(8)).getLong();
        this.metadataOffset = ByteBuffer.wrap(input.readNBytes(8)).getLong();
        this.startTimeNanos = ByteBuffer.wrap(input.readNBytes(8)).getLong();
        this.durationNanos = ByteBuffer.wrap(input.readNBytes(8)).getLong();
        this.startTicks = ByteBuffer.wrap(input.readNBytes(8)).getLong();
        this.ticksPerSecond = ByteBuffer.wrap(input.readNBytes(8)).getLong();
        this.features = ByteBuffer.wrap(input.readNBytes(8)).getInt();

        log.info("major: " + major + ", minor: " + minor);
        log.info(toString());
    }

    @Override
    public String toString() {
        return "JfrReader{" +
                "features=" + features +
                ", ticksPerSecond=" + ticksPerSecond +
                ", startTicks=" + startTicks +
                ", durationNanos=" + durationNanos +
                ", startTimeNanos=" + fromNanos(startTimeNanos) +
                ", constantPoolOffset=" + constantPoolOffset +
                ", chunkSize=" + chunkSize +
                ", metadataOffset=" + metadataOffset +
                '}';
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
