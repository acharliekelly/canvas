/**
 * Owns private S3-compatible object operations for originals and generated assets, independent
 * of domain workflow. Domain modules decide lifecycle, authorization, and compensation while this
 * module provides bucket-qualified binary operations.
 */
@org.springframework.modulith.ApplicationModule
package me.acharliekelly.canvas.storage;
