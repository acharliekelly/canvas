package me.acharliekelly.canvas.identity.api;

public record SessionResponse(boolean authenticated, String username, String csrfToken) {
}
