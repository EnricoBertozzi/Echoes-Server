package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.exception.DeviceAuthenticationException;
import com.n0hana.echoes_server.device.port.DeviceAccessPolicy;
import com.n0hana.echoes_server.device.port.DeviceCredentialVerifier;

@ExtendWith(MockitoExtension.class)
class DeviceJwtFilterTests {

  private static final String TOKEN = "device.jwt.token";

  @Mock
  private DeviceCredentialVerifier verifier;

  @Mock
  private DeviceAccessPolicy policy;

  private DeviceJwtFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockFilterChain chain;

  @BeforeEach
  void setUp() {
    filter = new DeviceJwtFilter(verifier, policy);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Sem Authorization, segue a cadeia sem autenticar")
  void shouldPassThroughWithoutHeader() throws Exception {
    filter.doFilter(request, response, chain);

    assertNotNull(chain.getRequest());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Credencial que não é de dispositivo segue para os demais filtros")
  void shouldPassThroughWhenNotSupported() throws Exception {
    request.addHeader("Authorization", "Bearer " + TOKEN);
    when(verifier.supports(TOKEN)).thenReturn(false);

    filter.doFilter(request, response, chain);

    assertNotNull(chain.getRequest());
    verify(verifier, never()).verify(TOKEN);
  }

  @Test
  @DisplayName("Credencial inválida responde 401 e interrompe a cadeia")
  void shouldReturn401WhenInvalid() throws Exception {
    request.addHeader("Authorization", "Bearer " + TOKEN);
    when(verifier.supports(TOKEN)).thenReturn(true);
    when(verifier.verify(TOKEN)).thenThrow(new DeviceAuthenticationException());

    filter.doFilter(request, response, chain);

    assertEquals(401, response.getStatus());
    assertNull(chain.getRequest());
  }

  @Test
  @DisplayName("Dispositivo que não pode operar responde 403 e interrompe a cadeia")
  void shouldReturn403WhenCannotOperate() throws Exception {
    DeviceModel device = DeviceModel.builder().build();
    request.addHeader("Authorization", "Bearer " + TOKEN);
    when(verifier.supports(TOKEN)).thenReturn(true);
    when(verifier.verify(TOKEN)).thenReturn(device);
    when(policy.canOperate(device)).thenReturn(false);

    filter.doFilter(request, response, chain);

    assertEquals(403, response.getStatus());
    assertNull(chain.getRequest());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Dispositivo que pode operar é autenticado com ROLE_DEVICE")
  void shouldAuthenticateWhenCanOperate() throws Exception {
    DeviceModel device = DeviceModel.builder().build();
    request.addHeader("Authorization", "Bearer " + TOKEN);
    when(verifier.supports(TOKEN)).thenReturn(true);
    when(verifier.verify(TOKEN)).thenReturn(device);
    when(policy.canOperate(device)).thenReturn(true);

    filter.doFilter(request, response, chain);

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(authentication);
    assertEquals(device, authentication.getPrincipal());
    assertEquals("ROLE_DEVICE", authentication.getAuthorities().iterator().next().getAuthority());
    assertNotNull(chain.getRequest());
  }
}
