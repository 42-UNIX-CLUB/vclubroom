import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { GroupPicker } from "./GroupPicker";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

/** The club's groups and the campus groups, each from its own endpoint. */
function backend(club: unknown[], campus: unknown[]) {
  vi.mocked(api.GET).mockImplementation(((path: string) =>
    Promise.resolve({ data: path === "/api/campus-groups" ? campus : club })) as never);
}

function renderPicker(value: string[], onChange: (ids: string[]) => void = () => {}) {
  render(<QueryClientProvider client={new QueryClient()}><GroupPicker clubId="c1" value={value} onChange={onChange} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("GroupPicker", () => {
  it("ticks and unticks the club's groups", async () => {
    backend([{ id: "g1", name: "Board", memberCount: 3 }, { id: "g2", name: "Robotics team", memberCount: 8 }], []);
    const onChange = vi.fn();
    renderPicker(["g1"], onChange);

    const boardBox = await screen.findByRole("checkbox", { name: /Board/ }, { timeout: 3000 });
    expect((boardBox as HTMLInputElement).checked).toBe(true);
    fireEvent.click(screen.getByRole("checkbox", { name: /Robotics team/ }));
    expect(onChange).toHaveBeenLastCalledWith(["g1", "g2"]);
    fireEvent.click(boardBox);
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it("says where groups are made when there are none", async () => {
    backend([], []);
    renderPicker([]);
    expect(await screen.findByText(/create them in Settings › Groups/, {}, { timeout: 3000 })).toBeTruthy();
  });

  it("offers campus groups too, filtered when there are many, keeping the picked ones in view", async () => {
    const pools = Array.from({ length: 9 }, (_, i) => ({ id: `p${i}`, name: `Pool ${2017 + i}`, memberCount: 100, synced: true }));
    backend([], [...pools, { id: "t1", name: "Tutors", memberCount: 4, synced: false }]);
    const onChange = vi.fn();
    renderPicker(["p0"], onChange);

    fireEvent.change(await screen.findByRole("textbox", { name: /find a campus group/ }, { timeout: 3000 }), { target: { value: "tut" } });
    expect(screen.getAllByRole("checkbox").map((box) => box.parentElement?.textContent)).toEqual(["Pool 2017(100)", "Tutors(4)"]);
    fireEvent.click(screen.getByRole("checkbox", { name: /Tutors/ }));
    expect(onChange).toHaveBeenLastCalledWith(["p0", "t1"]);
  });
});
