import { useNavigate } from "react-router-dom";
import { useDebugStore } from "@/store/debugStore";
import { DebugWorkspace } from "./DebugWorkspace";
import { PreviewSandbox } from "./PreviewSandbox";
import { RefactorPanel } from "./RefactorPanel";
import { StatusBadge } from "@/components/ui/status-badge";

export function MainApp() {
  const { activeView, repository } = useDebugStore();
  
  // Only workspace and refactor views are supported
  if (activeView === "workspace") {
    return <DebugWorkspace />;
  }
  if (activeView === "refactor") {
    return <RefactorPanel />;
  }
  // Fallback: show workspace
  return <DebugWorkspace />;
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
  const navigate = useNavigate();
  
  // Only show workspace and refactor
  const views = ["workspace", "refactor"] as const;
  return (
    <div className="flex items-center gap-1">
      {views.map((view) => (
        <button
          key={view}
          onClick={() => {
            setActiveView(view);
            if (view === "workspace") {
              navigate("/workspace");
            }
          }}
          className={`px-3 py-1.5 text-xs font-medium rounded transition-colors ${
            activeView === view 
              ? "bg-primary text-primary-foreground" 
              : "text-muted-foreground hover:text-foreground hover:bg-accent"
          }`}
        >
          {view.charAt(0).toUpperCase() + view.slice(1)}
        </button>
      ))}
    </div>
  );
}
