package pt.vclubroom.space.api;

import java.util.UUID;
import pt.vclubroom.shared.error.ForbiddenException;

/** Answer to "what may this actor do in this space?". */
public record SpaceAccess(UUID spaceId, UUID clubId, int currentEra,
                          boolean canRead, boolean canWrite, boolean canModerate) {

    public SpaceAccess requireRead() {
        if (!canRead) throw new ForbiddenException("SPACE_READ_DENIED", "You cannot read this space.");
        return this;
    }

    public SpaceAccess requireWrite() {
        if (!canWrite) throw new ForbiddenException("SPACE_WRITE_DENIED", "You cannot post in this space.");
        return this;
    }
}
