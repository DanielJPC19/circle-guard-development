package com.circleguard.promotion.controller;

import com.circleguard.promotion.service.HealthStatusService;
import com.circleguard.promotion.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthStatusController.class)
@Import(SecurityConfig.class)
class HealthStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthStatusService statusService;

    @Test
    @WithMockUser
    void confirmPositive_CallsUpdateStatus() throws Exception {
        String json = "{\"anonymousId\": \"user-1\", \"status\": \"CONFIRMED\"}";

        mockMvc.perform(post("/api/v1/promotion/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(statusService).updateStatus("user-1", "CONFIRMED", false);
    }

    @Test
    @WithMockUser
    void resolve_CallsResolveStatus() throws Exception {
        String json = "{\"anonymousId\": \"user-1\"}";

        mockMvc.perform(post("/api/v1/promotion/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(statusService).resolveStatus("user-1", false);
    }

    @Test
    @WithMockUser
    void resolve_WithMissingAnonymousId_Returns400() throws Exception {
        String json = "{}";

        mockMvc.perform(post("/api/v1/promotion/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}