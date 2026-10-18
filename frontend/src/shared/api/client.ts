import createClient, { type Middleware } from "openapi-fetch";
import type { paths } from "./schema";
import { ApiError, type ProblemDetail } from "./errors";

const UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

function readCookie(name: string): string | undefined {
  const entry = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`));
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : undefined;
}

/** Spring Security CSRF: echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header. */
export function csrfHeader(): Record<string, string> {
  const token = readCookie("XSRF-TOKEN");
  return token ? { "X-XSRF-TOKEN": token } : {};
}

const csrfMiddleware: Middleware = {
  async onRequest({ request }) {
    if (UNSAFE_METHODS.has(request.method)) {
      for (const [name, value] of Object.entries(csrfHeader())) request.headers.set(name, value);
    }
    return request;
  },
};

/** Turns RFC 9457 problem details into typed exceptions (TanStack Query surfaces them as `error`). */
const errorMiddleware: Middleware = {
  async onResponse({ response }) {
    if (response.ok) return response;
    const problem = (await response.clone().json().catch(() => null)) as ProblemDetail | null;
    throw new ApiError(response.status, problem);
  },
};

export const api = createClient<paths>({ baseUrl: "" }); // same origin: nginx (prod) / Vite proxy (dev)
api.use(csrfMiddleware, errorMiddleware);

/** Call once at startup so the XSRF-TOKEN cookie exists before the first unsafe request. */
export async function bootstrapCsrf(): Promise<void> {
  try {
    await fetch("/api/auth/csrf", { credentials: "same-origin" });
  } catch {
    // Backend not running yet (e.g. frontend-only work): the next request will retry.
  }
}