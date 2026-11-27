/**
 * Operations (E15-S04): what an admin needs to see that the platform is keeping up, and what everyone may see of it, the
 * public status (E19-S02). It reads Spring Modulith's event publication table and the health indicators, and asks
 * notification for its email outbox and support for its tracker sync (E17-S04); nothing depends on it.
 */
@ApplicationModule(displayName = "Ops", allowedDependencies = {"shared", "identity::api", "media::api", "notification::api", "support::api"})
package pt.vclubroom.ops;

import org.springframework.modulith.ApplicationModule;
