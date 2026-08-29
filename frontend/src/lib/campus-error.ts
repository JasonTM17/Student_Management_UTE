export type CampusErrorKind =
  | 'network'
  | 'validation'
  | 'conflict'
  | 'unauthorized'
  | 'forbidden'
  | 'notFound'
  | 'server'
  | 'unknown';

export type CampusErrorCopy = Record<CampusErrorKind, string>;

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
