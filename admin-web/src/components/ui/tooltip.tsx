"use client";

import * as React from "react";
import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import { cn } from "@/lib/utils";

// Adapted from Dashtail's tooltip (shadcn/ui, Radix). Tooltips only repeat a visible or accessible
// name; they never hold information that exists nowhere else.
const TooltipProvider = TooltipPrimitive.Provider;
const Tooltip = TooltipPrimitive.Root;
const TooltipTrigger = TooltipPrimitive.Trigger;

function TooltipContent({ className, sideOffset = 8, ...props }: React.ComponentProps<typeof TooltipPrimitive.Content>) {
  return <TooltipPrimitive.Portal>
    <TooltipPrimitive.Content sideOffset={sideOffset} className={cn(
      "z-50 rounded-md bg-foreground px-3 py-1.5 text-sm font-medium text-card shadow-overlay", className)} {...props} />
  </TooltipPrimitive.Portal>;
}

export { Tooltip, TooltipTrigger, TooltipContent, TooltipProvider };
