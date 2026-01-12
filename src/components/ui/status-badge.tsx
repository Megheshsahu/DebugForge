import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const statusBadgeVariants = cva(
  "inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-medium rounded border",
  {
    variants: {
      variant: {
        error: "bg-error-muted text-error border-error/30",
        warning: "bg-warning-muted text-warning border-warning/30",
        success: "bg-success-muted text-success border-success/30",
        info: "bg-info-muted text-info border-info/30",
        ai: "bg-ai-muted text-ai border-ai/30",
        neutral: "bg-muted text-muted-foreground border-border",
      },
      size: {
        sm: "text-[10px] px-1.5 py-0",
        default: "text-xs px-2 py-0.5",
        lg: "text-sm px-2.5 py-1",
      },
    },
    defaultVariants: {
      variant: "neutral",
      size: "default",
    },
  }
);

interface StatusBadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof statusBadgeVariants> {
  dot?: boolean;
}

export function StatusBadge({
  className,
  variant,
  size,
  dot = false,
  children,
  ...props
}: StatusBadgeProps) {
  return (
    <span
      className={cn(statusBadgeVariants({ variant, size }), className)}
      {...props}
    >
      {dot && (
        <span
          className={cn("w-1.5 h-1.5 rounded-full", {
            "bg-error": variant === "error",
            "bg-warning": variant === "warning",
            "bg-success": variant === "success",
            "bg-info": variant === "info",
            "bg-ai": variant === "ai",
            "bg-muted-foreground": variant === "neutral",
          })}
        />
      )}
      {children}
    </span>
  );
}
