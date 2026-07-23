/**
 * Owns persistent caption-job orchestration and the replaceable worker contract that produces
 * draft material. This module coordinates worker calls and recovery; it never owns a caption
 * model implementation or the approval of generated descriptions.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"artwork", "description", "description :: api"})
package org.canvas.caption;
