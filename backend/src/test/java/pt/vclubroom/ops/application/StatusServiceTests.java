package pt.vclubroom.ops.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthEndpoint;
import pt.vclubroom.media.api.StorageProbe;
import pt.vclubroom.notification.api.EmailBacklog;
import pt.vclubroom.notification.api.EmailOutbox;
import pt.vclubroom.support.api.TrackerBacklog;
import pt.vclubroom.support.api.TrackerQueue;

/** The status page is open to anyone: however many visitors arrive when the cache expires, one of them checks. */
class StatusServiceTests {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-27T12:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private final AtomicInteger probes = new AtomicInteger();
    private final AtomicReference<CountDownLatch> gate = new AtomicReference<>(new CountDownLatch(0));

    /** Storage down: each probe waits for its timeout (here: until the test opens the gate). */
    private final StorageProbe slowStorage = () -> {
        probes.incrementAndGet();
        try {
            gate.get().await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    };

    private final EmailOutbox emails = () -> new EmailBacklog(0, 0, 0, 0, null);
    private final TrackerQueue tickets = () -> new TrackerBacklog(0, 0, null, now.get(), null, "");

    @Test
    void visitorsArrivingTogetherShareOneCheck() throws Exception {
        StatusService status = new StatusService(mock(HealthEndpoint.class), slowStorage, emails, tickets, clock);
        PublicStatus first = status.status();                               // checked, and cached
        gate.set(new CountDownLatch(1));                                    // from now on the probe hangs
        now.set(now.get().plus(Duration.ofSeconds(11)));                     // and the cache has expired
        probes.set(0);

        ExecutorService visitors = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<PublicStatus>> answers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            answers.add(visitors.submit(() -> {
                go.await();
                return status.status();
            }));
        }
        go.countDown();
        Thread.sleep(500);                                                  // everyone has arrived
        int probing = probes.get();
        gate.get().countDown();
        for (Future<PublicStatus> answer : answers) {
            assertThat(answer.get(10, TimeUnit.SECONDS)).isNotNull();
        }
        visitors.shutdown();

        assertThat(probing).as("storage probes running at once").isEqualTo(1);
        assertThat(status.status().checkedAt()).isAfter(first.checkedAt());  // the one check refreshed the cache
    }
}
