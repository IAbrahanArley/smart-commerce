import type { ReactNode } from "react";

// Each area owns its chrome: (site) the authentication header, the account and store areas the admin frame.
export default function PlatformLayout({ children }: { children: ReactNode }) {
  return children;
}
