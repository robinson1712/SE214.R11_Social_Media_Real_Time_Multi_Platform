import type { NextConfig } from "next";
const config: NextConfig = {
  devIndicators: false,
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
