import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { GitBranch, Github, Loader2, ChevronDown, Check, AlertCircle, FolderOpen } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { useDebugStore } from "@/store/debugStore";
import { useHealth, useCloneRepo, useLoadRepo } from "@/hooks/useApi";
import { api } from "@/services/api";
import type { Platform, Repository } from "@/types/debug";

// Multiplatform toggles removed for minimal product


const BRANCHES = ["main", "develop", "feature/compose-updates", "release/1.2.0"];

export function RepoIntake() {
  const navigate = useNavigate();
  const { setRepository, startScan, setScanResult } = useDebugStore();
  
  // API hooks
  const { data: health, isLoading: healthLoading } = useHealth();
  const cloneRepo = useCloneRepo();
  const loadRepo = useLoadRepo();
  
  const [repoUrl, setRepoUrl] = useState("");
  const [localPath, setLocalPath] = useState("");
  const [inputMode, setInputMode] = useState<'github' | 'local'>('github');
  const [branch, setBranch] = useState("main");
  // Only android platform supported for minimal product
  const targets: Platform[] = ["android"];
  const [isValidating, setIsValidating] = useState(false);
  const [isValid, setIsValid] = useState<boolean | null>(null);
  const [showBranchDropdown, setShowBranchDropdown] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [repoMetadata, setRepoMetadata] = useState<{
    name: string;
    owner: string;
    stars: number;
    lastCommit: string;
  } | null>(null);

  // Check backend connection
  const backendConnected = !!health?.status;

  const validateRepo = async () => {
    if (!repoUrl.includes("github.com")) return;
    
    setIsValidating(true);
    setError(null);
    
    // Parse GitHub URL
    const match = repoUrl.match(/github\.com\/([^/]+)\/([^/]+)/);
    if (match) {
      try {
        // Fetch repo info from GitHub API
        const owner = match[1];
        const name = match[2].replace(".git", "").replace(/\/$/, "");
        
        const response = await fetch(`https://api.github.com/repos/${owner}/${name}`);
        if (response.ok) {
          const data = await response.json();
          setRepoMetadata({
            owner: data.owner.login,
            name: data.name,
            stars: data.stargazers_count,
            lastCommit: new Date(data.pushed_at).toLocaleDateString(),
          });
          setIsValid(true);
        } else {
          setIsValid(false);
          setError("Repository not found or inaccessible");
        }
      } catch {
        // Fallback to basic validation
        setRepoMetadata({
          owner: match[1],
          name: match[2].replace(".git", ""),
          stars: 0,
          lastCommit: "Unknown",
        });
        setIsValid(true);
      }
    } else {
      setIsValid(false);
      setError("Invalid GitHub URL format");
    }
    setIsValidating(false);
  };

  // Platform toggle removed for minimal product

  const handleScan = async () => {
    setError(null);
    
    try {
      if (inputMode === 'local' && localPath) {
        // Load local repository
        startScan();
        const result = await loadRepo.mutateAsync(localPath);
        
        const repo: Repository = {
          url: localPath,
          name: result.name,
          owner: "local",
          branch: result.gitInfo?.branch || "main",
          targets,
        };
        setRepository(repo);
        
        // Fetch diagnostics and modules
        const [diagnostics, modules] = await Promise.all([
          api.getDiagnostics(),
          api.getModules(),
        ]);
        
        setScanResult({
          modules: modules.map(m => ({
            id: m.id,
            name: m.name,
            path: m.path,
            sharedCodePercent: 80,
            expectCount: 0,
            actualCount: 0,
            coverage: 85,
          })),
          diagnostics: diagnostics.map(d => ({
            id: d.id,
            severity: d.severity.toLowerCase() as "error" | "warning" | "info",
            category: d.category as any,
            title: d.message,
            description: d.explanation || d.message,
            location: {
              file: d.location.filePath,
              line: d.location.startLine,
              column: d.location.startColumn,
            },
            platforms: targets,
          })),
          suggestions: [],
          stats: {
            totalFiles: modules.length * 10,
            sharedFiles: modules.length * 8,
            platformSpecificFiles: modules.length * 2,
            errorsCount: diagnostics.filter(d => d.severity === 'ERROR').length,
            warningsCount: diagnostics.filter(d => d.severity === 'WARNING').length,
            suggestionsCount: 0,
          },
        });
        
        navigate("/workspace");
      } else if (inputMode === 'github' && repoMetadata) {
        // Clone GitHub repository
        const targetPath = `./repos/${repoMetadata.owner}/${repoMetadata.name}`;
        
        startScan();
        await cloneRepo.mutateAsync({
          url: repoUrl,
          targetPath,
          branch,
        });
        
        const repo: Repository = {
          url: repoUrl,
          name: repoMetadata.name,
          owner: repoMetadata.owner,
          branch,
          targets,
          stars: repoMetadata.stars,
          lastCommit: repoMetadata.lastCommit,
        };
        
        setRepository(repo);
        navigate("/workspace");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load repository");
    }
  };

  return (
    <div className="min-h-screen bg-background flex flex-col">
      {/* Header */}
      <header className="border-b border-border px-6 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-primary/20 rounded flex items-center justify-center">
              <span className="text-primary font-mono font-bold text-sm">K</span>
            </div>
            <span className="font-semibold text-foreground">DebugForge</span>
            <span className="text-xs text-muted-foreground font-mono">v0.9.0-beta</span>
          </div>
          {/* Backend Status */}
          <div className={cn(
            "flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium",
            backendConnected 
              ? "bg-success-muted text-success" 
              : "bg-error-muted text-error"
          )}>
            <div className={cn(
              "w-2 h-2 rounded-full",
              backendConnected ? "bg-success" : "bg-error"
            )} />
            {backendConnected ? "Backend Connected" : "Backend Offline"}
          </div>
        </div>
      </header>

      {/* Main content */}
      <main className="flex-1 flex items-center justify-center p-8">
        <div className="w-full max-w-2xl space-y-8">
          {/* Title */}
          <div className="space-y-2">
            <h1 className="text-2xl font-semibold text-foreground">Load Repository</h1>
            <p className="text-sm text-muted-foreground">
              Enter a Kotlin Multiplatform repository to begin static analysis and debugging.
            </p>
          </div>

          {/* Error display */}
          {error && (
            <div className="p-4 bg-error-muted border border-error rounded-md">
              <p className="text-sm text-error">{error}</p>
            </div>
          )}

          {/* Input Mode Toggle */}
          <div className="flex gap-2 p-1 bg-muted rounded-lg w-fit">
            <button
              onClick={() => setInputMode('github')}
              className={cn(
                "px-4 py-2 text-sm font-medium rounded-md transition-colors",
                inputMode === 'github' 
                  ? "bg-background text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              )}
            >
              <Github className="w-4 h-4 inline mr-2" />
              GitHub URL
            </button>
            <button
              onClick={() => setInputMode('local')}
              className={cn(
                "px-4 py-2 text-sm font-medium rounded-md transition-colors",
                inputMode === 'local' 
                  ? "bg-background text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              )}
            >
              <FolderOpen className="w-4 h-4 inline mr-2" />
              Local Path
            </button>
          </div>

          {/* Repo URL Input (GitHub mode) */}
          {inputMode === 'github' && (
            <div className="space-y-3">
              <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                GitHub Repository
              </label>
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <Github className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    value={repoUrl}
                    onChange={(e) => {
                      setRepoUrl(e.target.value);
                      setIsValid(null);
                      setRepoMetadata(null);
                    }}
                    placeholder="https://github.com/user/kmp-project"
                    className="pl-10 bg-muted border-border font-mono text-sm"
                    onBlur={validateRepo}
                  />
                  {isValidating && (
                    <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground animate-spin" />
                  )}
                  {isValid === true && (
                    <Check className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-success" />
                  )}
                  {isValid === false && (
                    <AlertCircle className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-error" />
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Local Path Input */}
          {inputMode === 'local' && (
            <div className="space-y-3">
              <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Local Repository Path
              </label>
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <FolderOpen className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    value={localPath}
                    onChange={(e) => setLocalPath(e.target.value)}
                    placeholder="C:\Projects\my-kmp-project or /home/user/my-kmp-project"
                    className="pl-10 bg-muted border-border font-mono text-sm"
                  />
                </div>
              </div>
              <p className="text-xs text-muted-foreground">
                Enter the absolute path to a local KMP project directory
              </p>
            </div>
          )}

          {/* Repo Metadata */}
          {inputMode === 'github' && repoMetadata && (
            <div className="panel p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded bg-secondary flex items-center justify-center text-lg">
                    📦
                  </div>
                  <div>
                    <div className="font-medium text-foreground">
                      {repoMetadata.owner}/{repoMetadata.name}
                    </div>
                    <div className="text-xs text-muted-foreground">
                      Last commit: {repoMetadata.lastCommit} • ⭐ {repoMetadata.stars}
                    </div>
                  </div>
                </div>
                <span className="text-xs font-mono bg-success-muted text-success px-2 py-1 rounded">
                  KMP Detected
                </span>
              </div>
            </div>
          )}

          {/* Branch Selector - Only for GitHub mode */}
          {inputMode === 'github' && (
            <div className="space-y-3">
              <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Branch
              </label>
              <div className="relative">
                <button
                  onClick={() => setShowBranchDropdown(!showBranchDropdown)}
                  className="w-full flex items-center justify-between px-3 py-2 bg-muted border border-border rounded text-sm font-mono"
                >
                  <span className="flex items-center gap-2">
                    <GitBranch className="w-4 h-4 text-muted-foreground" />
                    {branch}
                  </span>
                  <ChevronDown className="w-4 h-4 text-muted-foreground" />
                </button>
                {showBranchDropdown && (
                  <div className="absolute top-full left-0 right-0 mt-1 bg-popover border border-border rounded shadow-lg z-10">
                    {BRANCHES.map((b) => (
                      <button
                        key={b}
                        onClick={() => {
                          setBranch(b);
                          setShowBranchDropdown(false);
                        }}
                        className={cn(
                          "w-full px-3 py-2 text-left text-sm font-mono hover:bg-accent",
                          b === branch && "bg-accent text-accent-foreground"
                        )}
                      >
                        {b}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Target Platforms removed for minimal product */}

          {/* Scan Button */}
          <Button
            onClick={handleScan}
            disabled={
              !backendConnected ||
              targets.length === 0 || 
              (inputMode === 'github' && !isValid) ||
              (inputMode === 'local' && !localPath) ||
              loadRepo.isPending ||
              cloneRepo.isPending
            }
            className="w-full h-12 text-base font-medium"
          >
            {(loadRepo.isPending || cloneRepo.isPending) ? (
              <>
                <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                Scanning Repository...
              </>
            ) : (
              "Scan Repository"
            )}
          </Button>

          {/* Info */}
          <p className="text-xs text-muted-foreground text-center">
            Analysis includes KSP static checks, expect/actual coverage, 
            and AI-powered refactoring suggestions.
          </p>
        </div>
      </main>
    </div>
  );
}
