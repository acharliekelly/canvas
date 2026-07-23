/**
 * Owns ordered descriptions and their append-only revision and approval semantics. Callers use
 * its domain contract to create drafts or select approved revisions instead of mutating approved
 * wording in place.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "artwork")
package org.canvas.description;
