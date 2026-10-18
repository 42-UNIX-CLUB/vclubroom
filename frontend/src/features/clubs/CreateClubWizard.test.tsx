import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { CreateClubWizard } from "./CreateClubWizard";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn(), POST: vi.fn() } }));

beforeEach(() => {
  vi.mocked(api.GET).mockResolvedValue({ data: ["Games", "Tech"] } as never);   // categories
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderWizard() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <CreateClubWizard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const type = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });

/** Base UI's select picks an option on pointer-up, which a bare click() does not send: replay a real mouse press. */
async function chooseOption(label: string, option: string) {
  fireEvent.click(screen.getByLabelText(label));
  const item = await screen.findByRole("option", { name: option });
  for (const event of ["pointerDown", "mouseDown", "pointerUp", "mouseUp", "click"] as const) {
    fireEvent[event](item, { pointerType: "mouse", button: 0 });
  }
}

describe("CreateClubWizard", () => {
  it("does not leave a step with invalid fields", async () => {
    renderWizard();
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(await screen.findByText("Use at least 3 characters.")).toBeTruthy();
    expect(screen.getByText("Choose a category.")).toBeTruthy();
    expect(screen.getByText("Step 1 of 3")).toBeTruthy();
  });

  it("walks through the three steps and submits the request", async () => {
    vi.mocked(api.POST).mockResolvedValueOnce({ data: { id: "c1", name: "Chess Club", status: "PENDING_APPROVAL" } } as never);
    renderWizard();

    type("Name", "Chess Club");
    await chooseOption("Category", "Games");
    fireEvent.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText("Step 2 of 3")).toBeTruthy();
    type("Description", "**Blitz** on Fridays");
    type("Tags", "chess, Beginners");
    fireEvent.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText("Step 3 of 3")).toBeTruthy();
    expect(screen.getByText("Blitz").tagName).toBe("STRONG");           // Markdown preview
    fireEvent.click(screen.getByRole("button", { name: "Submit for review" }));

    expect(await screen.findByText("Request sent")).toBeTruthy();
    expect(api.POST).toHaveBeenCalledWith("/api/clubs", { body: {
      name: "Chess Club", tagline: undefined, description: "**Blitz** on Fridays", category: "Games",
      tags: ["chess", "beginners"],
    } });
  });
});