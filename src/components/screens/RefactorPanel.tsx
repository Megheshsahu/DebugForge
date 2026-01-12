import { useState } from "react";
import { api } from "@/services/api";
import { Check, X, Pencil, ChevronDown, ChevronUp, Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import { Panel } from "@/components/layout/Panel";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { useDebugStore } from "@/store/debugStore";
import { mockSuggestions } from "@/data/mockData";
import type { AIRefactorSuggestion } from "@/types/debug";

function DiffView({ before, after, showFull }: { before: string; after: string; showFull: boolean }) {
  const beforeLines = before.split("\n");
  const afterLines = after.split("\n");

  return (
    <div className="grid grid-cols-2 gap-2 text-xs font-mono">
      {/* Before */}
      <div className="code-block overflow-hidden">
        <div className="px-3 py-1.5 bg-error-muted/30 text-error text-[10px] uppercase tracking-wider border-b border-error/20">
          Before
        </div>
        <div className="overflow-auto max-h-48">
          {(showFull ? beforeLines : beforeLines.slice(0, 6)).map((line, i) => (
            <div key={i} className="code-line code-line-removed px-3 py-0.5">
              <span className="code-line-number text-error/50">{i + 1}</span>
              <pre className="whitespace-pre">{line}</pre>
            </div>
          ))}
          {!showFull && beforeLines.length > 6 && (
            <div className="px-3 py-1 text-muted-foreground text-center">
              ... {beforeLines.length - 6} more lines
            </div>
          )}
        </div>
      </div>

      {/* After */}
      <div className="code-block overflow-hidden">
        <div className="px-3 py-1.5 bg-success-muted/30 text-success text-[10px] uppercase tracking-wider border-b border-success/20">
          After
        </div>
        <div className="overflow-auto max-h-48">
          {(showFull ? afterLines : afterLines.slice(0, 6)).map((line, i) => (
            <div key={i} className="code-line code-line-added px-3 py-0.5">
              <span className="code-line-number text-success/50">{i + 1}</span>
              <pre className="whitespace-pre">{line}</pre>
            </div>
          ))}
          {!showFull && afterLines.length > 6 && (
            <div className="px-3 py-1 text-muted-foreground text-center">
              ... {afterLines.length - 6} more lines
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function SuggestionCard({ suggestion }: { suggestion: AIRefactorSuggestion }) {
  const { updateSuggestionStatus } = useDebugStore();
  const [expanded, setExpanded] = useState(false);
  const [showFullDiff, setShowFullDiff] = useState(false);
  const [showGitHubDialog, setShowGitHubDialog] = useState(false);
  const [owner, setOwner] = useState("");
  const [repo, setRepo] = useState("");
  const [filePath, setFilePath] = useState(suggestion.location?.file || "");
  const [fixDescription, setFixDescription] = useState(suggestion.title);
  const [prStatus, setPrStatus] = useState<string|null>(null);
  const [isSyncing, setIsSyncing] = useState(false);

  const isActioned = suggestion.status !== "pending";

  // TODO: Replace with actual API call
  const handleGitHubSync = async () => {
    setIsSyncing(true);
    setPrStatus(null);
    try {
      const res = await api.createGitHubPR({
        owner,
        repo,
        filePath,
        newContent: suggestion.after,
        fixDescription,
      });
      if (res.status === "success") {
        setPrStatus(
          `✅ Pull Request created: #${res.prNumber} ` +
          (res.prUrl ? `[View PR](${res.prUrl})` : "")
        );
        setTimeout(() => setShowGitHubDialog(false), 1200);
      } else {
        setPrStatus(`❌ Failed: ${res.error || "Unknown error"}`);
      }
    } catch (e: any) {
      setPrStatus(`❌ Failed: ${e.message || e.toString()}`);
    } finally {
      setIsSyncing(false);
    }
  };

  return (
    <div 
      className={cn(
        "panel overflow-hidden transition-opacity",
        isActioned && "opacity-60"
      )}
    >
      {/* Header */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-start gap-3 p-4 text-left hover:bg-accent/30 transition-colors"
      >
        <Sparkles className="w-4 h-4 text-ai shrink-0 mt-0.5" />
        
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="font-medium text-sm">{suggestion.title}</span>
            <StatusBadge variant="ai" size="sm">
              {Math.round(suggestion.confidence * 100)}% confident
            </StatusBadge>
            {suggestion.status !== "pending" && (
              <StatusBadge 
                variant={
                  suggestion.status === "applied" ? "success" : 
                  suggestion.status === "rejected" ? "error" : "warning"
                } 
                size="sm"
              >
                {suggestion.status}
              </StatusBadge>
            )}
          </div>
          <p className="text-xs text-muted-foreground">
            {suggestion.description}
          </p>
          <div className="text-[10px] font-mono text-muted-foreground mt-1">
            {suggestion.location.file.split("/").pop()} : {suggestion.location.startLine}-{suggestion.location.endLine}
          </div>
        </div>

        {expanded ? (
          <ChevronUp className="w-4 h-4 text-muted-foreground shrink-0" />
        ) : (
          <ChevronDown className="w-4 h-4 text-muted-foreground shrink-0" />
        )}
      </button>

      {/* Expanded content */}
      {expanded && (
        <div className="border-t border-border">
          {/* Diff view */}
          <div className="p-4">
            <DiffView 
              before={suggestion.before} 
              after={suggestion.after}
              showFull={showFullDiff}
            />
            <button
              onClick={() => setShowFullDiff(!showFullDiff)}
              className="mt-2 text-xs text-muted-foreground hover:text-foreground"
            >
              {showFullDiff ? "Show less" : "Show full diff"}
            </button>
          </div>

          {/* Actions */}
          {suggestion.status === "pending" && (
            <div className="flex items-center gap-2 p-4 pt-0">
              <Button
                size="sm"
                onClick={() => updateSuggestionStatus(suggestion.id, "applied")}
                className="bg-success hover:bg-success/90 text-success-foreground"
              >
                <Check className="w-3.5 h-3.5 mr-1.5" />
                Apply
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => updateSuggestionStatus(suggestion.id, "rejected")}
                className="border-error/50 text-error hover:bg-error-muted"
              >
                <X className="w-3.5 h-3.5 mr-1.5" />
                Reject
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => updateSuggestionStatus(suggestion.id, "edited")}
              >
                <Pencil className="w-3.5 h-3.5 mr-1.5" />
                Edit
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => setShowGitHubDialog(true)}
                className="border-blue-400 text-blue-700 hover:bg-blue-50"
              >
                <Sparkles className="w-3.5 h-3.5 mr-1.5" />
                Sync to GitHub
              </Button>
            </div>
          )}

          {/* GitHub PR Dialog */}
          {showGitHubDialog && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" role="dialog" aria-modal="true">
              <div className="bg-white dark:bg-zinc-900 rounded-lg shadow-lg p-6 w-full max-w-md relative">
                <button
                  className="absolute top-2 right-2 text-lg text-muted-foreground hover:text-foreground"
                  aria-label="Close dialog"
                  onClick={() => setShowGitHubDialog(false)}
                >
                  <X className="w-5 h-5" />
                </button>
                <h3 className="text-lg font-semibold mb-2">Sync Fix to GitHub</h3>
                <div className="space-y-3">
                  <input
                    className="input w-full"
                    placeholder="Owner (e.g. octocat)"
                    value={owner}
                    onChange={e => setOwner(e.target.value)}
                    autoFocus
                  />
                  <input
                    className="input w-full"
                    placeholder="Repository (e.g. my-repo)"
                    value={repo}
                    onChange={e => setRepo(e.target.value)}
                  />
                  <input
                    className="input w-full"
                    placeholder="File Path (e.g. src/main/kotlin/File.kt)"
                    value={filePath}
                    onChange={e => setFilePath(e.target.value)}
                  />
                  <input
                    className="input w-full"
                    placeholder="Fix Description (PR title)"
                    value={fixDescription}
                    onChange={e => setFixDescription(e.target.value)}
                  />
                </div>
                <div className="flex gap-2 mt-4 justify-end">
                  <Button size="sm" variant="outline" onClick={() => setShowGitHubDialog(false)} disabled={isSyncing}>
                    Cancel
                  </Button>
                  <Button size="sm" onClick={handleGitHubSync} disabled={!owner || !repo || !filePath || !fixDescription || isSyncing}>
                    {isSyncing ? (
                      <span className="flex items-center gap-1"><span className="animate-spin inline-block w-4 h-4 border-2 border-blue-400 border-t-transparent rounded-full"></span> Creating...</span>
                    ) : "Create PR"}
                  </Button>
                </div>
                {prStatus && (
                  <div className={
                    "mt-3 text-xs " +
                    (prStatus.startsWith("✅") ? "text-green-600 dark:text-green-400" : "text-red-600 dark:text-red-400")
                  }>
                    {/* Render markdown link if present */}
                    {prStatus.includes('[View PR]') ? (
                      <span dangerouslySetInnerHTML={{ __html: prStatus.replace(/\[View PR\]\((.*?)\)/, '<a href="$1" target="_blank" rel="noopener noreferrer">View PR</a>') }} />
                    ) : (
                      prStatus
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function RefactorPanel() {
  const { suggestions } = useDebugStore();
  const items = suggestions.length > 0 ? suggestions : mockSuggestions;

  const pending = items.filter((s) => s.status === "pending").length;
  const applied = items.filter((s) => s.status === "applied").length;

  return (
    <div className="h-full flex flex-col p-4">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-semibold">AI Refactor Suggestions</h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            {pending} pending • {applied} applied
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Sparkles className="w-4 h-4 text-ai" />
          Powered by static analysis + LLM
        </div>
      </div>

      <div className="flex-1 overflow-auto space-y-3">
        {items.map((suggestion) => (
          <SuggestionCard key={suggestion.id} suggestion={suggestion} />
        ))}
      </div>

      {/* No batch actions: keep UI simple and focused on individual suggestions */}
    </div>
  );
}
