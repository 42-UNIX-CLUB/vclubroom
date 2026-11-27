package pt.vclubroom.ops.application;

import java.time.Instant;
import java.util.List;

/** GET /api/public/status (E19-S02): the platform as a whole, each service, and when it was checked. No internal details. */
public record PublicStatus(ServiceState status, List<ServiceStatus> services, Instant checkedAt) {
}
