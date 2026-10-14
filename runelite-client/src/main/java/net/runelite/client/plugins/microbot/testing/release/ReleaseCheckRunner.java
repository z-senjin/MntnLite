package net.runelite.client.plugins.microbot.testing.release;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

public class ReleaseCheckRunner {

    public static final class Options {
        private boolean failFast;
        private long totalTimeoutMs = 600_000L;
        private long cleanupTimeoutMs = 60_000L;
        private long terminationWaitMs = 5_000L;

        public Options failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public Options totalTimeoutMs(long totalTimeoutMs) {
            this.totalTimeoutMs = totalTimeoutMs;
            return this;
        }

        public Options cleanupTimeoutMs(long cleanupTimeoutMs) {
            this.cleanupTimeoutMs = cleanupTimeoutMs;
            return this;
        }

        public Options terminationWaitMs(long terminationWaitMs) {
            this.terminationWaitMs = terminationWaitMs;
            return this;
        }

        public boolean isFailFast() {
            return failFast;
        }

        public long getTotalTimeoutMs() {
            return totalTimeoutMs;
        }
    }

    private final Options options;
    private final LongSupplier clock;

    public ReleaseCheckRunner(Options options) {
        this(options, System::currentTimeMillis);
    }

    ReleaseCheckRunner(Options options, LongSupplier clock) {
        this.options = options;
        this.clock = clock;
    }

    public static List<ReleaseCheck> select(List<ReleaseCheck> all, ReleaseTier tier, Collection<String> requestedIds) {
        Set<String> known = new HashSet<>();
        for (ReleaseCheck check : all) {
            if (!known.add(normalize(check.getId()))) {
                throw new IllegalArgumentException("Duplicate release E2E check id: " + check.getId());
            }
        }

        Set<String> requested = new LinkedHashSet<>();
        if (requestedIds != null) {
            for (String id : requestedIds) {
                if (id != null && !id.isBlank()) {
                    requested.add(normalize(id));
                }
            }
        }

        if (requested.isEmpty()) {
            return all.stream()
                    .filter(check -> tier.includes(check.getTier()))
                    .collect(Collectors.toList());
        }

        List<String> unknown = requested.stream()
                .filter(id -> !known.contains(id))
                .collect(Collectors.toList());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown release E2E check id(s) " + unknown + "; known ids: "
                    + all.stream().map(ReleaseCheck::getId).collect(Collectors.joining(", ")));
        }

        return all.stream()
                .filter(check -> requested.contains(normalize(check.getId())))
                .collect(Collectors.toList());
    }

    public List<CheckOutcome> run(List<ReleaseCheck> checks, Consumer<CheckOutcome> listener) {
        List<CheckOutcome> outcomes = new ArrayList<>();
        long deadline = clock.getAsLong() + options.totalTimeoutMs;
        String stopReason = null;

        for (int index = 0; index < checks.size(); index++) {
            ReleaseCheck check = checks.get(index);
            int order = index + 1;

            if (stopReason == null && Thread.currentThread().isInterrupted()) {
                stopReason = "harness interrupted";
            }

            long remaining = deadline - clock.getAsLong();
            if (stopReason == null && remaining <= 0) {
                stopReason = "total timeout of " + options.totalTimeoutMs + "ms exceeded";
            }

            CheckOutcome outcome;
            if (stopReason != null && !check.isAlwaysRun()) {
                outcome = CheckOutcome.notRun(order, check, "Not run: " + stopReason);
            } else {
                long budget = check.isAlwaysRun() ? check.getTimeoutMs() : Math.min(check.getTimeoutMs(), remaining);
                outcome = runOne(order, check, budget);
                if (outcome.status == CheckStatus.FAILED && options.failFast && stopReason == null) {
                    stopReason = "fail-fast after " + check.getId() + " failed";
                }
            }

            outcomes.add(outcome);
            if (listener != null) {
                listener.accept(outcome);
            }
        }
        return outcomes;
    }

    CheckOutcome runOne(int order, ReleaseCheck check, long budgetMs) {
        CheckOutcome outcome = new CheckOutcome();
        outcome.order = order;
        outcome.id = check.getId();
        outcome.description = check.getDescription();
        outcome.tier = check.getTier();
        outcome.timeoutMs = budgetMs;
        outcome.startedAt = Instant.ofEpochMilli(clock.getAsLong()).toString();

        CheckContext context = new CheckContext(check.getId());
        long start = clock.getAsLong();
        ExecutorService executor = Executors.newSingleThreadExecutor(daemonFactory("ReleaseE2E-" + check.getId()));
        Future<?> future = executor.submit(() -> {
            check.getBody().run(context);
            return null;
        });

        boolean harnessInterrupted = false;
        try {
            future.get(Math.max(1L, budgetMs), TimeUnit.MILLISECONDS);
            outcome.status = CheckStatus.PASSED;
        } catch (TimeoutException e) {
            future.cancel(true);
            outcome.status = CheckStatus.FAILED;
            outcome.detail = budgetMs < check.getTimeoutMs()
                    ? "Timed out after " + budgetMs + "ms (remaining total budget; check timeout " + check.getTimeoutMs() + "ms)"
                    : "Timed out after " + budgetMs + "ms";
        } catch (ExecutionException e) {
            classify(outcome, e.getCause());
        } catch (InterruptedException e) {
            future.cancel(true);
            harnessInterrupted = true;
            outcome.status = CheckStatus.FAILED;
            outcome.detail = "Harness interrupted while running check";
        } finally {
            executor.shutdownNow();
        }

        boolean wasInterrupted = Thread.interrupted() || harnessInterrupted;
        try {
            if (!executor.awaitTermination(options.terminationWaitMs, TimeUnit.MILLISECONDS)) {
                outcome.cleanupErrors.add("check thread still running after cancellation");
            }
        } catch (InterruptedException e) {
            wasInterrupted = true;
        }

        runCleanups(context, outcome);
        outcome.details = context.snapshotDetails();
        outcome.durationMs = clock.getAsLong() - start;

        if (!outcome.cleanupErrors.isEmpty()) {
            String cleanupMessage = "Cleanup failed: " + String.join("; ", outcome.cleanupErrors);
            if (outcome.status != CheckStatus.FAILED) {
                outcome.status = CheckStatus.FAILED;
                outcome.detail = outcome.detail == null ? cleanupMessage : outcome.detail + " | " + cleanupMessage;
            } else {
                outcome.detail = outcome.detail + " | " + cleanupMessage;
            }
        }

        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
        return outcome;
    }

    private void runCleanups(CheckContext context, CheckOutcome outcome) {
        List<CheckContext.NamedCleanup> cleanups = context.cleanupsInReverseOrder();
        if (cleanups.isEmpty()) {
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(daemonFactory("ReleaseE2E-cleanup-" + context.getCheckId()));
        try {
            Future<List<String>> future = executor.submit(() -> {
                List<String> errors = new ArrayList<>();
                for (CheckContext.NamedCleanup cleanup : cleanups) {
                    try {
                        cleanup.action.run();
                    } catch (Throwable t) {
                        errors.add(cleanup.name + ": " + describe(t));
                    }
                }
                return errors;
            });
            outcome.cleanupErrors.addAll(future.get(options.cleanupTimeoutMs, TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            outcome.cleanupErrors.add("cleanup timed out after " + options.cleanupTimeoutMs + "ms");
        } catch (ExecutionException e) {
            outcome.cleanupErrors.add(describe(e.getCause()));
        } catch (InterruptedException e) {
            outcome.cleanupErrors.add("cleanup interrupted");
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void classify(CheckOutcome outcome, Throwable cause) {
        if (cause instanceof CheckSkippedException) {
            outcome.status = CheckStatus.SKIPPED;
            outcome.detail = cause.getMessage();
        } else if (cause instanceof CheckFailedException) {
            outcome.status = CheckStatus.FAILED;
            outcome.detail = cause.getMessage();
        } else {
            outcome.status = CheckStatus.FAILED;
            outcome.detail = describe(cause);
        }
    }

    static String describe(Throwable t) {
        if (t == null) {
            return "unknown error";
        }
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
