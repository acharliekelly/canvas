/**
 * Owns immutable publication snapshots, public reads of the current snapshot, and associations
 * to generated assets. It publishes approved description inputs without allowing later workflow
 * edits to mutate historical public content.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"artwork", "description", "storage"})
package org.canvas.publication;
