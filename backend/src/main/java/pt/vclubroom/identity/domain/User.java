package pt.vclubroom.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import pt.vclubroom.identity.api.PlatformRole;

@Entity
@Table(name = "users", schema = "identity")
public class User {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider provider;

    @Column(name = "ft_id")
    private Long ftId;

    @Column(nullable = false)
    private String login;

    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false)
    private PlatformRole platformRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "ft_pool_year")
    private Integer ftPoolYear;

    @Column(name = "ft_pool_month")
    private String ftPoolMonth;

    @Column(name = "ft_alumni", nullable = false)
    private boolean ftAlumni;

    @Column(name = "ft_staff", nullable = false)
    private boolean ftStaff;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    private Long version;   // null until the first save, so Spring Data persists (not merges) new users

    protected User() {
        // JPA
    }

    public static User registerFromFortyTwo(FortyTwoProfile p, Instant now, boolean bootstrapAdmin) {
        User u = new User();
        u.id = UUID.randomUUID();
        u.provider = AuthProvider.FORTY_TWO;
        u.ftId = p.ftId();
        u.login = p.login();
        u.platformRole = bootstrapAdmin ? PlatformRole.ADMIN : PlatformRole.USER;
        u.status = UserStatus.ACTIVE;
        u.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        u.syncFromFortyTwo(p, now);
        return u;
    }

    public static User createLocal(String login, String displayName, String email, String passwordHash,
                                   PlatformRole role, Instant now) {
        User u = new User();
        u.id = UUID.randomUUID();
        u.provider = AuthProvider.LOCAL;
        u.login = login;
        u.displayName = displayName;
        u.email = email;
        u.passwordHash = passwordHash;
        u.platformRole = role;
        u.status = UserStatus.ACTIVE;
        u.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        u.updatedAt = u.createdAt;
        return u;
    }

    public void syncFromFortyTwo(FortyTwoProfile p, Instant now) {
        this.email = p.email();
        this.displayName = p.displayName();
        this.avatarUrl = p.avatarUrl();
        this.ftPoolYear = p.poolYear();
        this.ftPoolMonth = p.poolMonth();
        this.ftAlumni = p.alumni();
        this.ftStaff = p.staff();
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void changeRole(PlatformRole role, Instant now) {
        this.platformRole = role;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void disable(Instant now) {
        this.status = UserStatus.DISABLED;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void enable(Instant now) {
        this.status = UserStatus.ACTIVE;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void changePasswordHash(String newHash, Instant now) {
        if (provider != AuthProvider.LOCAL) {
            throw new IllegalStateException("Only local accounts have passwords");
        }
        this.passwordHash = newHash;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isAdmin() { return platformRole == PlatformRole.ADMIN; }
    public boolean isActive() { return status == UserStatus.ACTIVE; }
    public UUID id() { return id; }
    public String login() { return login; }
    public String email() { return email; }
    public String displayName() { return displayName; }
    public String avatarUrl() { return avatarUrl; }
    public String passwordHash() { return passwordHash; }
    public AuthProvider provider() { return provider; }
    public PlatformRole platformRole() { return platformRole; }
    public UserStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant lastLoginAt() { return lastLoginAt; }
}
