import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './client';

function response(
  body: string,
  status = 200,
  statusText = '',
  headers: Record<string, string> = {},
): Response {
  const res = {
    ok: status >= 200 && status < 300,
    status,
    statusText,
    headers: new Headers(headers),
    text: async () => body,
    json: async () => JSON.parse(body),
    clone: () => res,
  };
  return res as unknown as Response;
}

function stubFetch(res: Response) {
  const spy = vi.fn().mockResolvedValue(res);
  vi.stubGlobal('fetch', spy);
  return spy;
}

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; max-age=0; path=/';
  });

  it('echoes the CSRF cookie as a header on mutations', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-abc; path=/';
    const spy = stubFetch(response('', 204));

    await api.auth.logout();

    const [, init] = spy.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-abc');
    expect(init.credentials).toBe('same-origin');
  });

  it('does not send a CSRF header on reads', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-abc; path=/';
    const spy = stubFetch(response('{"id":"g1"}'));

    await api.getGame('g1');

    const [, init] = spy.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('turns an RFC 7807 problem into an ApiError', async () => {
    stubFetch(response('{"title":"Bad Request","detail":"nope"}', 400, 'Bad Request'));

    await expect(api.auth.me()).rejects.toMatchObject({
      status: 400,
      title: 'Bad Request',
      detail: 'nope',
    });
    await expect(api.auth.me()).rejects.toBeInstanceOf(ApiError);
  });

  it('resolves undefined for a 204', async () => {
    stubFetch(response('', 204));

    await expect(api.auth.logout()).resolves.toBeUndefined();
  });

  it('uses the token from the response header when no cookie is readable', async () => {
    // The gateway mirrors the token into a header; document.cookie can be unusable (httpOnly,
    // duplicate, stale path). The client must prime itself from a GET and reuse that token.
    // A fresh module instance is required because the token is memoised per module.
    vi.resetModules();
    const { api: freshApi } = await import('./client');

    const spy = vi
      .fn()
      .mockResolvedValueOnce(
        response('{"authenticated":false,"user":null}', 200, '', { 'X-CSRF-TOKEN': 'hdr-123' }),
      )
      .mockResolvedValueOnce(response('', 204));
    vi.stubGlobal('fetch', spy);
    document.cookie = 'XSRF-TOKEN=; max-age=0; path=/';

    await freshApi.auth.logout();

    const [primingUrl] = spy.mock.calls[0] as [string];
    const [, init] = spy.mock.calls[1] as [string, RequestInit];
    expect(primingUrl).toBe('/api/auth/me');
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('hdr-123');
  });
});
