package com.ccmmd.chess.platform.events;

/**
 * The published event contract. These strings are the routing keys on the {@code chess.events}
 * topic exchange and the discriminator consumers switch on — treat them as an API and only ever
 * add, never rename.
 */
public final class EventType {

    public static final String USER_REGISTERED = "user.registered";

    public static final String GAME_CREATED = "game.created";
    public static final String GAME_MOVE_PLAYED = "game.move.played";
    public static final String GAME_ENDED = "game.ended";

    public static final String ENGINE_MOVE_REQUESTED = "engine.move.requested";
    public static final String ENGINE_MOVE_COMPUTED = "engine.move.computed";

    public static final String PLAYER_QUEUE_REQUESTED = "player.queue.requested";
    public static final String PLAYER_QUEUED = "player.queued";
    public static final String MATCH_FOUND = "matchmaking.match.found";

    public static final String PRESENCE_UPDATED = "presence.updated";

    public static final String NOTIFICATION_SENT = "notification.sent";

    public static final String ANALYSIS_REQUESTED = "analysis.requested";
    public static final String ANALYSIS_COMPLETED = "analysis.completed";

    private EventType() {
    }
}
