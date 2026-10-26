import * as React from "react";
import { Search } from "lucide-react";
import { cn } from "@/lib/utils";

// Search field with a leading icon (Dashtail tables). type="search" lets Escape clear it natively.
function SearchInput({ className, ...props }: Omit<React.ComponentProps<"input">, "type">) {
  return <div className={cn("relative", className)}>
    <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
    <input type="search" className="h-10 w-full min-w-0 rounded-md border border-input bg-card py-0 pl-9 pr-3 text-sm text-foreground outline-none placeholder:text-muted-foreground focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-ring/40" {...props} />
  </div>;
}

export { SearchInput };
