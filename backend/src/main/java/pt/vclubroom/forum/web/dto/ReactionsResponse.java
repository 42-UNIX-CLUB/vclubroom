package pt.vclubroom.forum.web.dto;

import java.util.Map;
import java.util.Set;
import pt.vclubroom.forum.application.ReactionService.ReactionState;

/** A target's reactions after a change: the SPA replaces its optimistic guess with this. */
public record ReactionsResponse(Map<String, Long> counts, Set<String> mine) {

    public static ReactionsResponse from(ReactionState state) {
        return new ReactionsResponse(state.counts(), state.mine());
    }
}
