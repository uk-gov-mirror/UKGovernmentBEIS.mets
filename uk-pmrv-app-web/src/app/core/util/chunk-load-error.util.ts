/**
 * Helpers for recovering from stale lazy-chunk loads during zero-downtime (rolling) deployments.
 *
 * The app is built with `@angular/build:application` (esbuild) and `outputHashing: all`, so lazy
 * routes are loaded with native dynamic `import()` and every chunk filename carries a content hash.
 * While a rolling deployment is in progress, a user can be holding an old `index.html` that references
 * chunk hashes the newly rolled-out pods no longer serve. The dynamic import then fails and, left
 * unhandled, the user is sent to the generic `/error/500` page. A full-page load pulls the fresh
 * `index.html` with valid chunk hashes and recovers.
 */

/** sessionStorage key tracking the timestamp of the last stale-chunk reload. */
const STALE_CHUNK_RELOAD_KEY = 'stale-chunk-reload-at';

/** Minimum gap between automatic reloads, to avoid an infinite reload loop on a genuinely broken deployment. */
const RELOAD_DEBOUNCE_MS = 10000;

/**
 * Decides whether an error is a stale lazy-chunk failure that we should recover from with a reload.
 *
 * Returns `false` when we already reloaded within the debounce window, so a genuinely broken deployment
 * cannot trap the user in an infinite reload loop. Otherwise, matches the browser-specific dynamic-import
 * failure messages (and the legacy webpack `ChunkLoadError` as a safety net) raised when an old
 * `index.html` requests a chunk hash the freshly rolled-out pods no longer serve.
 */
export const isChunkLoadError = (error: unknown): boolean => {
  if (!error) {
    return false;
  }

  const lastReloadAt = Number(sessionStorage.getItem(STALE_CHUNK_RELOAD_KEY) ?? 0);

  if (Date.now() - lastReloadAt < RELOAD_DEBOUNCE_MS) {
    return false;
  }

  const name = (error as { name?: string }).name ?? '';
  const message = (error as { message?: string }).message ?? String(error);

  return (
    name === 'ChunkLoadError' ||
    /Loading chunk [\w-]+ failed/i.test(message) ||
    /Failed to fetch dynamically imported module/i.test(message) || // Chromium / esbuild
    /error loading dynamically imported module/i.test(message) || // Firefox
    /Importing a module script failed/i.test(message) // Safari
  );
};

/**
 * Records the reload attempt (so {@link isChunkLoadError} suppresses repeat reactions within the
 * debounce window) and triggers a full-page load, which pulls the fresh `index.html` and its valid
 * chunk hashes. When `url` is given, the browser loads that URL (e.g. the navigation target that failed),
 * otherwise the current page is reloaded.
 */
export const reloadForStaleChunk = (url?: string): void => {
  sessionStorage.setItem(STALE_CHUNK_RELOAD_KEY, String(Date.now()));

  if (url) {
    window.location.assign(url);
  } else {
    window.location.reload();
  }
};
