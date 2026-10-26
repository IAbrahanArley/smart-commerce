"use client";

import * as React from "react";
import { Eye, EyeOff } from "lucide-react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

// Password field with a show/hide toggle (Dashtail login). The toggle is a real button with a name and
// pressed state; the field keeps its autocomplete so password managers still work.
function PasswordInput({ className, ref, toggleLabel = "Mostrar senha", ...props }: Omit<React.ComponentProps<"input">, "type"> & { toggleLabel?: string }) {
  const [visible, setVisible] = React.useState(false);
  return <div className="relative">
    <Input ref={ref} type={visible ? "text" : "password"} className={cn("pr-12", className)} {...props} />
    <button type="button" onClick={() => setVisible(current => !current)} aria-pressed={visible} aria-label={toggleLabel}
      className="absolute right-1 top-1/2 inline-flex size-9 min-h-0 -translate-y-1/2 items-center justify-center rounded-md bg-transparent p-0 text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:ring-2 focus-visible:ring-ring">
      {visible ? <EyeOff className="size-5" aria-hidden="true" /> : <Eye className="size-5" aria-hidden="true" />}
    </button>
  </div>;
}

export { PasswordInput };
