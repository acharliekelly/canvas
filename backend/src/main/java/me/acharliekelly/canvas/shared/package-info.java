/**
 * Provides cross-module error and readiness primitives only. It does not own artwork,
 * description, caption, or publication workflow state.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"artwork", "caption", "description", "publication"})
package me.acharliekelly.canvas.shared;
