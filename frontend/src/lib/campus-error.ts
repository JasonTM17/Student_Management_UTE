export type CampusErrorKind =
  | 'network'
  | 'validation'
  | 'conflict'
  | 'unauthorized'
  | 'forbidden'
  | 'notFound'
  | 'server'
  | 'unknown';

export type CampusErrorCopy = Record<CampusErrorKind, string> & {
  /** Optional backend `code` → specific copy, preferred over the kind copy. */
  codes?: Record<string, string>;
};

type AxiosLike = {
  code?: unknown;
  response?: {
    status?: unknown;
    data?: unknown;
  };
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function axiosLike(error: unknown): AxiosLike | null {
  if (!isRecord(error)) {
    return null;
  }

  const response = isRecord(error.response) ? error.response : undefined;
  return {
    code: error.code,
    response: response
      ? { status: response.status, data: response.data }
      : undefined,
  };
}

function statusOf(error: AxiosLike): number | undefined {
  const status = error.response?.status;
  return typeof status === 'number' ? status : undefined;
}

export function campusErrorCode(error: unknown): string | undefined {
  const shaped = axiosLike(error);
  const data = shaped?.response?.data;
  if (isRecord(data) && typeof data.code === 'string' && data.code.trim()) {
    return data.code;
  }
  return undefined;
}

export function campusErrorKind(error: unknown): CampusErrorKind {
  const shaped = axiosLike(error);
  if (!shaped) {
    return 'unknown';
  }

  if (!shaped.response) {
    return 'network';
  }

  const status = statusOf(shaped);
  if (status === 400 || status === 422) {
    return 'validation';
  }
  if (status === 401) {
    return 'unauthorized';
  }
  if (status === 403) {
    return 'forbidden';
  }
  if (status === 404) {
    return 'notFound';
  }
  if (status === 409) {
    return 'conflict';
  }
  if (typeof status === 'number' && status >= 500) {
    return 'server';
  }

  return 'unknown';
}

export function campusErrorMessage(
  error: unknown,
  copy: CampusErrorCopy,
  fallback?: string,
): string {
  const kind = campusErrorKind(error);
  return copy[kind] || fallback || copy.unknown;
}

/**
 * `details` values the backend sends with a business code fill `{key}`
 * placeholders in the localized copy (e.g. `opensAt` on
 * REGISTRATION_WINDOW_NOT_OPEN). An unresolved placeholder rejects the
 * template so the caller can fall back to the backend's own message, which
 * still names the instant rather than showing raw braces.
 */
function interpolateDetails(template: string, error: unknown): string | null {
  const data = axiosLike(error)?.response?.data;
  const details = isRecord(data) && isRecord(data.details) ? data.details : undefined;
  if (!/\{\w+\}/.test(template)) return template;
  if (!details) return null;
  let resolved = true;
  const text = template.replace(/\{(\w+)\}/g, (match, key: string) => {
    if (typeof details[key] === 'string' || typeof details[key] === 'number') {
      return String(details[key]);
    }
    resolved = false;
    return match;
  });
  return resolved ? text : null;
}

/**
 * Prefer the backend business code (`SCHEDULE_CONFLICT`, `SECTION_FULL`, …)
 * over the generic HTTP-kind copy so students learn why a change failed.
 */
export function campusCodeMessage(
  error: unknown,
  copy: CampusErrorCopy,
  fallback?: string,
): string {
  const code = campusErrorCode(error);
  const specific = code ? copy.codes?.[code] : undefined;
  if (specific) {
    const resolved = interpolateDetails(specific, error);
    if (resolved) return resolved;
    const data = axiosLike(error)?.response?.data;
    if (isRecord(data) && typeof data.message === 'string' && data.message.trim()) {
      return data.message;
    }
  }
  return campusErrorMessage(error, copy, fallback);
}
