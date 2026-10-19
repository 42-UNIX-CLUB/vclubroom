package pt.vclubroom.shared.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * The demo profile (E16-S03). Each module seeds only its own data, through its own services, in this order;
 * cross-module effects (an approved club's owner, spaces and directory card) come from the usual events, so a
 * seeder that needs them waits for them. Every seeder skips what already exists: running twice changes nothing.
 */
public final class DemoData {

    public static final String PROFILE = "demo";

    public static final int USERS = 10;
    public static final int CLUBS = 20;
    public static final int MEMBERS = 30;
    public static final int POSTS = 50;
    public static final int CHAT = 55;
    public static final int EVENTS = 60;
    public static final int SECOND_ERA = 80;
    public static final int SECOND_ERA_POSTS = 85;
    public static final int ARCHIVE = 90;

    /** The club that shows the lifecycle: era 1, archived, revived into era 2, archived again. */
    public static final String FILM_SOCIETY = "film-society";

    private DemoData() {
    }

    /** Waits for what another module does asynchronously (at most 30 s). */
    public static void awaitUntil(BooleanSupplier condition, String what) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("Demo data: gave up waiting for " + what);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
