import type { NextConfig } from "next";
const config: NextConfig = {
  devIndicators: false,
  async rewrites() {
    const upstream = new URL(process.env.GATEWAY_UPSTREAM || "http://127.0.0.1:8080");
    if (!["http:", "https:"].includes(upstream.protocol) || upstream.username || upstream.password || upstream.pathname !== "/" || upstream.search || upstream.hash) {
      throw new Error("GATEWAY_UPSTREAM must be an HTTP(S) origin without credentials or path");
    }
    return [
      { source: "/api/:path*", destination: `${upstream.origin}/api/:path*` },
      { source: "/ws/:path*", destination: `${upstream.origin}/ws/:path*` },
      { source: "/ws-notifications/:path*", destination: `${upstream.origin}/ws-notifications/:path*` },
    ];
  },
  async redirects() {
    return [
      {
        source: "/forums/:path*",
        destination: "/explore/:path*",
        permanent: false,
      },
    ];
  },
};
export default config;
