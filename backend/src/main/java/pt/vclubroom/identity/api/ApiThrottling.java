package pt.vclubroom.identity.api;

/** How often the API's rate limits refused a request (E20-S03), for the admin's health card. */
public interface ApiThrottling {

    /** Requests refused in the last 60 minutes, by any rate limit. */
    long refusedLastHour();
}
