import { create } from "zustand";
import type {
  Repository,
  Module,
  Diagnostic,
  AIRefactorSuggestion,
  SourceFile,
  Platform,
  ScanResult,
} from "@/types/debug";

interface DebugState {
  // Repository state
  repository: Repository | null;
  isScanning: boolean;
  scanProgress: number;
  
  // Module tree
  modules: Module[];
  selectedModuleId: string | null;
  
  // Source files
  currentFile: SourceFile | null;
  showDiff: boolean;
  
  // Diagnostics
  diagnostics: Diagnostic[];
  selectedDiagnosticId: string | null;
  
  // AI Suggestions
  suggestions: AIRefactorSuggestion[];
  selectedSuggestionId: string | null;
  
  
  // View state
  activeView: "workspace" | "preview" | "refactor";
  
  // Actions
  setRepository: (repo: Repository) => void;
  startScan: () => void;
  setScanProgress: (progress: number) => void;
  setScanResult: (result: ScanResult) => void;
  selectModule: (id: string) => void;
  setCurrentFile: (file: SourceFile) => void;
  toggleDiff: () => void;
  selectDiagnostic: (id: string) => void;
  selectSuggestion: (id: string) => void;
  updateSuggestionStatus: (id: string, status: AIRefactorSuggestion["status"]) => void;
  setActiveView: (view: "workspace" | "preview" | "refactor") => void;
  reset: () => void;
}

const initialState = {
  repository: null,
  isScanning: false,
  scanProgress: 0,
  modules: [],
  selectedModuleId: null,
  currentFile: null,
  showDiff: false,
  diagnostics: [],
  selectedDiagnosticId: null,
  suggestions: [],
  selectedSuggestionId: null,
  activeView: "workspace" as const,
};

export const useDebugStore = create<DebugState>((set) => ({
  ...initialState,
  
  setRepository: (repo) => set({ repository: repo }),
  
  startScan: () => set({ isScanning: true, scanProgress: 0 }),
  
  setScanProgress: (progress) => set({ scanProgress: progress }),
  
  setScanResult: (result) =>
    set({
      isScanning: false,
      scanProgress: 100,
      modules: result.modules,
      diagnostics: result.diagnostics,
      suggestions: result.suggestions,
    }),
  
  selectModule: (id) => set({ selectedModuleId: id }),
  
  setCurrentFile: (file) => set({ currentFile: file }),
  
  toggleDiff: () => set((state) => ({ showDiff: !state.showDiff })),
  
  selectDiagnostic: (id) => set({ selectedDiagnosticId: id }),
  
  selectSuggestion: (id) => set({ selectedSuggestionId: id }),
  
  updateSuggestionStatus: (id, status) =>
    set((state) => ({
      suggestions: state.suggestions.map((s) =>
        s.id === id ? { ...s, status } : s
      ),
    })),
  
  setActiveView: (view) => set({ activeView: view }),
  
  reset: () => set(initialState),
}));
