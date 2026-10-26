import * as React from "react";
import { cn } from "@/lib/utils";

// Adapted from Dashtail's card (shadcn/ui).
function Card({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("rounded-lg border border-border bg-card text-card-foreground shadow-card", className)} {...props} />;
}
function CardHeader({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("flex flex-col gap-1.5 p-6 pb-0", className)} {...props} />;
}
function CardTitle({ className, ...props }: React.ComponentProps<"h2">) {
  return <h2 className={cn("m-0 text-lg font-semibold leading-tight", className)} {...props} />;
}
function CardDescription({ className, ...props }: React.ComponentProps<"p">) {
  return <p className={cn("m-0 text-sm text-muted-foreground", className)} {...props} />;
}
function CardContent({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("p-6", className)} {...props} />;
}

export { Card, CardHeader, CardTitle, CardDescription, CardContent };
