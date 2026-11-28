import { QueryClientProvider, useQuery } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import { RequireAuth } from "@/features/auth";
import type { Me } from "@/features/auth";
import { ApiError } from "@/shared/api/errors";
import { createQueryClient } from "./queryClient";

afterEach(cleanup);

const student: Me = { id: "u1", login: "jdoe", displayName: "John Doe", provider: "FORTY_TWO", admin: false };

function LoginProbe() {
  const location = useLocation();
  return <p>login page {location.search}</p>;
}

/** A protected page whose data request fails with the given status. */
function Page({ status }: { status: number }) {
  const { error } = useQuery({
    queryKey: ["page", status],
    queryFn: () => Promise.reject(new ApiError(status, { status, title: "x", detail: "x", code: "X" } as never)),
  });
  return <p>{error ? `error ${status}` : "loading"}</p>;
}

function renderPage(status: number) {
  const client = createQueryClient();
  client.setQueryData(["me"], student);                   // logged in when the page opened
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/clubs/new?draft=1"]}>
        <Routes>
          <Route path="/login" element={<LoginProbe />} />
          <Route path="*" element={<RequireAuth><Page status={status} /></RequireAuth>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}

describe("queryClient", () => {
  it("sends someone whose session ended to the login page, with the page to come back to", async () => {
    const client = renderPage(401);

    expect(await screen.findByText("login page ?returnTo=%2Fclubs%2Fnew%3Fdraft%3D1")).toBeTruthy();
    expect(client.getQueryData(["me"])).toBeNull();
  });

  it("keeps the session for other errors", async () => {
    const client = renderPage(403);

    expect(await screen.findByText("error 403")).toBeTruthy();
    expect(client.getQueryData(["me"])).toEqual(student);
  });
});
