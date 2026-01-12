/**
 * React hooks for DebugForge API integration
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, type DiagnosticResponse, type RefactorSuggestion, type RepoInfo, type ModuleInfo } from '@/services/api';

// Query keys
export const queryKeys = {
  health: ['health'] as const,
  state: ['state'] as const,
  modules: ['modules'] as const,
  diagnostics: (filters?: Record<string, unknown>) => ['diagnostics', filters] as const,
  refactors: ['refactors'] as const,
  metrics: ['metrics'] as const,
  previews: ['previews'] as const,
};

// Health check hook
export function useHealth() {
  return useQuery({
    queryKey: queryKeys.health,
    queryFn: () => api.health(),
    retry: 3,
    refetchInterval: 30000, // Check every 30s
  });
}

// App state hook
export function useAppState() {
  return useQuery({
    queryKey: queryKeys.state,
    queryFn: () => api.getState(),
    refetchInterval: 5000, // Refresh every 5s
  });
}

// Modules hook
export function useModules() {
  return useQuery({
    queryKey: queryKeys.modules,
    queryFn: () => api.getModules(),
  });
}

// Diagnostics hook
export function useDiagnostics(filters?: {
  severity?: string[];
  category?: string[];
  module?: string;
}) {
  return useQuery({
    queryKey: queryKeys.diagnostics(filters),
    queryFn: () => api.getDiagnostics(filters),
    refetchInterval: 10000, // Refresh every 10s
  });
}

// Refactors hook
export function useRefactors() {
  return useQuery({
    queryKey: queryKeys.refactors,
    queryFn: () => api.getRefactors(),
  });
}

// Metrics hook
export function useMetrics() {
  return useQuery({
    queryKey: queryKeys.metrics,
    queryFn: () => api.getMetrics(),
    refetchInterval: 15000,
  });
}

// Previews hook
export function usePreviews() {
  return useQuery({
    queryKey: queryKeys.previews,
    queryFn: () => api.getPreviews(),
  });
}

// Load repo mutation
export function useLoadRepo() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: (path: string) => api.loadRepo(path),
    onSuccess: () => {
      // Invalidate all queries to refresh data
      queryClient.invalidateQueries({ queryKey: queryKeys.state });
      queryClient.invalidateQueries({ queryKey: queryKeys.modules });
      queryClient.invalidateQueries({ queryKey: queryKeys.diagnostics() });
      queryClient.invalidateQueries({ queryKey: queryKeys.refactors });
      queryClient.invalidateQueries({ queryKey: queryKeys.metrics });
    },
  });
}

// Clone repo mutation
export function useCloneRepo() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: ({ url, targetPath, branch }: { url: string; targetPath: string; branch?: string }) =>
      api.cloneRepo(url, targetPath, branch),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.state });
      queryClient.invalidateQueries({ queryKey: queryKeys.modules });
    },
  });
}

// Refresh repo mutation
export function useRefreshRepo() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: () => api.refreshRepo(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.diagnostics() });
      queryClient.invalidateQueries({ queryKey: queryKeys.refactors });
      queryClient.invalidateQueries({ queryKey: queryKeys.metrics });
    },
  });
}

// Suppress diagnostic mutation
export function useSuppressDiagnostic() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: (id: string) => api.suppressDiagnostic(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.diagnostics() });
    },
  });
}

// Apply refactor mutation
export function useApplyRefactor() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: (id: string) => api.applyRefactor(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.refactors });
      queryClient.invalidateQueries({ queryKey: queryKeys.diagnostics() });
    },
  });
}

// Dismiss refactor mutation
export function useDismissRefactor() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: (id: string) => api.dismissRefactor(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.refactors });
    },
  });
}

// Start preview mutation
export function useStartPreview() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: (id: string) => api.startPreview(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.previews });
    },
  });
}

// Stop preview mutation
export function useStopPreview() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: (sessionId: string) => api.stopPreview(sessionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.previews });
    },
  });
}
