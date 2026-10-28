import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { GroupPicker } from "./GroupPicker";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("GroupPicker", () => {
  it("ticks and unticks the club's groups", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: [
        { id: "g1", name: "Board", memberCount: 3 }, { id: "g2", name: "Robotics team", memberCount: 8 },
      ] } as never);
    const onChange = vi.fn();
    render(<QueryClientProvider client={new QueryClient()}><GroupPicker clubId="c1" value={["g1"]} onChange={onChange} /></QueryClientProvider>);

    const boardBox = await screen.findByRole("checkbox", { name: /Board/ }, { timeout: 3000 });
    expect((boardBox as HTMLInputElement).checked).toBe(true);
    fireEvent.click(screen.getByRole("checkbox", { name: /Robotics team/ }));
    expect(onChange).toHaveBeenLastCalledWith(["g1", "g2"]);
    fireEvent.click(boardBox);
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it("says where groups are made when there are none", async () => {
    vi.mocked(api.GET).mockResolvedValue({ data: [] } as never);
    render(<QueryClientProvider client={new QueryClient()}><GroupPicker clubId="c1" value={[]} onChange={() => {}} /></QueryClientProvider>);
    expect(await screen.findByText(/create them in Settings › Groups/, {}, { timeout: 3000 })).toBeTruthy();
  });
});
