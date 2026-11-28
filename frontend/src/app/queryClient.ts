import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/errors";

/**
 * The app's query client. A 401 from any request means the session ended (it expired, the password was changed in
 * another browser, an admin disabled the account): forget who was logged in, so RequireAuth sends the user to /login
 * with the page to come back to, and the live connection stops reconnecting. The login form's own 401 (a wrong
 * password) changes nothing: nobody is logged in there.
 */
export function createQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError: (error) => forgetSessionOn401(client, error) }),
    mutationCache: new MutationCache({ onError: (error) => forgetSessionOn401(client, error) }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.status < 500) && failureCount < 2,
      },
    },
  });
  return client;
}

function forgetSessionOn401(client: QueryClient, error: unknown) {
  if (error instanceof ApiError && error.status === 401 && client.getQueryData(["me"])) {
    client.setQueryData(["me"], null);
  }
}

export const queryClient = createQueryClient();
