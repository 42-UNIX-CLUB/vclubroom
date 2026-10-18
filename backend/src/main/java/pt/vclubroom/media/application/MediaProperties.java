package pt.vclubroom.media.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import pt.vclubroom.media.api.MediaKind;

/** app.media.* in application.yml: bucket names, URL lifetimes and the size/type limits per kind. */
@ConfigurationProperties("app.media")
public record MediaProperties(String publicBucket,
                              String privateBucket,
                              Duration uploadUrlTtl,
                              Duration downloadUrlTtl,
                              Duration orphanTtl,
                              Map<MediaKind, Limit> limits) {

    public record Limit(long maxBytes, List<String> contentTypes) { }
}
