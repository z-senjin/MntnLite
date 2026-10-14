package net.runelite.client.plugins.microbot.testing.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ClientLogScanner {

    public static final class AllowRule {
        private final Pattern pattern;
        private final String reason;

        public AllowRule(String regex, String reason) {
            this.pattern = Pattern.compile(regex);
            this.reason = reason;
        }

        public boolean matches(String line) {
            return pattern.matcher(line).find();
        }

        public String getReason() {
            return reason;
        }

        public String getRegex() {
            return pattern.pattern();
        }
    }

    public static final List<AllowRule> DEFAULT_ALLOWLIST = List.of(
            new AllowRule("ERROR n\\.r\\.c\\.p\\.m\\.s\\.StateMachineExampleScript - \\[StateMachineExample\\] Error in state [A-Z_]+: Interrupted waiting for client thread",
                    "script-lifecycle stops the example script mid-scan; the script logs the interrupt it is sent at ERROR")
    );

    static final String STARTUP_MARKER = ") starting up, args:";

    private static final Pattern ERROR_LINE = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\S+ \\[[^\\]]*\\] ERROR ");

    private ClientLogScanner() {
    }

    public static Path defaultLogFile() {
        return Paths.get(System.getProperty("user.home"), ".runelite", "logs", "client.log");
    }

    public static List<String> readCurrentRun(Path logFile) throws IOException {
        if (!Files.exists(logFile)) {
            return List.of();
        }
        return currentRun(Files.readAllLines(logFile, StandardCharsets.UTF_8));
    }

    public static List<String> currentRun(List<String> lines) {
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (lines.get(index).contains(STARTUP_MARKER)) {
                return new ArrayList<>(lines.subList(index, lines.size()));
            }
        }
        return new ArrayList<>(lines);
    }

    public static boolean isErrorLine(String line) {
        return ERROR_LINE.matcher(line).find();
    }

    public static List<String> findErrors(List<String> lines, List<AllowRule> allowlist) {
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!isErrorLine(line) || isAllowed(line, allowlist)) {
                continue;
            }
            String next = index + 1 < lines.size() ? lines.get(index + 1) : "";
            if (!next.isEmpty() && !isLogLine(next)) {
                errors.add(line + " | " + next.trim());
            } else {
                errors.add(line);
            }
        }
        return errors;
    }

    static boolean isAllowed(String line, List<AllowRule> allowlist) {
        for (AllowRule rule : allowlist) {
            if (rule.matches(line)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLogLine(String line) {
        return line.length() > 20 && Character.isDigit(line.charAt(0)) && line.charAt(4) == '-' && line.charAt(10) == ' ';
    }
}
