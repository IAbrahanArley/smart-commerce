import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  turbopack: { root: process.cwd() },
  // SC-047: product photos resized by this server (no paid CDN), only from the store media routes.
  images: {
    localPatterns: [{ pathname: "/midia/lojas/**", search: "" }],
    qualities: [75],
    minimumCacheTTL: 60,
  },
  async headers() {
    return [
      { source: "/:path*", headers: [
        { key: "Referrer-Policy", value: "no-referrer" },
        { key: "X-Content-Type-Options", value: "nosniff" },
        { key: "X-Frame-Options", value: "DENY" },
      ] },
      // HTML carries the visitor's cart count, so it is never stored by browsers or proxies; the
      // backend reads behind it are cached on this server (SC-047). Media set their own cache.
      { source: "/:path((?!midia/|_next/).*)", headers: [{ key: "Cache-Control", value: "no-store" }] },
    ];
  },
};

export default nextConfig;
