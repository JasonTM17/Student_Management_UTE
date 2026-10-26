import { ApiClientError, type RegistrationCatalogSection } from '../../api/client';

const CLOSED_CATALOG_CODES = new Set(['WINDOW_CLOSED', 'COHORT_INELIGIBLE', 'ROUND_NOT_FOUND']);

/**
 * One-line summary of the first weekly meeting for the section card; the
 * catalog already serves day/time/room per section so the card must not
 * claim "Schedule pending" while real slots exist.
 */
export function describeSectionSlot(
  section: Pick<RegistrationCatalogSection, 'schedules'>,
): string {
  const schedules = section.schedules ?? [];
  const first = schedules[0];
  if (!first) {
    return 'Schedule pending';
  }
  const parts = [
    first.dayOfWeek ? `Day ${first.dayOfWeek}` : null,
    first.startTime && first.endTime
      ? `${first.startTime} – ${first.endTime}`
      : first.startTime ?? null,
    first.room ?? null,
  ].filter((part): part is string => Boolean(part));
  const base = parts.length > 0 ? parts.join(' · ') : 'Schedule pending';
  return schedules.length > 1 ? `${base} +${schedules.length - 1} more` : base;
}

/**
 * The registration catalog call is the authoritative window gate: the backend
 * only serves sections while an OPEN round is inside its enrollment window,
 * and answers WINDOW_CLOSED otherwise. The rounds list only reports status —
 * a round marked OPEN but outside its window still fails the catalog call, so
 * "round open" must be derived from whether the catalog actually loaded.
 */
export async function loadRegistrationCatalog(
  api: {
    registrationRounds: () => Promise<Array<{ id: string; status: string; kind?: string }>>;
    registrationSections: () => Promise<RegistrationCatalogSection[]>;
  },
): Promise<{ sections: RegistrationCatalogSection[]; roundOpen: boolean }> {
  try {
    const [rounds, catalog] = await Promise.all([
      api.registrationRounds(),
      api.registrationSections(),
    ]);
    return {
      sections: catalog,
      roundOpen: rounds.some((round) => round.status === 'OPEN'),
    };
  } catch (error) {
    if (error instanceof ApiClientError && error.code !== undefined && CLOSED_CATALOG_CODES.has(error.code)) {
      return { sections: [], roundOpen: false };
    }
    throw error;
  }
}
