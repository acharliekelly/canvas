package org.canvas.identity.api;

import java.security.Principal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionController {
    @GetMapping
    SessionResponse session(Principal principal, CsrfToken csrfToken) {
        return new SessionResponse(principal != null, principal == null ? null : principal.getName(),
                csrfToken.getToken());
    }
}
