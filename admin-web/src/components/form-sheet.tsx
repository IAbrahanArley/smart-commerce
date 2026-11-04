"use client";

import type { ComponentProps, ReactNode, RefObject } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";

// Forms that create or act on a listed item open in a side sheet, so the page keeps only what it
// shows (2026-11-04). Radix keeps focus inside, Escape and the close button dismiss it, and callers
// keep the draft in their state, so closing and reopening keeps what was typed. While a request runs
// the sheet stays open; a confirmation <dialog> opened inside keeps Escape for itself. On close,
// focus goes to the page feedback when the action left one, otherwise back to the opener.
export function FormSheet({ open, onOpenChange, titleId, title, description, busy = false, feedbackRef, children }: {
  open: boolean; onOpenChange: (open: boolean) => void; titleId: string; title: ReactNode; description?: ReactNode; busy?: boolean;
  feedbackRef?: RefObject<HTMLElement | null>; children: ReactNode;
}) {
  return <Dialog open={open} onOpenChange={next => { if (!busy || next) onOpenChange(next); }}>
    <DialogContent side="right" aria-labelledby={titleId} {...(description ? {} : { "aria-describedby": undefined })}
      onCloseAutoFocus={event => { if (feedbackRef?.current) { event.preventDefault(); feedbackRef.current.focus(); } }}
      onEscapeKeyDown={event => { if (busy || document.querySelector("dialog[open]")) event.preventDefault(); }}
      onInteractOutside={event => { if (busy) event.preventDefault(); }}>
      <div className="form-sheet-header">
        <DialogTitle id={titleId}>{title}</DialogTitle>
        {description ? <DialogDescription>{description}</DialogDescription> : null}
      </div>
      <div className="form-sheet-body">{children}</div>
    </DialogContent>
  </Dialog>;
}

// The page header button that opens a sheet. The plus sign is decorative: the label says the action.
export function AddButton({ children, ...props }: ComponentProps<typeof Button>) {
  return <Button type="button" {...props}><Plus aria-hidden="true" />{children}</Button>;
}
