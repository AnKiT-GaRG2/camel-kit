package org.apache.camel.kit.knowledge.mcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class IndexDiscoveryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir
    Path tmp;
    private HttpServer server;
    private ArrayNode releases;
    private ArrayNode secondPage;
    private ObjectNode pinned;
    private String requestedPath;
    private String pin;
    private int status = 200;
    private String next;

    @BeforeEach
    void start() throws IOException {
        releases = mapper.createArrayNode();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/releases", exchange -> {
            requestedPath = exchange.getRequestURI().getPath();
            var body = requestedPath.contains("/tags/") ? pinned
                    : "page=2".equals(exchange.getRequestURI().getQuery()) ? secondPage : releases;
            if (next != null && !"page=2".equals(exchange.getRequestURI().getQuery())) {
                exchange.getResponseHeaders().add("Link", "<" + next + ">; rel=\"next\"");
            }
            byte[] bytes = mapper.writeValueAsBytes(body);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/releases";
    }

    private IndexDownloader downloader() {
        return new IndexDownloader(new IndexResolverConfig() {
            public Optional<String> path() {
                return Optional.empty();
            }

            public Optional<String> url() {
                return Optional.empty();
            }

            public Optional<String> version() {
                return Optional.ofNullable(pin);
            }

            public String releasesUrl() {
                return endpoint();
            }

            public String cacheDir() {
                return tmp.resolve("cache").toString();
            }
        }, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private ObjectNode release(String version) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve(version));
        Path zip = dir.resolve("knowledge-index.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("knowledge-index/segments_1"));
            out.write(version.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(zip)));
        Files.writeString(dir.resolve("index.json"), mapper.createObjectNode().put("version", version)
                .put("sha256", sha).put("asset", "knowledge-index.zip").put("formatVersion", 1)
                .put("embeddingDimensions", 384).put("embeddingModel", "granite-embedding-small-english-r2-q8")
                .toString());
        ObjectNode result = mapper.createObjectNode().put("tag_name", "knowledge-index-v1-" + version)
                .put("draft", false).put("prerelease", false);
        result.putArray("assets").addObject().put("name", "index.json")
                .put("browser_download_url", dir.resolve("index.json").toUri().toString());
        return result;
    }

    @Test
    void ignoresProductReleasesAndOtherFormatsAndFindsNewestAcrossPages() throws Exception {
        releases.add(release("2026.10.01-120000"));
        releases.add(release("2026.10.27-120000").put("tag_name", "camel-kit-1.0.0"));
        releases.add(release("2026.10.28-120000").put("tag_name", "knowledge-index-v2-2026.10.28-120000"));
        releases.add(release("2026.10.29-120000").put("draft", true));
        releases.add(release("2026.10.30-120000").put("prerelease", true));
        secondPage = mapper.createArrayNode().add(release("2026.10.26-120000"));
        next = endpoint() + "?page=2";
        assertEquals("2026.10.26-120000", downloader().resolve().dir().getFileName().toString());
    }

    @Test
    void pinUsesExactTagAndOfflineFallbackUsesOnlyThatVersion() throws Exception {
        releases.add(release("2026.10.26-120000"));
        downloader().resolve();
        pin = "2026.10.01-120000";
        pinned = release(pin);
        assertEquals(pin, downloader().resolve().dir().getFileName().toString());
        assertEquals("/releases/tags/knowledge-index-v1-" + pin, requestedPath);
        status = 503;
        assertEquals(pin, downloader().resolve().dir().getFileName().toString());
        pin = "2026.09.01-120000";
        assertThrows(IOException.class, () -> downloader().resolve());
    }

    @Test
    void invalidPinFailsBeforeNetworkAccess() {
        pin = "../escape";
        assertThrows(IOException.class, () -> downloader().resolve());
        assertNull(requestedPath);
    }

    @Test
    void tagAndManifestMustAgree() throws Exception {
        releases.add(release("2026.10.01-120000").put("tag_name", "knowledge-index-v1-2026.10.26-120000"));
        assertThrows(IOException.class, () -> downloader().resolve());
        assertFalse(Files.exists(tmp.resolve("cache/current")));
    }

    @Test
    void malformedCompatibilityMetadataNeverActivatesAnUpdate() throws Exception {
        releases.add(release("2026.10.01-120000"));
        Path original = downloader().resolve().dir();
        releases.add(release("2026.10.26-120000"));
        Path manifest = tmp.resolve("2026.10.26-120000/index.json");
        ObjectNode document = (ObjectNode) mapper.readTree(Files.readString(manifest));
        document.put("formatVersion", "one");
        Files.writeString(manifest, document.toString());
        assertEquals(original, downloader().resolve().dir());
        document.put("formatVersion", 1).put("embeddingDimensions", 768);
        Files.writeString(manifest, document.toString());
        assertEquals(original, downloader().resolve().dir());
        document.put("embeddingDimensions", 384).remove("embeddingModel");
        Files.writeString(manifest, document.toString());
        assertEquals(original, downloader().resolve().dir());
        assertFalse(Files.exists(tmp.resolve("cache/2026.10.26-120000")));
    }

    @Test
    void discoveryFailureKeepsWorkingCache() throws Exception {
        releases.add(release("2026.10.01-120000"));
        Path original = downloader().resolve().dir();
        status = 503;
        assertEquals(original, downloader().resolve().dir());
    }

    @Test
    void paginationCannotLeaveConfiguredEndpoint() throws Exception {
        releases.add(release("2026.10.01-120000"));
        next = "http://127.0.0.1:1/unexpected";
        IOException failure = assertThrows(IOException.class, () -> downloader().resolve());
        assertTrue(failure.getCause().getMessage().contains("pagination changed"));
    }
}
