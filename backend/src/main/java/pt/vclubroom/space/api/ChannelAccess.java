package pt.vclubroom.space.api;

import java.util.UUID;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ForbiddenException;

/** Answer to "what may this actor do in this channel?", with routing info for content modules. */
public record ChannelAccess(UUID channelId, ChannelType type, UUID spaceId, UUID clubId, int currentEra,
                            boolean canRead, boolean canWrite, boolean canModerate) {

    public ChannelAccess requireType(ChannelType expected) {
        if (type != expected) {
            throw new BusinessRuleException("WRONG_CHANNEL_TYPE", "This channel is a " + type + " channel.");
        }
        return this;
    }

    public ChannelAccess requireRead() {
        if (!canRead) throw new ForbiddenException("CHANNEL_READ_DENIED", "You cannot read this channel.");
        return this;
    }

    public ChannelAccess requireWrite() {
        if (!canWrite) throw new ForbiddenException("CHANNEL_WRITE_DENIED", "You cannot write in this channel.");
        return this;
    }

    /** BR-15: content from previous eras is read-only. */
    public boolean isCurrentEra(int contentEra) {
        return contentEra == currentEra;
    }
}
