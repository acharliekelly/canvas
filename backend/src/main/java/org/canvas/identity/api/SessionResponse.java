package org.canvas.identity.api;

public record SessionResponse(boolean authenticated, String username, String csrfToken) {
}
