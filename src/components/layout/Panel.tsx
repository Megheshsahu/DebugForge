import { cn } from "@/lib/utils";
import { ReactNode } from "react";

interface PanelProps {
  title?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  contentClassName?: string;
  noPadding?: boolean;
}

export function Panel({
  title,
  actions,
  children,
  className,
  contentClassName,
  noPadding = false,
}: PanelProps) {
  return (
    <div className={cn("panel flex flex-col h-full", className)}>
      {title && (
        <div className="panel-header flex items-center justify-between shrink-0">
          <span>{title}</span>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
      )}
      <div
        className={cn(
          "flex-1 overflow-auto",
          !noPadding && "panel-content",
          contentClassName
        )}
      >
        {children}
      </div>
    </div>
  );
}
