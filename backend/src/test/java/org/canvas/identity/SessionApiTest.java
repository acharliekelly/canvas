package org.canvas.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "canvas.admin.username=configured-admin",
        "canvas.admin.password-hash={noop}secret"
})
class SessionApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void anonymousSessionIncludesCsrfToken() throws Exception {
        mvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.username").isEmpty())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    }

    @Test
    void configuredCredentialsEstablishSession() throws Exception {
        MvcResult login = mvc.perform(formLogin("/api/login")
                        .user("configured-admin")
                        .password("secret"))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(get("/api/session").session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("configured-admin"))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    }

    @Test
    void incorrectCredentialsReturnJsonUnauthorized() throws Exception {
        mvc.perform(formLogin("/api/login")
                        .user("configured-admin")
                        .password("wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
