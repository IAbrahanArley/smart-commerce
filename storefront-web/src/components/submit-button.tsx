"use client";

import { useFormStatus } from "react-dom";

type Props = { children: React.ReactNode; pendingLabel: string; className?: string; disabled?: boolean; "aria-label"?: string };

// Blocks repeated submissions while the action is pending; the server still revalidates everything.
export function SubmitButton({ children, pendingLabel, className, disabled, "aria-label": ariaLabel }: Props) {
  const { pending } = useFormStatus();
  return (
    <button type="submit" className={className} disabled={disabled || pending} aria-label={pending ? undefined : ariaLabel}>
      {pending ? pendingLabel : children}
    </button>
  );
}
