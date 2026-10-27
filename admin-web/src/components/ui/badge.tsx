import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

// Adapted from Dashtail's badge (shadcn/ui); the text carries the meaning, color only reinforces it.
const badgeVariants = cva("inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-semibold", {
  variants: {
    variant: {
      default: "bg-primary-soft text-accent-foreground",
      success: "bg-success-soft text-success",
      destructive: "bg-destructive-soft text-destructive",
      outline: "border border-border text-muted-foreground",
    },
  },
  defaultVariants: { variant: "default" },
});

function Badge({ className, variant, ...props }: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}

function Kbd({ className, ...props }: React.ComponentProps<"kbd">) {
  return <kbd className={cn("inline-flex h-5 items-center rounded border border-border bg-muted px-1.5 font-sans text-[11px] font-medium text-muted-foreground", className)} {...props} />;
}

export { Badge, Kbd, badgeVariants };
