package com.socialapp.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustedProxyClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersFromUntrustedPeers() throws Exception {
        TrustedProxyClientIpResolver resolver = new TrustedProxyClientIpResolver("127.0.0.1");
        ServerHttpRequest request = request("203.0.113.8", "198.51.100.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.8");
    }

    @Test
    void trustsOneForwardedIpOnlyFromConfiguredProxy() throws Exception {
        TrustedProxyClientIpResolver resolver = new TrustedProxyClientIpResolver("127.0.0.1, ::1");

        assertThat(resolver.resolve(request("127.0.0.1", "198.51.100.9"))).isEqualTo("198.51.100.9");
        assertThat(resolver.resolve(request("127.0.0.1", "198.51.100.9, 192.0.2.7"))).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve(request("127.0.0.1", "not-an-ip"))).isEqualTo("127.0.0.1");
    }

    @Test
    void trustedProxyConfigurationAcceptsIpAddressesOnly() {
        assertThatThrownBy(() -> new TrustedProxyClientIpResolver("proxy.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ServerHttpRequest request(String peerIp, String forwardedFor) throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress(InetAddress.getByName(peerIp), 12345));
        HttpHeaders headers = new HttpHeaders();
        if (forwardedFor != null) {
            headers.set("X-Forwarded-For", forwardedFor);
        }
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }
}
