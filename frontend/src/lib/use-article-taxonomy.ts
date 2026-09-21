'use client';

import { useQuery } from '@tanstack/react-query';

import { articleTaxonomyApi } from '@/lib/api';
import type { TaxonomyCategory } from '@/lib/announcement-presentation';

const TAXONOMY_STALE_TIME_MS = 10 * 60_000;

/**
 * Live editorial-category feed used to label announcements from their real
 * `categoryId` instead of client-side keyword guesses. Long stale time: the
 * category table changes at editorial speed, not per session.
 */
export function useArticleTaxonomy() {
  const query = useQuery<TaxonomyCategory[], Error>({
    queryKey: ['article-taxonomy', 'categories'],
    queryFn: articleTaxonomyApi.getPublicCategories,
    staleTime: TAXONOMY_STALE_TIME_MS,
    retry: false,
    refetchOnWindowFocus: false,
  });

  return {
    categories: query.data ?? ([] as TaxonomyCategory[]),
    isLoading: query.isLoading,
  };
}
