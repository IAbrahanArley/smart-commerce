import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  turbopack: { root: process.cwd() },
  async rewrites() {
    return [{ source: "/api/:path*", destination: "http://127.0.0.1:8080/api/:path*" }];
  },
  async headers() {
    return [{ source: "/:path*", headers: [
      { key: "Referrer-Policy", value: "no-referrer" },
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "X-Frame-Options", value: "DENY" },
      { key: "Cache-Control", value: "no-store" },
    ] }];
  },
};

export default nextConfig;
