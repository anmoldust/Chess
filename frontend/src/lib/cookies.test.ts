import { describe, expect, it } from 'vitest';
import { csrfTokenFrom, readCookie } from './cookies';

describe('readCookie', () => {
  it('finds a value among several cookies', () => {
    expect(readCookie('JSESSIONID=abc123; XSRF-TOKEN=def456', 'XSRF-TOKEN')).toBe('def456');
  });

  it('finds a value at the start of the header', () => {
    expect(readCookie('XSRF-TOKEN=first; other=1', 'XSRF-TOKEN')).toBe('first');
  });

  it('returns null when absent', () => {
    expect(readCookie('JSESSIONID=abc123', 'XSRF-TOKEN')).toBeNull();
  });

  it('does not match a suffix of another cookie name', () => {
    expect(readCookie('NOT-XSRF-TOKEN=nope', 'XSRF-TOKEN')).toBeNull();
  });

  it('decodes an encoded value', () => {
    expect(readCookie('XSRF-TOKEN=a%2Db', 'XSRF-TOKEN')).toBe('a-b');
  });
});

describe('csrfTokenFrom', () => {
  it('reads the Spring Security CSRF cookie', () => {
    expect(csrfTokenFrom('XSRF-TOKEN=token-value')).toBe('token-value');
  });
});
