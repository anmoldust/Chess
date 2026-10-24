import type { ApiError } from '../api/client';

export function errorMessage(error: unknown): string {
  if (isApiError(error)) {
    return error.detail || error.title;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function isApiError(error: unknown): error is InstanceType<typeof ApiError> {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    'title' in error &&
    'detail' in error
  );
}
