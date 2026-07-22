package org.canvas.description.api;

public record UpdateDraftRequest(String label, String text, Long version) {
}
