/**
 * Owns the configured administrator and the session and CSRF security boundary. Other modules
 * rely on this module's authentication rules rather than defining competing access controls for
 * administrative workflow routes.
 */
@org.springframework.modulith.ApplicationModule
package org.canvas.identity;
