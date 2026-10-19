package com.chatapp.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of who is online, in memory.
 * A user can have several WebSocket sessions (tabs, devices) and counts as online while at least one is open.
 */
@Service
public class PresenceService {

    private final Map<String, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, String> userBySession = new ConcurrentHashMap<>();

    /** Registers a session. Returns true if it is the user's first open session (they just came online). */
    public boolean connected(String username, String sessionId) {
        userBySession.put(sessionId, username);
        boolean[] first = {false};
        sessionsByUser.compute(username, (key, sessions) -> {
            Set<String> updated = sessions;
            if (updated == null) {
                updated = ConcurrentHashMap.newKeySet();
                first[0] = true;
            }
            updated.add(sessionId);
            return updated;
        });
        return first[0];
    }

    /** Removes a session. Returns the username if that was their last open session, otherwise null. */
    public String disconnected(String sessionId) {
        String username = userBySession.remove(sessionId);
        if (username == null) {
            return null;
        }
        String[] wentOffline = {null};
        sessionsByUser.computeIfPresent(username, (key, sessions) -> {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                wentOffline[0] = username;
                return null;   // returning null removes the entry
            }
            return sessions;
        });
        return wentOffline[0];
    }

    public boolean isOnline(String username) {
        return sessionsByUser.containsKey(username);
    }
}
