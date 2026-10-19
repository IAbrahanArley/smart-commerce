import type { NextConfig } from "next";
import { backendOrigin } from "./src/lib/deployment";

// Validated at build time: an invalid ADMIN_API_ORIGIN fails the build (ADR-0014).
const apiOrigin = backendOrigin(process.env.ADMIN_API_ORIGIN);

const nextConfig: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  turbopack: { root: process.cwd() },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiOrigin}/api/:path*` }];
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
