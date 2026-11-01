package com.socialapp.gateway.config;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves the client IP only when the direct peer is a configured proxy. */
public final class TrustedProxyClientIpResolver {

    private final Set<String> trustedProxyAddresses;

    public TrustedProxyClientIpResolver(String configuredTrustedProxies) {
        this.trustedProxyAddresses = Arrays.stream(configuredTrustedProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(TrustedProxyClientIpResolver::canonicalIp)
                .peek(value -> {
                    if (value == null) {
                        throw new IllegalArgumentException("TRUSTED_PROXY_IPS must contain IP addresses only");
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        String peerIp = remoteAddress.getAddress().getHostAddress();
        if (!trustedProxyAddresses.contains(canonicalIp(peerIp))) {
            return peerIp;
        }

        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank() || forwardedFor.contains(",")) {
            return peerIp;
        }
        String clientIp = canonicalIp(forwardedFor);
        return clientIp == null ? peerIp : clientIp;
    }

    private static String canonicalIp(String rawAddress) {
        if (rawAddress == null) {
            return null;
        }
        String value = rawAddress.trim();
        if (value.isEmpty() || !value.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
