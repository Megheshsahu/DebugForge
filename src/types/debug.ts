export type Platform = "android" | "ios" | "desktop" | "web";

export type DiagnosticSeverity = "error" | "warning" | "info";

export interface Repository {
  url: string;
  name: string;
  owner: string;
  branch: string;
  targets: Platform[];
  lastCommit?: string;
  stars?: number;
}

export interface Module {
  id: string;
  name: string;
  path: string;
  sharedCodePercent: number;
  expectCount: number;
  actualCount: number;
  coverage: number;
  children?: Module[];
  hasErrors?: boolean;
  hasWarnings?: boolean;
}

export interface CodeAnnotation {
  line: number;
  type: "error" | "warning" | "ai-suggestion" | "info";
  message: string;
  suggestion?: string;
}

export interface SourceFile {
  path: string;
  language: string;
  content: string;
  annotations: CodeAnnotation[];
}

export interface Diagnostic {
  id: string;
  severity: DiagnosticSeverity;
  category: "coroutine" | "wasm" | "platform-api" | "memory" | "threading";
  title: string;
  description: string;
  location: {
    file: string;
    line: number;
    column?: number;
  };
  platforms: Platform[];
}

export interface AIRefactorSuggestion {
  id: string;
  title: string;
  description: string;
  confidence: number;
  before: string;
  after: string;
  location: {
    file: string;
    startLine: number;
    endLine: number;
  };
  status: "pending" | "applied" | "rejected" | "edited";
}

export interface PreviewState {
  platform: Platform;
  status: "loading" | "ready" | "error";
  errors?: string[];
  highlightedComponent?: string;
}

export interface ScanResult {
  modules: Module[];
  diagnostics: Diagnostic[];
  suggestions: AIRefactorSuggestion[];
  stats: {
    totalFiles: number;
    sharedFiles: number;
    platformSpecificFiles: number;
    errorsCount: number;
    warningsCount: number;
    suggestionsCount: number;
  };
}
