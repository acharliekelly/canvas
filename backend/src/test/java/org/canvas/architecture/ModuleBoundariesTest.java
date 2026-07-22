package org.canvas.architecture;

import org.canvas.CanvasApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleBoundariesTest {
    @Test
    void applicationModulesFollowDeclaredBoundaries() {
        ApplicationModules.of(CanvasApplication.class).verify();
    }
}
