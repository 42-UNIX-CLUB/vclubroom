package pt.vclubroom.media.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pt.vclubroom.media.application.MediaUploadService;

/** headers: what the PUT must send exactly (they are part of the signature); browsers set Host themselves. */
public record UploadSlotResponse(UUID mediaId, String uploadUrl, String method,
                                 Map<String, List<String>> headers, Instant expiresAt) {

    public static UploadSlotResponse from(MediaUploadService.UploadSlot slot) {
        return new UploadSlotResponse(slot.mediaId(), slot.upload().url(), "PUT", slot.upload().headers(),
            slot.upload().expiresAt());
    }
}
