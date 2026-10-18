import { createHmac } from "node:crypto";

// D4 (ADR-0012): visitor IP for the backend checkout limit. Free of Next imports for the smoke test.

const IP = /^[0-9A-Fa-f:.]{2,45}$/;

// Next keeps a visitor-supplied X-Forwarded-For untouched, so the header is trusted only when the
// deployment declares a proxy in front that overwrites it; then the last hop is what that proxy saw.
// Otherwise no IP is signed and the backend limits by the connection address (this server).
export function trustedClientIp(forwardedFor: string | null, trustForwardedFor: string | undefined): string | null {
  if (trustForwardedFor !== "true") return null;
  const last = forwardedFor?.split(",").at(-1)?.trim();
  return last && IP.test(last) ? last : null;
}

// Signed with the server-only shared secret (>= 32 characters); without it nothing is sent.
export function signedClientIp(ip: string | null, secret: string | undefined, now: number): Record<string, string> {
  if (!ip || !IP.test(ip) || !secret || secret.length < 32) return {};
  const timestamp = String(Math.floor(now / 1000));
  return {
    "X-SC-Client-IP": ip,
    "X-SC-Client-IP-Timestamp": timestamp,
    "X-SC-Client-IP-Signature": createHmac("sha256", secret).update(`${ip}\n${timestamp}`).digest("hex"),
  };
}
