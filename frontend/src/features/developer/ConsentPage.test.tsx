import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { loadPage } from "@/shared/lib/loadPage";
import { ConsentPage } from "./ConsentPage";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));
vi.mock("@/shared/lib/loadPage", () => ({ loadPage: vi.fn() }));

const stats = { clientId: "vcr_app_stats", name: "Club stats", description: "Charts of my clubs", homepageUrl: "https://stats.example/about",
  ownerName: "Omar Owner", scopes: ["profile:read", "forum:read"], granted: [] as string[] };
const fetchMock = vi.fn();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ redirectUri: "https://stats.example/cb?code=c0de&state=s" }), { status: 200 }));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

function renderConsent(request: object = stats, query = "client_id=vcr_app_stats&scope=profile:read%20forum:read&state=st%3D") {
  vi.mocked(api.GET).mockResolvedValue({ data: request } as never);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[`/oauth/consent?${query}`]}>
        <Routes><Route path="/oauth/consent" element={<ConsentPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** The form fields the page sent to the authorization server. */
function sent(): URLSearchParams {
  const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(url).toBe("/oauth2/authorize");
  expect(init.method).toBe("POST");
  return init.body as URLSearchParams;
}

describe("ConsentPage", () => {
  it("shows the app and what it asks for, and allows the scopes left ticked", async () => {
    renderConsent();

    expect(await screen.findByText("Club stats wants to use Virtual Clubroom as you", {}, { timeout: 3000 })).toBeTruthy();
    expect(screen.getByText(/Made by Omar Owner/)).toBeTruthy();
    expect(screen.getByRole("link", { name: "stats.example" }).getAttribute("href")).toBe("https://stats.example/about");
    expect(api.GET).toHaveBeenCalledWith("/api/oauth/consent", { params: { query: { client_id: "vcr_app_stats", scope: "profile:read forum:read" } } });

    fireEvent.click(screen.getByRole("checkbox", { name: /Forum/ }));
    fireEvent.click(screen.getByRole("button", { name: "Allow" }));

    await waitFor(() => expect(loadPage).toHaveBeenCalledWith("https://stats.example/cb?code=c0de&state=s"));
    const body = sent();
    expect(body.get("client_id")).toBe("vcr_app_stats");
    expect(body.get("state")).toBe("st=");
    expect(body.getAll("scope")).toEqual(["profile:read"]);
  });

  it("cancels by allowing nothing, so the app hears access was denied", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ redirectUri: "https://stats.example/cb?error=access_denied" }), { status: 200 }));
    renderConsent();

    fireEvent.click(await screen.findByRole("button", { name: "Cancel" }, { timeout: 3000 }));

    await waitFor(() => expect(loadPage).toHaveBeenCalledWith("https://stats.example/cb?error=access_denied"));
    expect(sent().getAll("scope")).toEqual([]);
  });

  it("keeps the scopes allowed before", async () => {
    renderConsent({ ...stats, granted: ["profile:read"] });

    const before = await screen.findByRole("checkbox", { name: /Profile.*allowed before/ }, { timeout: 3000 });
    expect((before as HTMLInputElement).checked).toBe(true);
    expect((before as HTMLInputElement).disabled).toBe(true);
  });

  it("says so when the request has expired", async () => {
    fetchMock.mockResolvedValue(new Response("", { status: 400 }));
    renderConsent();

    fireEvent.click(await screen.findByRole("button", { name: "Allow" }, { timeout: 3000 }));

    expect((await screen.findByRole("alert")).textContent).toContain("expired");
    expect(loadPage).not.toHaveBeenCalled();
  });

  it("explains itself when opened without an app's request", () => {
    renderConsent(stats, "");

    expect(screen.getByText(/part of signing in to an app/)).toBeTruthy();
    expect(api.GET).not.toHaveBeenCalled();
  });
});
