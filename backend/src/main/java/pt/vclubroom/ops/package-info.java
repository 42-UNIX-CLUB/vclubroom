/**
 * Operations (E15-S04): what an admin needs to see that the platform is keeping up. It reads Spring Modulith's
 * event publication table and asks notification for its email outbox and support for its tracker sync (E17-S04);
 * nothing depends on it.
 */
@ApplicationModule(displayName = "Ops", allowedDependencies = {"shared", "notification::api", "support::api"})
package pt.vclubroom.ops;

import org.springframework.modulith.ApplicationModule;
