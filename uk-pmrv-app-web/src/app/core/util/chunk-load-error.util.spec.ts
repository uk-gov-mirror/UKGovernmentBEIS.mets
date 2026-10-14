import { isChunkLoadError, reloadForStaleChunk } from '@core/util/chunk-load-error.util';

describe('chunk-load-error util', () => {
  const RELOAD_KEY = 'stale-chunk-reload-at';

  beforeEach(() => {
    sessionStorage.clear();
  });

  describe('isChunkLoadError', () => {
    it.each([
      ['Chromium / esbuild', 'Failed to fetch dynamically imported module: https://app/chunk-ABC123.js'],
      ['Firefox', 'error loading dynamically imported module'],
      ['Safari', 'Importing a module script failed.'],
      ['legacy webpack message', 'Loading chunk 123 failed.'],
    ])('returns true for a %s dynamic-import failure', (_label, message) => {
      expect(isChunkLoadError(new Error(message))).toBe(true);
    });

    it('returns true when the error name is ChunkLoadError', () => {
      const error = new Error('whatever');
      error.name = 'ChunkLoadError';

      expect(isChunkLoadError(error)).toBe(true);
    });

    it('returns false for unrelated errors', () => {
      expect(isChunkLoadError(new Error('Something else went wrong'))).toBe(false);
    });

    it('returns false for nullish values', () => {
      expect(isChunkLoadError(null)).toBe(false);
      expect(isChunkLoadError(undefined)).toBe(false);
    });

    it('returns false for a chunk-load error within the debounce window (loop guard)', () => {
      sessionStorage.setItem(RELOAD_KEY, String(Date.now()));

      expect(isChunkLoadError(new Error('Failed to fetch dynamically imported module: /chunk.js'))).toBe(false);
    });

    it('returns true for a chunk-load error once the debounce window has elapsed', () => {
      // Pretend the last reload happened well outside the debounce window.
      sessionStorage.setItem(RELOAD_KEY, String(Date.now() - 60_000));

      expect(isChunkLoadError(new Error('Failed to fetch dynamically imported module: /chunk.js'))).toBe(true);
    });
  });

  describe('reloadForStaleChunk', () => {
    let consoleError: jest.SpyInstance;

    beforeEach(() => {
      // window.location.reload()/assign() are non-mockable in jsdom and, since jsdom does not implement
      // navigation, they emit a "Not implemented" error via console.error. Silence it to keep the
      // (passing) test output clean.
      consoleError = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    });

    afterEach(() => {
      consoleError.mockRestore();
    });

    it('records the reload attempt so subsequent failures are debounced', () => {
      reloadForStaleChunk();

      expect(Number(sessionStorage.getItem(RELOAD_KEY))).toBeGreaterThan(0);
    });

    it('records the reload attempt when loading a target url', () => {
      reloadForStaleChunk('/installation-aviation/dashboard');

      expect(Number(sessionStorage.getItem(RELOAD_KEY))).toBeGreaterThan(0);
    });
  });
});
