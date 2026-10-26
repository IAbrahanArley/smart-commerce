import * as React from "react";
import { cn } from "@/lib/utils";

// Adapted from Dashtail's input (shadcn/ui): 44px tall, visible border (3:1) and focus ring.
function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return <input type={type} className={cn(
    "flex h-11 w-full min-w-0 rounded-md border border-input bg-card px-3 text-base text-foreground placeholder:text-muted-foreground outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-ring/40 disabled:cursor-not-allowed disabled:bg-muted aria-invalid:border-destructive",
    className)} {...props} />;
}

function Label({ className, ...props }: React.ComponentProps<"label">) {
  return <label className={cn("text-sm font-medium text-foreground", className)} {...props} />;
}

export { Input, Label };
