import { useState } from "react";
import { Smartphone, Monitor, Globe, Apple, RefreshCw, AlertCircle, Maximize2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { Panel } from "@/components/layout/Panel";
import { StatusBadge } from "@/components/ui/status-badge";
import type { Platform } from "@/types/debug";

interface PreviewFrameProps {
  platform: Platform;
  hasError?: boolean;
  errorMessage?: string;
  highlightedComponent?: string;
}

function PreviewFrame({ platform, hasError, errorMessage, highlightedComponent }: PreviewFrameProps) {
  const [isLoading, setIsLoading] = useState(false);

  const platformConfig = {
    android: {
      icon: Smartphone,
      label: "Android",
      emoji: "🤖",
      width: "w-[180px]",
      height: "h-[320px]",
      borderRadius: "rounded-2xl",
    },
    ios: {
      icon: Apple,
      label: "iOS",
      emoji: "🍎",
      width: "w-[180px]",
      height: "h-[320px]",
      borderRadius: "rounded-[28px]",
    },
    desktop: {
      icon: Monitor,
      label: "Desktop",
      emoji: "🖥️",
      width: "w-[280px]",
      height: "h-[200px]",
      borderRadius: "rounded-lg",
    },
    web: {
      icon: Globe,
      label: "Web (Wasm)",
      emoji: "🌐",
      width: "w-[280px]",
      height: "h-[200px]",
      borderRadius: "rounded-lg",
    },
  };

  const config = platformConfig[platform];
  const Icon = config.icon;

  const handleRefresh = () => {
    setIsLoading(true);
    setTimeout(() => setIsLoading(false), 800);
  };

  return (
    <div className="flex flex-col items-center">
      {/* Header */}
      <div className="flex items-center justify-between w-full mb-2 px-1">
        <div className="flex items-center gap-2">
          <span className="text-sm">{config.emoji}</span>
          <span className="text-xs font-medium text-muted-foreground">{config.label}</span>
          {hasError && (
            <StatusBadge variant="error" size="sm">Error</StatusBadge>
          )}
        </div>
        <div className="flex items-center gap-1">
          <button 
            onClick={handleRefresh}
            className="p-1 hover:bg-accent rounded transition-colors"
          >
            <RefreshCw className={cn("w-3.5 h-3.5 text-muted-foreground", isLoading && "animate-spin")} />
          </button>
          <button className="p-1 hover:bg-accent rounded transition-colors">
            <Maximize2 className="w-3.5 h-3.5 text-muted-foreground" />
          </button>
        </div>
      </div>

      {/* Device frame */}
      <div 
        className={cn(
          "relative bg-[#1a1a1a] border-4 border-[#2a2a2a] overflow-hidden",
          config.width,
          config.height,
          config.borderRadius,
          hasError && "border-error/50"
        )}
      >
        {/* Mock UI content */}
        <div className="absolute inset-0 bg-gradient-to-b from-slate-800 to-slate-900 p-3">
          {/* Status bar */}
          <div className="flex items-center justify-between mb-3 px-1">
            <span className="text-[10px] text-white/60">9:41</span>
            <div className="flex gap-1">
              <div className="w-3 h-1.5 bg-white/40 rounded-sm" />
              <div className="w-3 h-1.5 bg-white/40 rounded-sm" />
            </div>
          </div>

          {/* Header */}
          <div className="bg-white/10 rounded-lg p-2 mb-2">
            <div className="w-16 h-2 bg-white/30 rounded" />
          </div>

          {/* Content cards */}
          <div className="space-y-2">
            <div 
              className={cn(
                "bg-white/5 rounded-lg p-2 border border-transparent",
                highlightedComponent === "card1" && "border-warning animate-pulse"
              )}
            >
              <div className="w-full h-8 bg-white/10 rounded mb-1" />
              <div className="w-3/4 h-2 bg-white/20 rounded" />
            </div>

            <div 
              className={cn(
                "bg-white/5 rounded-lg p-2 border border-transparent",
                hasError && "border-error"
              )}
            >
              {hasError ? (
                <div className="flex items-center gap-2 text-error">
                  <AlertCircle className="w-4 h-4" />
                  <span className="text-[10px]">Render Error</span>
                </div>
              ) : (
                <>
                  <div className="w-full h-8 bg-white/10 rounded mb-1" />
                  <div className="w-1/2 h-2 bg-white/20 rounded" />
                </>
              )}
            </div>

            <div className="bg-white/5 rounded-lg p-2">
              <div className="flex gap-2">
                <div className="w-8 h-8 bg-white/10 rounded" />
                <div className="flex-1 space-y-1">
                  <div className="w-full h-2 bg-white/20 rounded" />
                  <div className="w-2/3 h-2 bg-white/10 rounded" />
                </div>
              </div>
            </div>
          </div>

          {/* Bottom nav */}
          <div className="absolute bottom-2 left-2 right-2">
            <div className="flex justify-around bg-white/10 rounded-lg p-2">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="w-5 h-5 bg-white/20 rounded" />
              ))}
            </div>
          </div>
        </div>

        {/* Loading overlay */}
        {isLoading && (
          <div className="absolute inset-0 bg-background/80 flex items-center justify-center">
            <RefreshCw className="w-6 h-6 text-primary animate-spin" />
          </div>
        )}
      </div>

      {/* Error tooltip */}
      {hasError && errorMessage && (
        <div className="mt-2 max-w-[200px] text-[10px] text-error bg-error-muted/50 px-2 py-1 rounded">
          {errorMessage}
        </div>
      )}
    </div>
  );
}

export function PreviewSandbox() {
  const [hoveredPlatform, setHoveredPlatform] = useState<Platform | null>(null);

  return (
    <div className="h-full flex flex-col">
      <Panel 
        title="Multiplatform Preview" 
        className="flex-1"
        actions={
          <span className="text-xs text-muted-foreground">
            Compose Multiplatform 1.6.0
          </span>
        }
      >
        <div className="h-full flex items-center justify-center">
          <div className="grid grid-cols-2 gap-8">
            <PreviewFrame 
              platform="android" 
              highlightedComponent={hoveredPlatform === "android" ? "card1" : undefined}
            />
            <PreviewFrame 
              platform="ios"
              hasError
              errorMessage="Missing actual implementation for getDeviceId()"
            />
            <PreviewFrame 
              platform="desktop"
            />
            <PreviewFrame 
              platform="web"
              hasError
              errorMessage="Wasm threading violation in CryptoUtils"
            />
          </div>
        </div>

        {/* Platform status bar */}
        <div className="mt-4 flex items-center justify-center gap-4 text-xs">
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-success" />
            <span className="text-muted-foreground">Android: Ready</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-error" />
            <span className="text-muted-foreground">iOS: 1 Error</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-success" />
            <span className="text-muted-foreground">Desktop: Ready</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-error" />
            <span className="text-muted-foreground">Web: 1 Error</span>
          </div>
        </div>
      </Panel>
    </div>
  );
}
