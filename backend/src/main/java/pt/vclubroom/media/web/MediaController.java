package pt.vclubroom.media.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.media.application.MediaUploadService;
import pt.vclubroom.media.web.dto.CreateUploadRequest;
import pt.vclubroom.media.web.dto.MediaResponse;
import pt.vclubroom.media.web.dto.UploadSlotResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** Upload slots and their completion. The upload itself goes to the media buckets (SeaweedFS, through the proxy). */
@RestController
@RequestMapping("/api/media")
class MediaController {

    private final MediaUploadService uploads;

    MediaController(MediaUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    UploadSlotResponse createUpload(@AuthenticationPrincipal AuthenticatedUser me,
                                    @Valid @RequestBody CreateUploadRequest request) {
        return UploadSlotResponse.from(uploads.createSlot(me.actor(), request.purpose(), request.fileName(),
            request.contentType(), request.sizeBytes()));
    }

    @PostMapping("/{mediaId}/complete")
    MediaResponse complete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("mediaId") UUID mediaId) {
        return MediaResponse.from(uploads.complete(me.actor(), mediaId));
    }
}
