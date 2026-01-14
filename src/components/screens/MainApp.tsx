import { useNavigate } from "react-router-dom";
import { useDebugStore } from "@/store/debugStore";
import { DebugWorkspace } from "./DebugWorkspace";
import { PreviewSandbox } from "./PreviewSandbox";
import { RefactorPanel } from "./RefactorPanel";
import { StatusBadge } from "@/components/ui/status-badge";

export function MainApp() {
  const { activeView, repository } = useDebugStore();

  return (
    <div className="h-screen flex flex-col bg-background">
      {/* Header with navigation */}
      <header className="border-b border-border px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-primary/20 rounded flex items-center justify-center">
              <span className="text-primary font-mono font-bold text-sm">K</span>
            </div>
            <div>
              <span className="font-semibold text-foreground">DebugForge</span>
              <span className="text-xs text-muted-foreground font-mono ml-2">v0.9.0-beta</span>
            </div>
          </div>
          <ViewSwitcher />
        </div>

        <div className="flex items-center gap-3">
          {repository && (
            <div className="text-xs text-muted-foreground">
              {repository.name} ({repository.branch})
            </div>
          )}
          <ViewSwitcherNav />
        </div>
      </header>

      {/* Main content */}
      <div className="flex-1 overflow-hidden">
        {activeView === "workspace" && <DebugWorkspace />}
        {activeView === "refactor" && <RefactorPanel />}
        {/* Fallback: show workspace */}
        {(!activeView || activeView === "workspace") && activeView !== "refactor" && <DebugWorkspace />}
      </div>
    </div>
  );
}

function ViewSwitcherNav() {
  const navigate = useNavigate();
  return (
    <button 
      onClick={() => navigate("/")}
      className="text-xs text-muted-foreground hover:text-foreground"
    >
      ← Back
    </button>
  );
}

function ViewSwitcher() {
  const { activeView, setActiveView } = useDebugStore();

  // Only show workspace and refactor
  const views = ["workspace", "refactor"] as const;
  return (
    <div className="flex items-center gap-1">
      {views.map((view) => (
        <button
          key={view}
          onClick={() => setActiveView(view)}
          className={`px-3 py-1.5 text-xs font-medium rounded transition-colors ${
            activeView === view
              ? "bg-primary text-primary-foreground"
              : "text-muted-foreground hover:text-foreground hover:bg-accent"
          }`}
        >
          {view === "workspace" ? "Workspace" : "AI Suggestions"}
        </button>
      ))}
    </div>
  );
}
