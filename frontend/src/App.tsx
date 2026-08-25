import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { ApiError } from '@/lib/api-client';
import { TooltipProvider } from '@/components/ui/overlay';
import { ToastProvider } from '@/components/ui/toast';
import { AppRoutes } from '@/routes/router';
import { useAuthStore } from '@/stores/auth-store';

/**
 * Query client configuration.
 *
 * Retrying a 4xx is pointless and, for a deploy or rollback, actively harmful, so retries are limited to
 * genuine server and network failures. Auth failures are handled by the API client's silent refresh, not by
 * retrying here.
 */
function createQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache(),
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        gcTime: 5 * 60_000,
        refetchOnWindowFocus: true,
        retry: (failureCount, error) => {
          if (error instanceof ApiError) {
            if (error.status >= 400 && error.status < 500) return false;
          }
          return failureCount < 2;
        },
      },
      mutations: {
        retry: false,
      },
    },
  });
}

export function App() {
  const [queryClient] = useState(createQueryClient);
  const bootstrap = useAuthStore((state) => state.bootstrap);

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <ToastProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </ToastProvider>
      </TooltipProvider>
    </QueryClientProvider>
  );
}
