package com.fincore.checkout.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAuditFilterTest {

    @Test
    void doesNotExecuteMutationWhenAuditStorageIsUnavailable() throws Exception {
        var store = mock(AdminAuditStore.class);
        doThrow(new DataAccessResourceFailureException("offline"))
                .when(store).append(any(), any(), anyString(), nullable(java.util.UUID.class), anyString(), anyInt());
        var chain = mock(FilterChain.class);
        var request = new MockHttpServletRequest("POST", "/v1/admin/settlement/refunds");
        var response = new MockHttpServletResponse();
        new AdminAuditFilter(store).doFilter(request, response, chain);
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUDIT_UNAVAILABLE"));
        verifyNoInteractions(chain);
    }
}
