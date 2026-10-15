/**
 * The API address must be reachable from whatever machine is running the
 * browser.
 *
 * This was a real production failure: the bundle had
 * `http://localhost:8081/api/v1` compiled into it, a colleague opened the app
 * from their own machine, and every single request failed with
 * `net::ERR_CONNECTION_REFUSED`. Nothing on screen explained why — the page
 * rendered perfectly, and `localhost` in a browser means *that browser's own
 * machine*, not the server.
 *
 * So the rule under test: when nothing is configured, the address is derived
 * from the page's origin, which reaches the same server from any machine on
 * that host.
 */
describe('API base URL resolution', () => {
  const originalLocation = window.location;

  afterEach(() => {
    jest.resetModules();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    });
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
  });

  /** Pretend the page is served from `origin`. */
  function serveFrom(origin: string) {
    const url = new URL(origin);
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...url, origin, href: origin, protocol: url.protocol, hostname: url.hostname },
    });
  }

  async function resolveBrowserUrl(): Promise<string> {
    jest.resetModules();
    const { getApiBaseUrl } = await import('@/lib/api/apiClient');
    return getApiBaseUrl();
  }

  it('derives the API host from the page origin when nothing is configured', async () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    serveFrom('http://192.168.137.1:3000/products');

    // The bug: this used to be http://localhost:8081/api/v1, which is a
    // different machine entirely.
    expect(await resolveBrowserUrl()).toBe('http://192.168.137.1:8081/api/v1');
  });

  it('works from localhost too, without special-casing it', async () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    serveFrom('http://localhost:3000/dashboard');

    expect(await resolveBrowserUrl()).toBe('http://localhost:8081/api/v1');
  });

  it('keeps the protocol of the page it was served from', async () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    serveFrom('https://stockflow.example.com');

    // An https page may not call an http API: the browser blocks it as mixed
    // content, so the protocol has to follow the page.
    expect(await resolveBrowserUrl()).toBe('https://stockflow.example.com:8081/api/v1');
  });

  it('lets an explicit URL win over the derived one', async () => {
    // Production must be able to name a different API host from the frontend.
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.stockflow.example.com/api/v1';
    serveFrom('http://192.168.137.1:3000');

    expect(await resolveBrowserUrl()).toBe('https://api.stockflow.example.com/api/v1');
  });

  it('never returns a bare localhost to a browser on another machine', async () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    serveFrom('http://192.168.137.1:3000');

    const resolved = await resolveBrowserUrl();
    expect(resolved).not.toContain('localhost');
  });

  it('ignores an env var that is present but empty', async () => {
    // `NEXT_PUBLIC_API_BASE_URL=` in a compose file sets an empty string, which
    // `??` does not catch: it would win and send every request to "".
    process.env.NEXT_PUBLIC_API_BASE_URL = '';
    serveFrom('http://192.168.137.1:3000');

    expect(await resolveBrowserUrl()).toBe('http://192.168.137.1:8081/api/v1');
  });
});