package org.apache.camel.kit.knowledge.mcp;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for knowledge index resolution. The index is distributed as a GitHub Release asset described by a small
 * JSON manifest; the MCP server downloads it once into a local cache and opens it in place.
 *
 * Resolution order: {@code path} (local dir, no network) → {@code url} manifest + local cache → classpath fallback.
 */
@ConfigMapping(prefix = "knowledge.index")
public interface IndexResolverConfig {

    /** Direct path to a Lucene index directory — used as-is, no download. Intended for dev, tests, air-gapped use. */
    Optional<String> path();

    /** Explicit manifest URL (https:// or file://), including existing standalone-repository manifests. */
    Optional<String> url();

    /** GitHub release discovery endpoint, used when no explicit manifest URL is configured. */
    @WithDefault("https://api.github.com/repos/apache/camel-kit/releases")
    String releasesUrl();

    /** Exact index version. When absent, discover the newest published version in the compatible format channel. */
    Optional<String> version();

    /** Local cache directory holding downloaded index versions. */
    @WithDefault("${user.home}/.camel-kit/knowledge-index")
    String cacheDir();
}
