package pt.vclubroom.support.api;

/** Read-only view of the tracker sync, for the operations card (E17-S04). */
public interface TrackerQueue {

    TrackerBacklog backlog();
}
