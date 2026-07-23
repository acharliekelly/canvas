/**
 * Owns ordered descriptions, editable current drafts, and retained immutable approved revisions.
 * Callers use its domain contract to create or edit drafts and select approved revisions instead
 * of mutating approved wording in place.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "artwork")
package me.acharliekelly.canvas.description;
