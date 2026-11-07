package com.mallsen.controller.notify;

import com.mallsen.properties.PayProperties;
import com.mallsen.result.Result;
import com.mallsen.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayNotifyControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PayProperties payProperties;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private PayNotifyController payNotifyController;

    @Test
    void mockPaySuccessNotify_shouldRejectWhenMockDisabled() {
        when(payProperties.isMock()).thenReturn(false);

        Result<?> result = payNotifyController.mockPaySuccessNotify(request, "O2026001");

        assertEquals(0, result.getCode());
        verifyNoInteractions(orderService);
    }

    @Test
    void mockPaySuccessNotify_shouldRejectWhenTokenMissing() {
        when(payProperties.isMock()).thenReturn(true);
        when(payProperties.getMockToken()).thenReturn("secret");
        when(request.getHeader("mock-token")).thenReturn(null);

        Result<?> result = payNotifyController.mockPaySuccessNotify(request, "O2026001");

        assertEquals(0, result.getCode());
        verify(orderService, never()).paySuccess(anyString());
    }

    @Test
    void mockPaySuccessNotify_shouldRejectWhenTokenInvalid() {
        when(payProperties.isMock()).thenReturn(true);
        when(payProperties.getMockToken()).thenReturn("secret");
        when(request.getHeader("mock-token")).thenReturn("wrong-token");

        Result<?> result = payNotifyController.mockPaySuccessNotify(request, "O2026001");

        assertEquals(0, result.getCode());
        verify(orderService, never()).paySuccess(anyString());
    }

    @Test
    void mockPaySuccessNotify_shouldCallPaySuccessWhenTokenMatches() {
        when(payProperties.isMock()).thenReturn(true);
        when(payProperties.getMockToken()).thenReturn("secret");
        when(request.getHeader("mock-token")).thenReturn("secret");

        Result<?> result = payNotifyController.mockPaySuccessNotify(request, "O2026001");

        assertEquals(1, result.getCode());
        verify(orderService).paySuccess("O2026001");
    }
}
