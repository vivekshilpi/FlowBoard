const AUTH_STORAGE_KEYS = [
  'token',
  'userId',
  'userRole',
  'userEmail',
  'userName',
  'userAvatarUrl'
] as const;

export function clearAuthStorage(): void {
  if (typeof localStorage === 'undefined') {
    return;
  }

  for (const key of AUTH_STORAGE_KEYS) {
    localStorage.removeItem(key);
  }
}
