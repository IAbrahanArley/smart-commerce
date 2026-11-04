// Deployment settings read on the server (and by next.config.ts at build time). Free of Next imports
// so the smoke test can exercise them directly.

const LOOPBACK = new Set(["localhost", "127.0.0.1", "[::1]"]);

// Backend origin for the /api rewrite (ADR-0014): HTTPS, or HTTP only on loopback for local work.
// Anything else fails the build instead of proxying credentials to an unexpected host.
export function backendOrigin(value: string | undefined): string {
  const configured = value?.trim() || "http://127.0.0.1:8080";
  let url: URL;
  try {
    url = new URL(configured);
  } catch {
    throw new Error("ADMIN_API_ORIGIN must be an absolute URL");
  }
  const allowed = url.protocol === "https:" || (url.protocol === "http:" && LOOPBACK.has(url.hostname));
  if (!allowed || url.username || url.password || url.pathname !== "/" || url.search || url.hash)
    throw new Error("ADMIN_API_ORIGIN must be an HTTPS origin (HTTP only on loopback), without path");
  return url.origin;
}
