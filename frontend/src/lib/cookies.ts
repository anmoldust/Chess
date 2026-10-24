/** Read a single cookie value out of a `document.cookie`-style string. */
export function readCookie(cookieHeader: string, name: string): string | null {
  const match = cookieHeader.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

/** The CSRF token Spring Security puts in the readable XSRF-TOKEN cookie. */
export function csrfTokenFrom(cookieHeader: string): string | null {
  return readCookie(cookieHeader, 'XSRF-TOKEN');
}
