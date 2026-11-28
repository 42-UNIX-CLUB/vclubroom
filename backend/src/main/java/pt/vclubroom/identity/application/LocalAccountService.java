package pt.vclubroom.identity.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.AuthProvider;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.domain.UserStatus;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ConflictException;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.shared.web.PageResponse;

/**
 * Admin user management and local passwords (identity.md §7, D-09): local accounts are created by admins only, with
 * a temporary password their owner changes in /settings. A role or status change, or a reset password, ends every
 * session of that user at once (BR-24).
 */
@Service
public class LocalAccountService {

    /** Like 42 logins: lowercase letters, digits, dots, dashes and underscores, 3 to 30 characters. */
    private static final Pattern LOGIN = Pattern.compile("[a-z0-9][a-z0-9._-]{2,29}");
    static final int MIN_PASSWORD = 10;
    static final int MAX_PASSWORD_BYTES = 72;                                  // all bcrypt reads; it refuses more

    public record NewLocalAccount(String login, String displayName, String email, String temporaryPassword, boolean admin) { }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService sessions;
    private final Clock clock;

    LocalAccountService(UserRepository users, PasswordEncoder passwordEncoder, SessionRevocationService sessions, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.clock = clock;
    }

    /** Every account, by login; query matches the start of a login or any part of a name. */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> list(String query, int page, int size) {
        String q = query == null ? "" : query.strip();
        var found = users.searchAll(q, PageRequest.of(page, size, Sort.by("login")));
        return new PageResponse<>(found.map(LocalAccountService::view).getContent(), page, size, found.getTotalElements());
    }

    @Transactional
    public AdminUserView create(NewLocalAccount account) {
        String login = account.login() == null ? "" : account.login().strip().toLowerCase();
        if (!LOGIN.matcher(login).matches()) {
            throw new InvalidRequestException("INVALID_LOGIN",
                "Use 3 to 30 lowercase letters, digits, dots, dashes or underscores, starting with a letter or digit.");
        }
        if (users.existsByLoginIgnoreCase(login)) {                                         // BR-23: 42 logins too
            throw new ConflictException("LOGIN_TAKEN", "Someone already uses the login " + login + ".");
        }
        String email = account.email() == null || account.email().isBlank() ? null : account.email().strip();
        User user = User.createLocal(login, account.displayName().strip(), email, encode(account.temporaryPassword()),
            account.admin() ? PlatformRole.ADMIN : PlatformRole.USER, clock.instant());
        return view(users.save(user));
    }

    /** Role and/or status; never your own, so an admin can't lock themselves out by mistake. */
    @Transactional
    public AdminUserView update(Actor admin, UUID userId, PlatformRole role, UserStatus status) {
        if (admin.userId().equals(userId)) {
            throw new BusinessRuleException("CANNOT_CHANGE_SELF", "Ask another admin to change your own role or status.");
        }
        User user = load(userId);
        boolean changed = false;
        if (role != null && role != user.platformRole()) {
            user.changeRole(role, clock.instant());
            changed = true;
        }
        if (status != null && status != user.status()) {
            if (status == UserStatus.DISABLED) user.disable(clock.instant());
            else user.enable(clock.instant());
            changed = true;
        }
        if (changed) {
            sessions.revokeAll(userId);                     // the session holds the old role: log in again (BR-24)
        }
        return view(user);
    }

    /** A new temporary password for a local account; its sessions end. */
    @Transactional
    public void resetPassword(UUID userId, String temporaryPassword) {
        User user = requireLocal(load(userId));
        user.changePasswordHash(encode(temporaryPassword), clock.instant());
        sessions.revokeAll(userId);
    }

    /** The owner of a local account; their other browsers are logged out, this one stays. */
    @Transactional
    public void changeOwnPassword(UUID userId, String currentPassword, String newPassword, String keptSessionId) {
        User user = requireLocal(load(userId));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new BusinessRuleException("WRONG_PASSWORD", "Your current password is not right.");
        }
        user.changePasswordHash(encode(newPassword), clock.instant());
        sessions.revokeAllExcept(userId, keptSessionId);
    }

    private User load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }

    private static User requireLocal(User user) {
        if (user.provider() != AuthProvider.LOCAL) {
            throw new BusinessRuleException("NOT_LOCAL_ACCOUNT", "This account logs in with 42 and has no password here.");
        }
        return user;
    }

    private String encode(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new InvalidRequestException("WEAK_PASSWORD", "Passwords need at least " + MIN_PASSWORD + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidRequestException("PASSWORD_TOO_LONG",
                "Passwords can be at most " + MAX_PASSWORD_BYTES + " bytes: about 72 letters, fewer with accents or emoji.");
        }
        return passwordEncoder.encode(password);
    }

    private static AdminUserView view(User u) {
        return new AdminUserView(u.id(), u.login(), u.displayName(), u.email(), u.provider(), u.platformRole(), u.status(),
            u.createdAt(), u.lastLoginAt());
    }
}
