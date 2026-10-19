package pt.vclubroom.forum.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.vclubroom.forum.domain.Reaction;
import pt.vclubroom.forum.domain.ReactionTarget;
import pt.vclubroom.forum.persistence.ReactionRepository;

/** Counts and "mine" for many targets in two queries, whatever the page size (forum.md §8.2). */
@Component
class ReactionSummary {

    /** counts: code → number of people; mine: the codes the viewer used. */
    record Reactions(Map<String, Long> counts, Set<String> mine) {
        static final Reactions NONE = new Reactions(Map.of(), Set.of());
    }

    private final ReactionRepository reactions;

    ReactionSummary(ReactionRepository reactions) {
        this.reactions = reactions;
    }

    Map<UUID, Reactions> of(ReactionTarget target, Collection<UUID> targetIds, UUID viewerId) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, Long>> counts = new HashMap<>();
        reactions.countByTargets(target, targetIds).forEach(row ->
            counts.computeIfAbsent(row.getTargetId(), id -> new HashMap<>()).put(row.getEmoji(), row.getTotal()));
        Map<UUID, Set<String>> mine = new HashMap<>();
        for (Reaction r : reactions.mineByTargets(target, targetIds, viewerId)) {
            mine.computeIfAbsent(r.targetId(), id -> new HashSet<>()).add(r.emoji());
        }
        Map<UUID, Reactions> result = new HashMap<>();
        for (UUID id : targetIds) {
            result.put(id, new Reactions(counts.getOrDefault(id, Map.of()), mine.getOrDefault(id, Set.of())));
        }
        return result;
    }

    Reactions of(ReactionTarget target, UUID targetId, UUID viewerId) {
        return of(target, Set.of(targetId), viewerId).getOrDefault(targetId, Reactions.NONE);
    }
}
