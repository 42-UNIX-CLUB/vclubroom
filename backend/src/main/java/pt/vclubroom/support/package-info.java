/**
 * Support (E17): tickets people send from the app (bugs, suggestions, questions…). Each ticket becomes an issue in the
 * team's tracker (Forgejo) through the TicketTracker port; the app keeps its own copy, so a tracker outage only delays.
 */
@ApplicationModule(displayName = "Support", allowedDependencies = {"shared", "identity::api"})
package pt.vclubroom.support;

import org.springframework.modulith.ApplicationModule;
