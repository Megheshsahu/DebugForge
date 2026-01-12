import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { 
  ChevronRight, 
  ChevronDown, 
  Folder,
  Copy,
  FileCode, 
  AlertCircle, 
  AlertTriangle,
  CheckCircle2,
  Layers,
  ToggleLeft,
  RefreshCw
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Panel } from "@/components/layout/Panel";
import { StatusBadge } from "@/components/ui/status-badge";
import { useDebugStore } from "@/store/debugStore";
import { useDiagnostics, useModules, useRefreshRepo, useMetrics } from "@/hooks/useApi";
import { mockModules, mockDiagnostics, mockSuggestions, mockSourceFile, mockScanResult } from "@/data/mockData";
import type { Module, Diagnostic } from "@/types/debug";

function ModuleTreeItem({ 
  module, 
  depth = 0,
  selectedId,
  onSelect 
}: { 
  module: Module; 
  depth?: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  const [expanded, setExpanded] = useState(depth === 0);
  const hasChildren = module.children && module.children.length > 0;
  const isSelected = module.id === selectedId;

  return (
    <div>
      <button
        onClick={() => {
          onSelect(module.id);
          if (hasChildren) setExpanded(!expanded);
        }}
        className={cn(
          "w-full flex items-center gap-1.5 px-2 py-1.5 text-sm rounded transition-colors",
          "hover:bg-accent",
          isSelected && "bg-accent text-accent-foreground"
        )}
        style={{ paddingLeft: `${depth * 12 + 8}px` }}
      >
        {hasChildren ? (
          expanded ? (
            <ChevronDown className="w-3.5 h-3.5 shrink-0 text-muted-foreground" />
          ) : (
            <ChevronRight className="w-3.5 h-3.5 shrink-0 text-muted-foreground" />
          )
        ) : (
          <span className="w-3.5" />
        )}
        
        {hasChildren ? (
          <Folder className="w-4 h-4 shrink-0 text-warning" />
        ) : (
          <FileCode className="w-4 h-4 shrink-0 text-info" />
        )}
        
        <span className="font-mono truncate">{module.name}</span>
        
        <div className="ml-auto flex items-center gap-1.5">
          {/* Shared code indicator */}
          <div 
            className="w-12 h-1.5 rounded-full bg-muted overflow-hidden"
            title={`${module.sharedCodePercent}% shared`}
          >
            <div 
              className={cn(
                "h-full rounded-full",
                module.sharedCodePercent > 80 ? "bg-success" :
                module.sharedCodePercent > 50 ? "bg-warning" : "bg-error"
              )}
              style={{ width: `${module.sharedCodePercent}%` }}
            />
          </div>
          
          {module.hasErrors && (
            <AlertCircle className="w-3.5 h-3.5 text-error" />
          )}
          {module.hasWarnings && !module.hasErrors && (
            <AlertTriangle className="w-3.5 h-3.5 text-warning" />
          )}
        </div>
      </button>
      
      {hasChildren && expanded && (
        <div>
          {module.children!.map((child) => (
            <ModuleTreeItem 
              key={child.id} 
              module={child} 
              depth={depth + 1}
              selectedId={selectedId}
              onSelect={onSelect}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function CodeViewer() {
  const { currentFile, showDiff, toggleDiff } = useDebugStore();
  const file = currentFile || mockSourceFile;
  
  const lines = file.content.split("\n");
  
  return (
    <Panel 
      title={file.path.split("/").pop() || "Source"} 
      noPadding
      actions={
        <button
          onClick={toggleDiff}
          className={cn(
            "flex items-center gap-1.5 px-2 py-1 rounded text-xs transition-colors",
            showDiff ? "bg-ai-muted text-ai" : "hover:bg-accent"
          )}
        >
          <ToggleLeft className="w-3.5 h-3.5" />
          Diff
        </button>
      }
    >
      <div className="text-xs text-muted-foreground px-3 py-1.5 border-b border-border bg-muted/50 font-mono">
        {file.path}
      </div>
      <div className="code-block overflow-auto">
        {lines.map((line, index) => {
          const lineNum = index + 1;
          const annotation = file.annotations.find((a) => a.line === lineNum);
          
          return (
            <div key={lineNum} className="group">
              <div 
                className={cn(
                  "code-line flex",
                  annotation?.type === "error" && "code-line-removed",
                  annotation?.type === "ai-suggestion" && "code-line-highlight"
                )}
              >
                <span className="code-line-number">{lineNum}</span>
                <pre className="flex-1 whitespace-pre">{line || " "}</pre>
              </div>
              {annotation && (
                <div 
                  className={cn(
                    "ml-16 px-3 py-1.5 text-xs border-l-2 -mt-px",
                    annotation.type === "error" 
                      ? "bg-error-muted/30 border-error text-error" 
                      : "bg-ai-muted/30 border-ai text-ai"
                  )}
                >
                  {annotation.message}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </Panel>
  );
}

function DiagnosticsPanel() {
  const { diagnostics: storeDiagnostics, selectedDiagnosticId, selectDiagnostic } = useDebugStore();
  const { data: apiDiagnostics, isLoading, refetch } = useDiagnostics();
  const refreshRepo = useRefreshRepo();
  
  // Use API diagnostics if available, otherwise fall back to store or mock
  const items = apiDiagnostics && apiDiagnostics.length > 0 
    ? apiDiagnostics.map(d => ({
        id: d.id,
        severity: d.severity.toLowerCase() as "error" | "warning" | "info",
        category: (d.category || "platform-api") as Diagnostic["category"],
        title: d.message,
        description: d.explanation || d.message,
        location: {
          file: d.location.filePath,
          line: d.location.startLine,
          column: d.location.startColumn,
        },
        platforms: ["android", "ios", "desktop", "web"] as const,
      }))
    : storeDiagnostics.length > 0 
      ? storeDiagnostics 
      : mockDiagnostics;
  
  const severityIcon = (severity: Diagnostic["severity"]) => {
    switch (severity) {
      case "error":
        return <AlertCircle className="w-4 h-4 text-error shrink-0" />;
      case "warning":
        return <AlertTriangle className="w-4 h-4 text-warning shrink-0" />;
      default:
        return <CheckCircle2 className="w-4 h-4 text-info shrink-0" />;
    }
  };

  const categoryLabel = (category: Diagnostic["category"]) => {
    const labels: Record<Diagnostic["category"], string> = {
      coroutine: "Coroutine",
      wasm: "Wasm",
      "platform-api": "Platform API",
      memory: "Memory",
      threading: "Threading",
    };
    return labels[category];
  };

  return (
    <Panel title="Diagnostics" noPadding>
      <div className="divide-y divide-border">
        {items.map((diag) => (
          <button
            key={diag.id}
            onClick={() => selectDiagnostic(diag.id)}
            className={cn(
              "w-full diagnostic-item text-left",
              selectedDiagnosticId === diag.id && "bg-accent/50"
            )}
          >
            {severityIcon(diag.severity)}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <span className="font-medium text-sm truncate">{diag.title}</span>
                <StatusBadge 
                  variant={diag.severity === "error" ? "error" : diag.severity === "warning" ? "warning" : "info"}
                  size="sm"
                >
                  {categoryLabel(diag.category)}
                </StatusBadge>
              </div>
              <p className="text-xs text-muted-foreground line-clamp-2">
                {diag.description}
              </p>
              <div className="flex items-center gap-2 mt-1.5">
                <span className="text-xs font-mono text-muted-foreground truncate">
                  {diag.location.file.split("/").pop()}:{diag.location.line}
                </span>
                <div className="flex gap-1">
                  {diag.platforms.map((p) => (
                    <span key={p} className="text-[10px] opacity-60">
                      {p === "android" && "🤖"}
                      {p === "ios" && "🍎"}
                      {p === "desktop" && "🖥️"}
                      {p === "web" && "🌐"}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </button>
        ))}
      </div>
    </Panel>
  );
}

export function DebugWorkspace() {
  const navigate = useNavigate();
  const { 
    repository, 
    modules, 
    selectedModuleId, 
    selectModule,
    isScanning,
    scanProgress,
    setScanProgress,
    setScanResult,
    setActiveView,
    activeView
  } = useDebugStore();

  // Simulate scan progress
  useEffect(() => {
    if (isScanning && scanProgress < 100) {
      const timer = setTimeout(() => {
        const next = Math.min(scanProgress + Math.random() * 15, 100);
        setScanProgress(next);
        if (next >= 100) {
          setScanResult(mockScanResult);
        }
      }, 200);
      return () => clearTimeout(timer);
    }
  }, [isScanning, scanProgress, setScanProgress, setScanResult]);

  const displayModules = modules.length > 0 ? modules : mockModules;

  return (
    <div className="h-screen flex flex-col bg-background">
      {/* Header */}
      <header className="border-b border-border px-4 py-2 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-4">
          <button 
            onClick={() => navigate("/")}
            className="flex items-center gap-2 hover:opacity-80"
          >
            <div className="w-6 h-6 bg-primary/20 rounded flex items-center justify-center">
              <span className="text-primary font-mono font-bold text-xs">K</span>
            </div>
            <span className="font-semibold text-sm">DebugForge</span>
          </button>
          
          {repository && (
            <>
              <span className="text-muted-foreground">/</span>
              <span className="text-sm font-mono">
                {repository.owner}/{repository.name}
              </span>
              <StatusBadge variant="info" size="sm" dot>
                {repository.branch}
              </StatusBadge>
            </>
          )}
        </div>
        
        <div className="flex items-center gap-1">
          {["workspace", "refactor"].map((view) => (
            <button
              key={view}
              onClick={() => setActiveView(view as any)}
              className={cn(
                "px-3 py-1.5 text-xs font-medium rounded transition-colors",
                activeView === view 
                  ? "bg-primary text-primary-foreground" 
                  : "text-muted-foreground hover:text-foreground hover:bg-accent"
              )}
            >
              {view.charAt(0).toUpperCase() + view.slice(1)}
            </button>
          ))}
        </div>
      </header>

      {/* Scanning overlay */}
      {isScanning && (
        <div className="absolute inset-0 bg-background/80 backdrop-blur-sm z-50 flex items-center justify-center">
          <div className="text-center space-y-4">
            <div className="relative w-48 h-1 bg-muted rounded-full overflow-hidden">
              <div 
                className="absolute inset-y-0 left-0 bg-primary transition-all duration-200"
                style={{ width: `${scanProgress}%` }}
              />
            </div>
            <p className="text-sm text-muted-foreground">
              Analyzing repository... {Math.round(scanProgress)}%
            </p>
          </div>
        </div>
      )}

      {/* Main workspace */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left: Module Tree */}
        <div className="w-64 border-r border-border flex flex-col shrink-0">
          <Panel title="Modules" className="flex-1 rounded-none border-0">
            <div className="space-y-0.5">
              {displayModules.map((module) => (
                <ModuleTreeItem 
                  key={module.id} 
                  module={module}
                  selectedId={selectedModuleId}
                  onSelect={selectModule}
                />
              ))}
            </div>
            
            {/* Legend */}
            <div className="mt-6 pt-4 border-t border-border space-y-2">
              <div className="text-xs text-muted-foreground uppercase tracking-wider mb-2">
                Shared Code %
              </div>
              <div className="flex items-center gap-2 text-xs">
                <div className="w-8 h-1.5 rounded-full bg-success" />
                <span className="text-muted-foreground">&gt;80%</span>
              </div>
              <div className="flex items-center gap-2 text-xs">
                <div className="w-8 h-1.5 rounded-full bg-warning" />
                <span className="text-muted-foreground">50-80%</span>
              </div>
              <div className="flex items-center gap-2 text-xs">
                <div className="w-8 h-1.5 rounded-full bg-error" />
                <span className="text-muted-foreground">&lt;50%</span>
              </div>
            </div>
          </Panel>
        </div>

        {/* Center: Code Viewer */}
        <div className="flex-1 flex flex-col overflow-hidden p-2">
          <CodeViewer />
        </div>

        {/* Right: Diagnostics */}
        <div className="w-80 border-l border-border p-2 shrink-0">
          <DiagnosticsPanel />
        </div>
      </div>
    </div>
  );
}
