import { createHmac, timingSafeEqual } from "node:crypto";

// SC-047 (ADR-0018 V9): the backend asks this server to drop the cached reads of one store after
// the owner changes it. Only the two servers know the secret (>= 32 characters); the signature
// covers the store slug and a timestamp, and a call older or newer than 60 s is refused.
// Free of Next imports for the smoke test.

const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const WINDOW_SECONDS = 60;

export type RevalidationCheck = { ok: true; store: string } | { ok: false; status: 401 | 404 | 400 };

export function verifyRevalidation(secret: string | undefined, body: string, timestamp: string | null, signature: string | null, nowMs: number): RevalidationCheck {
  // Without a configured secret the route does not exist.
  if (!secret || secret.length < 32) return { ok: false, status: 404 };
  if (!timestamp || !/^\d{1,12}$/.test(timestamp) || Math.abs(Number(timestamp) - Math.floor(nowMs / 1000)) > WINDOW_SECONDS
    || !signature || !/^[0-9a-f]{64}$/.test(signature)) return { ok: false, status: 401 };
  let store: unknown;
  try {
    store = body.length <= 200 ? (JSON.parse(body) as { store?: unknown }).store : undefined;
  } catch {
    return { ok: false, status: 400 };
  }
  if (typeof store !== "string" || store.length < 3 || store.length > 63 || !SLUG.test(store)) return { ok: false, status: 400 };
  const expected = createHmac("sha256", secret).update(`${store}\n${timestamp}`).digest();
  return timingSafeEqual(expected, Buffer.from(signature, "hex")) ? { ok: true, store } : { ok: false, status: 401 };
}
