import { getApiErrorMessage } from './http-error';

describe('getApiErrorMessage', () => {
  it('handles string object network and fallback error shapes', () => {
    expect(getApiErrorMessage({ error: 'Plain error' }, 'Fallback')).toBe('Plain error');
    expect(getApiErrorMessage({ error: { message: 'Nested message' } }, 'Fallback')).toBe('Nested message');
    expect(getApiErrorMessage({ error: { error: 'Inner error' } }, 'Fallback')).toBe('Inner error');
    expect(getApiErrorMessage({ status: 0 }, 'Fallback')).toContain('Unable to reach the server');
    expect(getApiErrorMessage({ message: 'Direct message' }, 'Fallback')).toBe('Direct message');
    expect(getApiErrorMessage({}, 'Fallback')).toBe('Fallback');
  });
});
