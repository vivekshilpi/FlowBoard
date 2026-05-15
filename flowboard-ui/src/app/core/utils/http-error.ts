export function getApiErrorMessage(error: unknown, fallback: string): string {
  const err = error as {
    status?: number;
    error?: { message?: string; error?: string } | string | null;
    message?: string;
  };

  if (typeof err?.error === 'string' && err.error.trim()) {
    return err.error;
  }

  if (err?.error && typeof err.error === 'object') {
    if (typeof err.error.message === 'string' && err.error.message.trim()) {
      return err.error.message;
    }
    if (typeof err.error.error === 'string' && err.error.error.trim()) {
      return err.error.error;
    }
  }

  if (err?.status === 0) {
    return 'Unable to reach the server. Please check your connection and try again.';
  }

  if (typeof err?.message === 'string' && err.message.trim()) {
    return err.message;
  }

  return fallback;
}
