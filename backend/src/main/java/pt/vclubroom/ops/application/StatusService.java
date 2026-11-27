package pt.vclubroom.ops.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;
import pt.vclubroom.media.api.StorageProbe;
import pt.vclubroom.notification.api.EmailOutbox;
import pt.vclubroom.support.api.TrackerBacklog;
import pt.vclubroom.support.api.TrackerQueue;

/**
 * The public status (E19-S02), from the health indicators (database, Redis), a storage probe and the outboxes (emails
 * being retried, a tracker out of reach for 10 minutes). The platform is down without its database or Redis, degraded
 * when anything else isn't operational. Checked at most every 10 s, whatever the number of visitors.
 */
@Service
public class StatusService {

    private static final Duration FRESH = Duration.ofSeconds(10);
    private static final Duration TRACKER_LATE = Duration.ofMinutes(10);

    private final HealthEndpoint health;
    private final StorageProbe storage;
    private final EmailOutbox emails;
    private final TrackerQueue tickets;
    private final Clock clock;
    private volatile PublicStatus last;

    StatusService(HealthEndpoint health, StorageProbe storage, EmailOutbox emails, TrackerQueue tickets, Clock clock) {
        this.health = health;
        this.storage = storage;
        this.emails = emails;
        this.tickets = tickets;
        this.clock = clock;
    }

    public PublicStatus status() {
        Instant now = clock.instant();
        PublicStatus cached = last;
        if (cached != null && cached.checkedAt().plus(FRESH).isAfter(now)) {
            return cached;
        }
        List<ServiceStatus> services = List.of(
            new ServiceStatus("app", "App", ServiceState.OPERATIONAL),             // it is answering
            new ServiceStatus("database", "Database", indicator("db")),
            new ServiceStatus("sessions", "Logins and live updates", indicator("redis")),
            new ServiceStatus("storage", "Images and files", storage.reachable() ? ServiceState.OPERATIONAL : ServiceState.DOWN),
            new ServiceStatus("email", "Email", emails.backlog().retrying() > 0 ? ServiceState.DEGRADED : ServiceState.OPERATIONAL),
            new ServiceStatus("tickets", "Help and feedback", trackerLate(tickets.backlog(), now) ? ServiceState.DEGRADED : ServiceState.OPERATIONAL));
        boolean essentialDown = services.stream()
            .anyMatch(c -> (c.id().equals("database") || c.id().equals("sessions")) && c.status() == ServiceState.DOWN);
        boolean allFine = services.stream().allMatch(c -> c.status() == ServiceState.OPERATIONAL);
        PublicStatus status = new PublicStatus(essentialDown ? ServiceState.DOWN : allFine ? ServiceState.OPERATIONAL : ServiceState.DEGRADED,
            services, now);
        last = status;
        return status;
    }

    private ServiceState indicator(String name) {
        HealthComponent component = health.healthForPath(name);
        if (component == null) {
            return ServiceState.DEGRADED;                                        // unknown: not checked
        }
        Status status = component.getStatus();
        return Status.UP.equals(status) ? ServiceState.OPERATIONAL
            : Status.DOWN.equals(status) || Status.OUT_OF_SERVICE.equals(status) ? ServiceState.DOWN : ServiceState.DEGRADED;
    }

    private static boolean trackerLate(TrackerBacklog backlog, Instant now) {
        Instant late = now.minus(TRACKER_LATE);
        return (backlog.oldestUnsent() != null && backlog.oldestUnsent().isBefore(late))
            || (backlog.lastRead() != null && backlog.lastRead().isBefore(late));
    }
}
