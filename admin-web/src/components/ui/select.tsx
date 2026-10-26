import * as React from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";

// Native select styled like Dashtail's: keeps the platform's keyboard, screen reader and mobile pickers.
function Select({ className, children, ...props }: React.ComponentProps<"select">) {
  return <div className={cn("relative", className)}>
    <select className="h-10 w-full min-w-0 cursor-pointer appearance-none rounded-md border border-input bg-card py-0 pl-3 pr-9 text-sm text-foreground outline-none focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-ring/40" {...props}>{children}</select>
    <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
  </div>;
}

export { Select };
