/** A full page load, leaving the SPA: for pages the backend serves, or another site. In its own module so tests mock it. */
export function loadPage(url: string, { replace = false } = {}): void {
  if (replace) window.location.replace(url);
  else window.location.assign(url);
}
