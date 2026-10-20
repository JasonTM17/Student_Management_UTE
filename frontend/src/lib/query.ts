'use client';

import {
  QueryClient,
  useQuery,
  type QueryKey,
  type UseQueryOptions,
  type UseQueryResult,
} from '@tanstack/react-query';

export const STATIC_STALE_TIME_MS = 10 * 60_000;
export const DEFAULT_STALE_TIME_MS = 5 * 60_000;

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: DEFAULT_STALE_TIME_MS,
        refetchOnWindowFocus: false,
        retry: 1,
      },
    },
  });
}

// One QueryClient per browser tab: several query keys carry no user id, so
// AuthContext drops this singleton on every identity change to keep the next
// signed-in user from reading the previous user's cached data. Server renders
// still get a fresh client per call — SSR must never share cache state
// between requests.
let browserQueryClient: QueryClient | undefined;

export function getBrowserQueryClient(): QueryClient {
  if (typeof window === 'undefined') {
    return createQueryClient();
  }
  if (!browserQueryClient) {
    browserQueryClient = createQueryClient();
  }
  return browserQueryClient;
}

export function useApiQuery<TData>(
  queryKey: QueryKey,
  queryFn: () => Promise<TData>,
  options?: Omit<UseQueryOptions<TData, Error, TData, QueryKey>, 'queryKey' | 'queryFn'>,
): UseQueryResult<TData, Error> {
  return useQuery<TData, Error, TData, QueryKey>({
    queryKey,
    queryFn,
    ...options,
  });
}
