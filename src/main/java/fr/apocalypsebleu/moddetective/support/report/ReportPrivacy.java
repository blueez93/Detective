package fr.apocalypsebleu.moddetective.support.report;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Defense in depth for metadata supplied by mods or incident files. */
public final class ReportPrivacy {
    private static final Pattern SENSITIVE_LABEL = Pattern.compile(
            "(?i)(access[_-]?token|session[_-]?id|user(?:name|\\.home)|host[_-]?name|"
                    + "server[_-]?(?:address|ip)|authorization|bearer|password|secret)");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(?:access[_-]?token|session[_-]?id|user(?:name|\\.home)|host[_-]?name|"
                    + "server[_-]?(?:address|ip)|authorization|bearer|password|secret)"
                    + "\\s*[:=]\\s*[^,;\\s]+");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(?![0-9])");
    private static final Pattern IPV6 = Pattern.compile(
            "(?i)(?<![0-9a-f:])(?=[0-9a-f:]*:[0-9a-f:]*:)"
                    + "[0-9a-f]{0,4}(?::[0-9a-f]{0,4}){2,7}(?![0-9a-f:])");
    private static final Pattern MAC_ADDRESS = Pattern.compile(
            "(?i)(?<![0-9a-f])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f])");
    private static final Pattern UUID = Pattern.compile(
            "(?i)(?<![0-9a-f])[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![0-9a-f])");
    private static final Pattern WINDOWS_USER_PATH = Pattern.compile(
            "(?i)[a-z]:[\\\\/]+users[\\\\/]+[^\\\\/\\s]+(?:[\\\\/][^\\s]*)?");
    private static final Pattern UNIX_HOME_PATH = Pattern.compile(
            "(?i)(?:/home/[^/\\s]+|/users/[^/\\s]+)(?:/[^\\s]*)?");
    private static final int MAXIMUM_TEXT_LENGTH = 512;
    private static final Set<String> LOCAL_USER_NAMES = localUserNames();

    private ReportPrivacy() {}

    public static String metadata(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').strip();
        sanitized = SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("[redacted]");
        sanitized = WINDOWS_USER_PATH.matcher(sanitized).replaceAll("[redacted-path]");
        sanitized = UNIX_HOME_PATH.matcher(sanitized).replaceAll("[redacted-path]");
        sanitized = IPV4.matcher(sanitized).replaceAll("[redacted-ip]");
        sanitized = IPV6.matcher(sanitized).replaceAll("[redacted-ip]");
        sanitized = MAC_ADDRESS.matcher(sanitized).replaceAll("[redacted-mac]");
        sanitized = UUID.matcher(sanitized).replaceAll("[redacted-uuid]");
        sanitized = SENSITIVE_LABEL.matcher(sanitized).replaceAll("[redacted]");
        for (String userName : LOCAL_USER_NAMES) {
            sanitized = Pattern.compile("(?i)(?<![\\p{L}\\p{N}_-])" + Pattern.quote(userName)
                            + "(?![\\p{L}\\p{N}_-])")
                    .matcher(sanitized).replaceAll("[redacted-user]");
        }
        return sanitized.length() <= MAXIMUM_TEXT_LENGTH
                ? sanitized
                : sanitized.substring(0, MAXIMUM_TEXT_LENGTH);
    }

    public static String fileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        try {
            Path name = Path.of(value).getFileName();
            return metadata(name == null ? "unknown" : name.toString());
        } catch (RuntimeException ignored) {
            String normalized = value.replace('\\', '/');
            int separator = normalized.lastIndexOf('/');
            return metadata(separator >= 0 ? normalized.substring(separator + 1) : normalized);
        }
    }

    private static Set<String> localUserNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        addUserName(names, System.getProperty("user.name"));
        try {
            Path homeName = Path.of(System.getProperty("user.home", "")).getFileName();
            addUserName(names, homeName == null ? null : homeName.toString());
        } catch (RuntimeException ignored) {
            // A malformed home property must not affect report generation.
        }
        return Set.copyOf(names);
    }

    private static void addUserName(Set<String> names, String value) {
        if (value != null && value.length() >= 2) {
            names.add(value);
        }
    }
}
