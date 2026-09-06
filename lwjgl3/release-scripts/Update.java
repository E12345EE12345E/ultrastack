import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces the local UltraStack-*.jar with the JAR attached to the newest GitHub Release.
 * Identity is the asset filename, not a parsed version number.
 */
public class Update {
    static final String API =
            "https://api.github.com/repos/E12345EE12345E/ultrastack/releases?per_page=1";
    static final String UA = "UltraStack-updater";

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            System.err.println("Update failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void run() throws Exception {
        Path dir = Path.of(".").toAbsolutePath().normalize();
        List<Path> local = findLocalJars(dir);

        System.out.println("Checking latest GitHub release...");
        JarAsset remote = findRemoteJar(httpGet(API));
        for (Path p : local) {
            if (p.getFileName().toString().equals(remote.name)) {
                System.out.println("Already up to date: " + remote.name);
                return;
            }
        }

        String from = local.isEmpty() ? "(none)" : (
                local.size() == 1 ? local.get(0).getFileName().toString() : local.size() + " local jars"
        );
        System.out.println("Updating " + from + " -> " + remote.name);

        Path part = dir.resolve(remote.name + ".part");
        Files.deleteIfExists(part);
        httpDownload(remote.url, part);
        if (!Files.isRegularFile(part) || Files.size(part) < 1_000_000L) {
            Files.deleteIfExists(part);
            throw new IllegalStateException("Downloaded file is too small; aborting");
        }

        for (Path p : local) {
            Files.delete(p);
        }
        Files.move(part, dir.resolve(remote.name), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Updated to " + remote.name);
        System.out.println("Close this window and use start to launch.");
    }

    static List<Path> findLocalJars(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "UltraStack-*.jar")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    static JarAsset findRemoteJar(String json) {
        Matcher names = Pattern.compile("\"name\"\\s*:\\s*\"(UltraStack-[^\"]+\\.jar)\"").matcher(json);
        Set<String> found = new LinkedHashSet<>();
        while (names.find()) {
            String n = names.group(1);
            if (isClientJarName(n)) {
                found.add(n);
            }
        }
        if (found.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one UltraStack-*.jar on the latest release, found " + found);
        }
        String name = found.iterator().next();
        Matcher urls = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"(https:[^\"]+)\"").matcher(json);
        while (urls.find()) {
            String url = urls.group(1);
            if (url.endsWith("/" + name)) {
                return new JarAsset(name, url);
            }
        }
        throw new IllegalStateException("No download URL for " + name);
    }

    static boolean isClientJarName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("-linux-") || lower.contains("-mac-")
                || lower.contains("-windows-") || lower.contains("-win-")) {
            return false;
        }
        return name.startsWith("UltraStack-") && name.endsWith(".jar");
    }

    static String httpGet(String url) throws IOException {
        HttpURLConnection conn = open(url);
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    static void httpDownload(String url, Path dest) throws IOException {
        HttpURLConnection conn = open(url);
        try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(dest)) {
            in.transferTo(out);
        } finally {
            conn.disconnect();
        }
    }

    static HttpURLConnection open(String url) throws IOException {
        URI uri = URI.create(url);
        for (int i = 0; i < 10; i++) {
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(300_000);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isBlank()) {
                    throw new IOException("Redirect without Location from " + uri);
                }
                uri = uri.resolve(location);
                continue;
            }
            if (code != 200) {
                conn.disconnect();
                throw new IOException("HTTP " + code + " for " + url);
            }
            return conn;
        }
        throw new IOException("Too many redirects for " + url);
    }

    static final class JarAsset {
        final String name;
        final String url;

        JarAsset(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}
