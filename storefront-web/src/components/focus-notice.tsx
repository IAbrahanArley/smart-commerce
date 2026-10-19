"use client";

import { useEffect, useRef } from "react";

// Notices rendered after a redirect are already in the markup at load, where screen readers often
// skip live regions; moving focus to them makes the result announced. Without JS it is just text.
export function FocusNotice({ className, children }: { className: string; children: string }) {
  const ref = useRef<HTMLParagraphElement>(null);
  useEffect(() => { ref.current?.focus(); }, []);
  return <p ref={ref} className={className} role="status" tabIndex={-1}>{children}</p>;
}
