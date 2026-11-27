package pt.vclubroom.support.domain;

/**
 * A development issue, as a ticket shows it (E18-S03). issue: its number in the development repository; milestone:
 * its release ("v1.2.0") or null; released: that release is out.
 */
public record LinkedWork(int issue, String title, WorkState state, String milestone, boolean released) {
}
