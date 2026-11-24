package com.fincore.checkout.security;

import com.fincore.checkout.client.SettlementClient;
import com.fincore.checkout.config.TestKafkaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestKafkaConfig.class)
class AdminSecurityIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean SettlementClient settlement;

    @Test
    void recordsAuthenticatedMutationAndDeniedAccessWithoutSensitiveInput() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/v1/admin/settlement/batches/" + id + "/payout")
                        .with(oidcLogin().idToken(t -> t.subject("finance-subject"))
                                .authorities(new SimpleGrantedAuthority("ROLE_FINANCE_OPS")))
                        .with(csrf()))
                .andExpect(status().isOk());
        assertEquals(2, jdbc.sql("SELECT count(*) FROM checkout_bff.audit_events WHERE resource_id = ?")
                .param(id).query(Integer.class).single());
        assertEquals("user:finance-subject", jdbc.sql("SELECT actor_subject FROM checkout_bff.audit_events WHERE resource_id = ? AND outcome = 'SUCCESS'")
                .param(id).query(String.class).single());
        verify(settlement).payout(id);

        UUID denied = UUID.randomUUID();
        mvc.perform(post("/v1/admin/settlement/batches/" + denied + "/payout")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        assertEquals(403, jdbc.sql("SELECT http_status FROM checkout_bff.audit_events WHERE resource_id = ? AND outcome = 'REJECTED'")
                .param(denied).query(Integer.class).single());
        verify(settlement, never()).payout(denied);
    }

    @Test
    void unknownSettlementMutationIsDeniedEvenToFinance() throws Exception {
        mvc.perform(post("/v1/admin/settlement/new-dangerous-action")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_OPS"))).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(settlement);
    }

    @Test
    void securityHeadersAndFixedPkceCallback() throws Exception {
        mvc.perform(get("/v1/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")));
        mvc.perform(get("/oauth2/authorization/keycloak").header("Host", "untrusted.invalid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code_challenge_method=S256")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("redirect_uri=http://127.0.0.1:5173/login/oauth2/code/keycloak")));
    }
}
