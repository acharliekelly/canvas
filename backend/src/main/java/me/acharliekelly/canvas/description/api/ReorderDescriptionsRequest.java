package me.acharliekelly.canvas.description.api;

import java.util.List;
import java.util.UUID;

public record ReorderDescriptionsRequest(List<UUID> descriptionIds, Long version) {
}
