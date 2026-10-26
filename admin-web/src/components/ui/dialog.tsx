"use client";

import * as React from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

// Adapted from Dashtail's dialog and sheet (shadcn/ui, Radix): focus trap, Escape, focus return and
// an inert page behind. "side" turns the dialog into a drawer (the mobile navigation).
const Dialog = DialogPrimitive.Root;
const DialogTrigger = DialogPrimitive.Trigger;
const DialogClose = DialogPrimitive.Close;
const DialogTitle = DialogPrimitive.Title;
const DialogDescription = DialogPrimitive.Description;

function DialogContent({ className, children, side, closeLabel = "Fechar", ...props }: React.ComponentProps<typeof DialogPrimitive.Content> & { side?: "left"; closeLabel?: string }) {
  return <DialogPrimitive.Portal>
    <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-foreground/40" />
    <DialogPrimitive.Content className={cn(
      "fixed z-50 bg-card text-card-foreground shadow-overlay outline-none",
      side === "left"
        ? "inset-y-0 left-0 flex h-full w-[min(18rem,85vw)] flex-col overscroll-contain border-r border-border"
        : "left-1/2 top-[12vh] w-[min(40rem,calc(100vw-2rem))] -translate-x-1/2 rounded-xl border border-border",
      className)} {...props}>
      {children}
      <DialogPrimitive.Close className="absolute right-3 top-3 inline-flex size-9 min-h-0 items-center justify-center rounded-md bg-transparent p-0 text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:ring-2 focus-visible:ring-ring" aria-label={closeLabel}>
        <X className="size-5" aria-hidden="true" />
      </DialogPrimitive.Close>
    </DialogPrimitive.Content>
  </DialogPrimitive.Portal>;
}

export { Dialog, DialogTrigger, DialogClose, DialogContent, DialogTitle, DialogDescription };
