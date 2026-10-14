package net.runelite.client.plugins.microbot.util.security;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.proxy.ProxyConfiguration;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

@Slf4j
public final class HopBudget {
    public static final int DEFAULT_CAPACITY = 390;
    public static final int DEFAULT_REFILL_PER_HOUR = 93;
    public static final int DEFAULT_RESERVE = 20;
    public static final String FILE_NAME = "microbot-hop-budget.properties";

    private static final HopBudget INSTANCE = new HopBudget(System::currentTimeMillis, new MemoryStore());

    private final LongSupplier clock;
    private volatile Store store;
    private volatile boolean enabled = true;
    private volatile int capacity = DEFAULT_CAPACITY;
    private volatile int refillPerHour = DEFAULT_REFILL_PER_HOUR;
    private volatile int reserve = DEFAULT_RESERVE;

    HopBudget(LongSupplier clock, Store store) {
        this.clock = clock;
        this.store = store;
    }

    public static HopBudget getInstance() {
        return INSTANCE;
    }

    public static String networkKey() {
        return networkKey(System.getProperty("socksProxyHost"), System.getProperty("socksProxyPort"),
                ProxyConfiguration.getUsername());
    }

    static String networkKey(String host, String port, String username) {
        if (host == null || host.isBlank()) {
            return "direct";
        }
        StringBuilder key = new StringBuilder("proxy.").append(sanitize(host));
        if (port != null && !port.isBlank()) {
            key.append('_').append(sanitize(port));
        }
        if (username != null && !username.isEmpty()) {
            key.append('.').append(digest(username));
        }
        return key.toString();
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sanitize(String value) {
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public void persistTo(Path file, String networkKey) {
        store = new FileStore(file, networkKey, store);
        current();
    }

    public void configure(boolean enabled, int capacity, int refillPerHour, int reserve) {
        this.capacity = Math.max(1, capacity);
        this.refillPerHour = Math.max(1, refillPerHour);
        this.reserve = Math.min(Math.max(0, reserve), this.capacity - 1);
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int available() {
        return (int) Math.floor(current().tokens);
    }

    public long millisUntilHopAllowed() {
        return enabled ? millisUntil(current()) : 0L;
    }

    public long cachedMillisUntilHopAllowed() {
        return enabled ? millisUntil(refill(store.peek(), clock.getAsLong())) : 0L;
    }

    private long millisUntil(State state) {
        double missing = reserve + 1 - state.tokens;
        if (missing <= 0) {
            return 0L;
        }
        return Math.max(1L, (long) Math.ceil(missing / refillPerMilli()));
    }

    public void recordHop() {
        long now = clock.getAsLong();
        State next = store.update(state -> {
            State refilled = refill(state, now);
            return new State(Math.max(0, refilled.tokens - 1), now);
        });
        if (enabled && next.tokens < reserve + 1) {
            log.info("Hop limit nearly reached - {} hops left, keeping {} in reserve for logins",
                    (int) Math.floor(next.tokens), reserve);
        }
    }

    public void recordLockout() {
        long now = clock.getAsLong();
        store.update(state -> new State(0, now));
    }

    private State current() {
        long now = clock.getAsLong();
        return refill(store.update(state -> state != null && state.updatedAt > now
                ? new State(state.tokens, now) : state), now);
    }

    private State refill(State state, long now) {
        if (state == null) {
            return new State(capacity, now);
        }
        long elapsed = Math.max(0L, now - state.updatedAt);
        double tokens = Math.min(capacity, state.tokens + elapsed * refillPerMilli());
        return new State(tokens, now);
    }

    private double refillPerMilli() {
        return refillPerHour / (double) TimeUnit.HOURS.toMillis(1);
    }

    static final class State {
        final double tokens;
        final long updatedAt;

        State(double tokens, long updatedAt) {
            this.tokens = tokens;
            this.updatedAt = updatedAt;
        }
    }

    interface Store {
        State update(UnaryOperator<State> change);

        State peek();
    }

    static final class MemoryStore implements Store {
        private State state;

        MemoryStore() {
        }

        MemoryStore(State state) {
            this.state = state;
        }

        @Override
        public synchronized State update(UnaryOperator<State> change) {
            state = change.apply(state);
            return state;
        }

        @Override
        public synchronized State peek() {
            return state;
        }
    }

    static final class FileStore implements Store {
        private final Path file;
        private final Path lockFile;
        private final String tokensKey;
        private final String updatedAtKey;
        private final MemoryStore fallback;
        private boolean warned;

        FileStore(Path file, String networkKey, Store seed) {
            this.file = file;
            this.lockFile = file.resolveSibling(file.getFileName() + ".lock");
            this.tokensKey = networkKey + ".tokens";
            this.updatedAtKey = networkKey + ".updatedAt";
            this.fallback = new MemoryStore(seed.peek());
        }

        @Override
        public synchronized State update(UnaryOperator<State> change) {
            boolean interrupted = Thread.interrupted();
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                    Properties properties = read();
                    State current = parse(properties);
                    State next = change.apply(current);
                    if (next != current && next != null) {
                        properties.setProperty(tokensKey, Double.toString(next.tokens));
                        properties.setProperty(updatedAtKey, Long.toString(next.updatedAt));
                        write(properties);
                    }
                    fallback.update(state -> next);
                    return next;
                }
            } catch (IOException | RuntimeException e) {
                if (!warned) {
                    warned = true;
                    log.warn("Hop budget file {} unavailable, tracking hops for this client only", file, e);
                }
                return fallback.update(change);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public State peek() {
            return fallback.peek();
        }

        private State parse(Properties properties) {
            String tokens = properties.getProperty(tokensKey);
            String updatedAt = properties.getProperty(updatedAtKey);
            if (tokens == null || updatedAt == null) {
                return null;
            }
            try {
                double parsedTokens = Double.parseDouble(tokens);
                if (!Double.isFinite(parsedTokens) || parsedTokens < 0) {
                    return null;
                }
                return new State(parsedTokens, Long.parseLong(updatedAt));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Properties read() throws IOException {
            Properties properties = new Properties();
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (NoSuchFileException e) {
                return properties;
            }
            properties.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            return properties;
        }

        private void write(Properties properties) throws IOException {
            StringWriter writer = new StringWriter();
            properties.store(writer, null);
            ByteBuffer buffer = ByteBuffer.wrap(writer.toString().getBytes(StandardCharsets.UTF_8));
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), file.getFileName().toString(), ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(false);
                }
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }
}
