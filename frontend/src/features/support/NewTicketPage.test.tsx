import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { NewTicketPage } from "./NewTicketPage";

vi.mock("@/shared/api/client", () => ({ api: { POST: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function renderPage(url = "/support/new?from=%2Fc%2Fchess%3Ftab%3Devents") {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/support/new" element={<NewTicketPage />} />
          <Route path="/support/:number" element={<p>Ticket page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const fill = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
const send = () => screen.getByRole("button", { name: "Send ticket" }) as HTMLButtonElement;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("NewTicketPage", () => {
  it("sends a bug with the page it came from and the browser, then opens the ticket", async () => {
    vi.mocked(api.POST).mockResolvedValue({ data: { number: 17, ref: "T-17" } } as never);
    renderPage();

    expect(send().disabled).toBe(true);
    fireEvent.click(screen.getByLabelText(/Something doesn't work/));
    fill("Title", "The Join button does nothing");
    fill("Details", "I click it and nothing happens.");
    expect(screen.getByText("Your browser's name and version are sent with it.")).toBeTruthy();
    fireEvent.click(send());

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/support/tickets", { body: {
        type: "BUG", title: "The Join button does nothing", description: "I click it and nothing happens.",
        pageUrl: "/c/chess?tab=events", userAgent: navigator.userAgent,
      } }));
    expect(toast.success).toHaveBeenCalledWith("Ticket T-17 sent. The team answers on its page.");
    expect(await screen.findByText("Ticket page")).toBeTruthy();
  });

  it("leaves the page and the browser out when they don't apply or aren't wanted", async () => {
    vi.mocked(api.POST).mockResolvedValue({ data: { number: 18, ref: "T-18" } } as never);
    renderPage();

    fireEvent.click(screen.getByLabelText(/Suggestion/));
    fill("Title", "Dark calendar");
    fill("Details", "Please.");
    fireEvent.click(screen.getByLabelText(/Include the page I was on/));
    fireEvent.click(send());

    await waitFor(() => expect(api.POST).toHaveBeenCalledWith("/api/support/tickets", { body: {
        type: "SUGGESTION", title: "Dark calendar", description: "Please.", pageUrl: undefined, userAgent: undefined,
      } }));
  });

  it("ignores a page outside the app", () => {
    renderPage("/support/new?from=https%3A%2F%2Fevil.example");
    expect(screen.queryByLabelText(/Include the page I was on/)).toBeNull();
  });

  it("says when sending failed", async () => {
    vi.mocked(api.POST).mockRejectedValue(new Error("You have sent 10 tickets in the last 24 hours. Please try again tomorrow."));
    renderPage("/support/new");

    fireEvent.click(screen.getByLabelText(/Question/));
    fill("Title", "How do I leave a club?");
    fill("Details", "I can't find it.");
    fireEvent.click(send());
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("You have sent 10 tickets in the last 24 hours. Please try again tomorrow."));
  });
});
