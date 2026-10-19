package pt.vclubroom.identity.application;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.api.UserContact;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.identity.web.dto.MeResponse;
import pt.vclubroom.shared.error.NotFoundException;

/**
 * Other modules use it through the UserDirectory interface (identity.api). The MeResponse methods
 * serve only identity's own controllers; other modules never see MeResponse.
 */
@Service
@Transactional(readOnly = true)
public class UserDirectoryService implements UserDirectory {

    private final UserRepository users;
    private final Clock clock;

    public UserDirectoryService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public Optional<UserSummary> findById(UUID userId) {
        return users.findById(userId).map(UserDirectoryService::summary);
    }

    @Override
    public Optional<UserSummary> findByLogin(String login) {
        return users.findByLoginIgnoreCase(login).map(UserDirectoryService::summary);
    }

    @Override
    public Map<UUID, UserSummary> findByIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(userIds).stream()
            .map(UserDirectoryService::summary)
            .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    @Override
    public Optional<UserContact> contactOf(UUID userId) {
        return users.findById(userId).map(u -> new UserContact(u.id(), u.email(), u.displayName(), u.isActive()));
    }

    @Override
    public List<UUID> adminIds() {
        return users.findIdsByRole(PlatformRole.ADMIN);
    }

    /**
     * Active users whose login starts with, or whose display name contains, the query (ownership invitations,
     * groups). Fewer than 2 characters finds nobody: a one-letter search would list half the campus.
     */
    public List<UserSummary> search(String query, int limit) {
        String q = query == null ? "" : query.strip();
        if (q.length() < 2) {
            return List.of();
        }
        return users.search(q, Limit.of(Math.clamp(limit, 1, 20))).stream().map(UserDirectoryService::summary).toList();
    }

    public MeResponse requireMe(UUID userId) {
        return users.findById(userId).map(UserDirectoryService::me)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }

    public MeResponse requireMeByLogin(String login) {
        return users.findLocalByLogin(login).map(UserDirectoryService::me)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }

    @Transactional
    public void recordLogin(UUID userId) {
        users.findById(userId).ifPresent(u -> u.recordLogin(clock.instant()));
    }

    private static UserSummary summary(User u) {
        return new UserSummary(u.id(), u.login(), u.displayName(), u.avatarUrl());
    }

    private static MeResponse me(User u) {
        return new MeResponse(u.id(), u.login(), u.displayName(), u.email(), u.avatarUrl(),
            u.provider().name(), u.isAdmin());
    }
}
