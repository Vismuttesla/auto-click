package com.click.precisiontrigger.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class AsyncJsonlLogWriter implements StructuredLogService {
    private static final OpenOption[] OPEN_OPTIONS = {
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND
    };

    private final Path directory;
    private final LogRotationPolicy rotationPolicy;
    private final LogEventSerializer serializer;
    private final BlockingQueue<LogCommand> queue;
    private final Duration criticalOfferTimeout;
    private final AtomicLong droppedEvents = new AtomicLong();
    private final AtomicReference<Throwable> workerFailure = new AtomicReference<>();
    private final Thread workerThread;
    private volatile boolean accepting = true;

    public AsyncJsonlLogWriter(
            Path directory,
            LogRotationPolicy rotationPolicy,
            LogEventSerializer serializer,
            int queueCapacity,
            Duration criticalOfferTimeout
    ) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.rotationPolicy = Objects.requireNonNull(rotationPolicy, "rotationPolicy");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.criticalOfferTimeout = Objects.requireNonNull(criticalOfferTimeout, "criticalOfferTimeout");
        this.workerThread = new Thread(this::runWriter, "precision-trigger-jsonl-writer");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    @Override
    public boolean log(LogEvent event) {
        Objects.requireNonNull(event, "event");
        ensureHealthy();
        if (!accepting) {
            return false;
        }

        LogCommand command = LogCommand.write(event);
        boolean enqueued;
        try {
            if (event.priority() == LogPriority.CRITICAL) {
                enqueued = queue.offer(command, criticalOfferTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                enqueued = queue.offer(command);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while enqueuing log event " + event.eventType(), ex);
        }

        if (!enqueued) {
            droppedEvents.incrementAndGet();
        }
        return enqueued;
    }

    @Override
    public long droppedEvents() {
        return droppedEvents.get();
    }

    @Override
    public void flush(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        ensureHealthy();
        CountDownLatch latch = new CountDownLatch(1);
        offerControlCommand(LogCommand.flush(latch));
        awaitLatch(latch, timeout, "Timed out waiting for log flush");
        ensureHealthy();
    }

    @Override
    public void close() {
        accepting = false;
        CountDownLatch latch = new CountDownLatch(1);
        offerControlCommand(LogCommand.shutdown(latch));
        awaitLatch(latch, Duration.ofSeconds(5), "Timed out waiting for log writer shutdown");
        try {
            workerThread.join(Duration.ofSeconds(5).toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for log writer thread to stop", ex);
        }
        ensureHealthy();
    }

    private void offerControlCommand(LogCommand command) {
        while (true) {
            ensureHealthy();
            try {
                if (queue.offer(command, 100, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while sending control command to log writer", ex);
            }
        }
    }

    private void awaitLatch(CountDownLatch latch, Duration timeout, String timeoutMessage) {
        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(timeoutMessage);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for log writer", ex);
        }
    }

    private void ensureHealthy() {
        Throwable failure = workerFailure.get();
        if (failure != null) {
            throw new IllegalStateException("Async JSONL writer failed", failure);
        }
    }

    private void runWriter() {
        BufferedWriter activeWriter = null;
        Path activePath = null;
        try {
            Files.createDirectories(directory);
            while (true) {
                LogCommand command = queue.take();
                if (command.type == LogCommandType.WRITE) {
                    Instant timestamp = command.event.timestamp();
                    Path targetPath = rotationPolicy.resolvePath(directory, timestamp);
                    if (!targetPath.equals(activePath)) {
                        activeWriter = replaceWriter(activeWriter, targetPath);
                        activePath = targetPath;
                    }
                    activeWriter.write(serializer.serialize(command.event));
                    activeWriter.newLine();
                } else if (command.type == LogCommandType.FLUSH) {
                    if (activeWriter != null) {
                        activeWriter.flush();
                    }
                    command.signalCompletion();
                } else if (command.type == LogCommandType.SHUTDOWN) {
                    if (activeWriter != null) {
                        activeWriter.flush();
                        activeWriter.close();
                    }
                    command.signalCompletion();
                    return;
                }
            }
        } catch (Throwable failure) {
            workerFailure.compareAndSet(null, failure);
            if (activeWriter != null) {
                try {
                    activeWriter.close();
                } catch (IOException ignored) {
                    // best-effort close after a worker failure
                }
            }
        }
    }

    private BufferedWriter replaceWriter(BufferedWriter activeWriter, Path targetPath) throws IOException {
        if (activeWriter != null) {
            activeWriter.flush();
            activeWriter.close();
        }
        return Files.newBufferedWriter(targetPath, StandardCharsets.UTF_8, OPEN_OPTIONS);
    }

    private enum LogCommandType {
        WRITE,
        FLUSH,
        SHUTDOWN
    }

    private record LogCommand(LogCommandType type, LogEvent event, CountDownLatch completionLatch) {
        private static LogCommand write(LogEvent event) {
            return new LogCommand(LogCommandType.WRITE, event, null);
        }

        private static LogCommand flush(CountDownLatch latch) {
            return new LogCommand(LogCommandType.FLUSH, null, latch);
        }

        private static LogCommand shutdown(CountDownLatch latch) {
            return new LogCommand(LogCommandType.SHUTDOWN, null, latch);
        }

        private void signalCompletion() {
            if (completionLatch != null) {
                completionLatch.countDown();
            }
        }
    }
}
