/**
 * Owns artwork metadata, validated original-image ingestion, and artwork lifecycle state.
 * Other modules use the artwork API or repository-visible domain contract rather than taking
 * ownership of object-storage validation and compensation.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "storage")
package org.canvas.artwork;
