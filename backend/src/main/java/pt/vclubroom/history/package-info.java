@ApplicationModule(
    displayName = "History",
    allowedDependencies = {"shared", "club::api", "club::events", "membership::events", "identity::api"}
)
package pt.vclubroom.history;

import org.springframework.modulith.ApplicationModule;
