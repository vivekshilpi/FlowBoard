import { clearAuthStorage } from './auth-storage';

describe('clearAuthStorage', () => {
  it('removes all authentication keys from localStorage', () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem('userId', '1');
    localStorage.setItem('userRole', 'MEMBER');
    localStorage.setItem('userEmail', 'user@example.com');
    localStorage.setItem('userName', 'User');
    localStorage.setItem('userAvatarUrl', '/avatar.png');

    clearAuthStorage();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('userId')).toBeNull();
    expect(localStorage.getItem('userRole')).toBeNull();
    expect(localStorage.getItem('userEmail')).toBeNull();
    expect(localStorage.getItem('userName')).toBeNull();
    expect(localStorage.getItem('userAvatarUrl')).toBeNull();
  });
});
