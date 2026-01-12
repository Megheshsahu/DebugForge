// GitHub PR creation request/response
export interface GitHubPRRequest {
  owner: string;
  repo: string;
  filePath: string;
  newContent: string;
  fixDescription: string;
}

export interface GitHubPRResponse {
  status: 'success' | 'error';
  prNumber?: number;
  prUrl?: string;
  branch?: string;
  error?: string;
}
/**
 * API Service Layer for DebugForge
 * Connects frontend to backend at port 8765
 */

const API_BASE = 'http://127.0.0.1:8765';

export interface ApiError {
  code: string;
  message: string;
}

export interface HealthResponse {
  status: string;
  version: string;
}

export interface AppState {
  loadedRepos: RepoInfo[];
  activeRepoId: string | null;
  isAnalyzing: boolean;
  analysisProgress: number;
  error: string | null;
}

export interface RepoInfo {
  id: string;
  path: string;
  name: string;
  gitInfo: GitInfo | null;
  modules: ModuleInfo[];
  buildSystem: string;
  lastAnalyzed: number | null;
}

export interface GitInfo {
  branch: string;
  commitHash: string;
  remoteUrl: string | null;
  isDirty: boolean;
}

export interface ModuleInfo {
  id: string;
  name: string;
  path: string;
  type: string;
  targets: string[];
  dependencies: string[];
}

export interface DiagnosticLocation {
  filePath: string;
  relativeFilePath: string;
  moduleId: string;
  sourceSet: string;
  startLine: number;
  startColumn: number;
  endLine: number;
  endColumn: number;
  sourceSnippet?: string;
}

export interface DiagnosticFix {
  title: string;
  description: string;
  edits: TextEdit[];
  isPreferred: boolean;
  confidence: number;
}

export interface TextEdit {
  filePath: string;
  range: { startLine: number; startColumn: number; endLine: number; endColumn: number };
  newText: string;
}

export interface DiagnosticResponse {
  id: string;
  severity: 'ERROR' | 'WARNING' | 'INFO' | 'HINT';
  category: string;
  message: string;
  explanation: string;
  location: DiagnosticLocation;
  relatedLocations: DiagnosticLocation[];
  fixes: DiagnosticFix[];
  source: string;
  timestamp: number;
  isActive: boolean;
  tags: string[];
}

// Helper to extract flat fields for compatibility
export function flattenDiagnostic(d: DiagnosticResponse): {
  id: string;
  severity: string;
  category: string;
  message: string;
  filePath: string;
  line: number;
  column: number;
  source: string;
  fixes: DiagnosticFix[];
  tags: string[];
} {
  return {
    id: d.id,
    severity: d.severity,
    category: d.category,
    message: d.message,
    filePath: d.location.filePath,
    line: d.location.startLine,
    column: d.location.startColumn,
    source: d.source,
    fixes: d.fixes,
    tags: d.tags,
  };
}

export interface RefactorSuggestion {
  id: string;
  title: string;
  rationale: string;
  confidence: number;
  category: string;
  priority: string;
  unifiedDiff: string;
  changes: FileChange[];
  affectedLocations: DiagnosticLocation[];
  fixesDiagnosticIds: string[];
  sharedCodeImpact: number | null;
  isAutoApplicable: boolean;
  risks: RefactorRisk[];
  source: string;
  generatedAt: number;
}

export interface FileChange {
  filePath: string;
  changeType: 'CREATE' | 'MODIFY' | 'DELETE' | 'RENAME';
  hunks: DiffHunk[];
  oldPath?: string;
}

export interface DiffHunk {
  oldStart: number;
  oldCount: number;
  newStart: number;
  newCount: number;
  lines: DiffLine[];
}

export interface DiffLine {
  type: 'CONTEXT' | 'ADD' | 'REMOVE';
  content: string;
  oldLineNumber?: number;
  newLineNumber?: number;
}

export interface RefactorRisk {
  level: 'LOW' | 'MEDIUM' | 'HIGH';
  description: string;
  mitigation?: string;
}

export interface LoadRepoRequest {
  path: string;
}

export interface CloneRepoRequest {
  url: string;
  targetPath: string;
  branch?: string;
}

class DebugForgeApi {

    // Create a GitHub PR for a code change
    async createGitHubPR(data: GitHubPRRequest): Promise<GitHubPRResponse> {
      return this.fetch<GitHubPRResponse>(`/api/github/sync`, {
        method: 'POST',
        body: JSON.stringify(data),
      });
    }
  private baseUrl: string;

  constructor(baseUrl: string = API_BASE) {
    this.baseUrl = baseUrl;
  }

  private async fetch<T>(endpoint: string, options?: RequestInit): Promise<T> {
    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ 
        code: 'UNKNOWN', 
        message: response.statusText 
      }));
      throw new Error(error.message || 'API request failed');
    }

    return response.json();
  }

  // Health check
  async health(): Promise<HealthResponse> {
    return this.fetch<HealthResponse>('/health');
  }

  // Get current application state
  async getState(): Promise<AppState> {
    return this.fetch<AppState>('/api/state');
  }

  // Load a local repository
  async loadRepo(path: string): Promise<RepoInfo> {
    return this.fetch<RepoInfo>('/api/repo/load', {
      method: 'POST',
      body: JSON.stringify({ path }),
    });
  }

  // Clone a GitHub repository
  async cloneRepo(url: string, targetPath: string, branch?: string): Promise<RepoInfo> {
    return this.fetch<RepoInfo>('/api/repo/clone', {
      method: 'POST',
      body: JSON.stringify({ url, targetPath, branch }),
    });
  }

  // Refresh/rescan current repository
  async refreshRepo(): Promise<void> {
    return this.fetch<void>('/api/repo/refresh', {
      method: 'POST',
    });
  }

  // Get all modules
  async getModules(): Promise<ModuleInfo[]> {
    return this.fetch<ModuleInfo[]>('/api/modules');
  }

  // Get diagnostics with optional filters
  async getDiagnostics(filters?: {
    severity?: string[];
    category?: string[];
    module?: string;
  }): Promise<DiagnosticResponse[]> {
    const params = new URLSearchParams();
    if (filters?.severity) params.set('severity', filters.severity.join(','));
    if (filters?.category) params.set('category', filters.category.join(','));
    if (filters?.module) params.set('module', filters.module);
    
    const query = params.toString();
    return this.fetch<DiagnosticResponse[]>(`/api/diagnostics${query ? `?${query}` : ''}`);
  }

  // Suppress a diagnostic
  async suppressDiagnostic(id: string): Promise<void> {
    return this.fetch<void>(`/api/diagnostics/${id}/suppress`, {
      method: 'POST',
    });
  }

  // Get refactoring suggestions
  async getRefactors(): Promise<RefactorSuggestion[]> {
    return this.fetch<RefactorSuggestion[]>('/api/refactors');
  }

  // Apply a refactoring suggestion
  async applyRefactor(id: string): Promise<{ success: boolean; appliedFiles: string[] }> {
    return this.fetch<{ success: boolean; appliedFiles: string[] }>(`/api/refactors/${id}/apply`, {
      method: 'POST',
    });
  }

  // Dismiss a refactoring suggestion
  async dismissRefactor(id: string): Promise<void> {
    return this.fetch<void>(`/api/refactors/${id}/dismiss`, {
      method: 'POST',
    });
  }

  // Get metrics
  async getMetrics(): Promise<{
    totalFiles: number;
    sharedCodePercent: number;
    diagnosticsCount: { error: number; warning: number; info: number };
  }> {
    return this.fetch('/api/metrics');
  }

  // Get preview configurations
  async getPreviews(): Promise<Array<{
    id: string;
    platform: string;
    status: string;
  }>> {
    return this.fetch('/api/previews');
  }

  // Start a preview
  async startPreview(id: string): Promise<{ sessionId: string }> {
    return this.fetch<{ sessionId: string }>(`/api/previews/${id}/start`, {
      method: 'POST',
    });
  }

  // Stop a preview
  async stopPreview(sessionId: string): Promise<void> {
    return this.fetch<void>(`/api/previews/${sessionId}/stop`, {
      method: 'POST',
    });
  }

  // Report file changes (for live updates)
  async reportFileChanges(paths: string[]): Promise<void> {
    return this.fetch<void>('/api/files/changed', {
      method: 'POST',
      body: JSON.stringify({ paths }),
    });
  }

  // Clear errors
  async clearErrors(): Promise<void> {
    return this.fetch<void>('/api/error/clear', {
      method: 'POST',
    });
  }

  // WebSocket connection for real-time diagnostics
  connectDiagnosticsStream(onEvent: (event: DiagnosticEvent) => void): WebSocket {
    const ws = new WebSocket(`ws://127.0.0.1:8765/ws/diagnostics`);
    
    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        onEvent(data);
      } catch (e) {
        console.error('Failed to parse diagnostic event:', e);
      }
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    return ws;
  }

  // SSE connection for analysis progress
  connectAnalysisStream(onProgress: (progress: AnalysisProgress) => void): EventSource {
    const es = new EventSource(`${this.baseUrl}/sse/analysis`);
    
    es.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        onProgress(data);
      } catch (e) {
        console.error('Failed to parse analysis event:', e);
      }
    };

    es.onerror = (error) => {
      console.error('SSE error:', error);
    };

    return es;
  }
}

export interface DiagnosticEvent {
  type: 'Added' | 'Resolved' | 'Dismissed' | 'FileClear' | 'Progress';
  diagnostic?: DiagnosticResponse;
  diagnosticId?: string;
  filePath?: string;
  progress?: AnalysisProgress;
}

export interface AnalysisProgress {
  phase: string;
  currentFile: string | null;
  filesProcessed: number;
  totalFiles: number;
  diagnosticsFound: number;
}

// Export singleton instance
export const api = new DebugForgeApi();

// Export class for testing/custom instances
export { DebugForgeApi };
