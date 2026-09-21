export type ReorderBlockReason = 'filtered' | 'paged';

export interface ReorderGuard {
  allowed: boolean;
  blockedBy: ReorderBlockReason[];
}

/**
 * Reordering writes each row's position as its global `displayOrder`, and the
 * server sorts the whole feed by that column. That is only honest while the
 * dialog is looking at the feed itself: page 1 of an unfiltered list. Under a
 * filter, or from page 2, the indexes on screen belong to a subset, so saving
 * them would silently re-rank rows the admin never saw — including rows another
 * admin just placed. Rather than guess a global offset, the affordance is
 * switched off and the admin is told what to clear. Default-deny: an unknown
 * status counts as a filter.
 */
export function canReorder(input: {
  page: number;
  filters: { semesterId: string; priority: string; status: string };
}): ReorderGuard {
  const blockedBy: ReorderBlockReason[] = [];
  const filtered =
    Boolean(input.filters?.semesterId?.trim()) ||
    Boolean(input.filters?.priority?.trim()) ||
    (input.filters?.status ?? '') !== 'ALL';
  if (filtered) blockedBy.push('filtered');
  if (input.page !== 1) blockedBy.push('paged');
  return { allowed: blockedBy.length === 0, blockedBy };
}

export interface DisplayOrderWriteSummary {
  attempted: number;
  failed: number;
  allSucceeded: boolean;
}

/**
 * Settled-write accounting for the reorder save path.
 *
 * `Promise.all(...map(req => req.catch(...)))` swallows every rejection, so a
 * half-written order looked identical to a saved one. Counting `allSettled`
 * results keeps "every row saved" distinguishable from "some rows did not".
 */
export function summarizeDisplayOrderWrites(
  results: ReadonlyArray<PromiseSettledResult<unknown>>,
): DisplayOrderWriteSummary {
  let failed = 0;
  for (const result of results) {
    if (result.status === 'rejected') failed += 1;
  }
  return { attempted: results.length, failed, allSucceeded: failed === 0 };
}
