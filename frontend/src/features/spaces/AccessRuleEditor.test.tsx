import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api/client";
import { AccessRuleEditor, audienceSentence } from "./AccessRuleEditor";
import type { Rule } from "./api";

vi.mock("@/shared/api/client", () => ({ api: { GET: vi.fn() } }));

const names = new Map([["g1", "Board"], ["g2", "Robotics team"]]);

function Editor({ initial }: { initial: [Rule, Rule] }) {
  const [rules, setRules] = useState(initial);
  return <AccessRuleEditor clubId="c1" read={rules[0]} write={rules[1]} onChange={(r, w) => setRules([r, w])} />;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("audienceSentence", () => {
  it("says who can read and who can post, posting never wider than reading (R-23)", () => {
    expect(audienceSentence({ level: "PUBLIC", groupIds: [] }, { level: "GROUPS", groupIds: ["g1"] }, names))
      .toBe("Everyone can read · Board can post");
    expect(audienceSentence({ level: "GROUPS", groupIds: ["g1", "g2"] }, { level: "PUBLIC", groupIds: [] }, names))
      .toBe("Board, Robotics team can read · Board, Robotics team can post");
    expect(audienceSentence({ level: "CLUB_MEMBERS", groupIds: [] }, { level: "GROUPS", groupIds: [] }, names))
      .toBe("Club members can read · Nobody can post");
  });
});

describe("AccessRuleEditor", () => {
  it("offers the club's groups for a GROUPS rule and keeps the sentence in step", async () => {
    vi.mocked(api.GET).mockImplementation(((path: string) => Promise.resolve({
      data: path === "/api/campus-groups" ? [] : [{ id: "g1", name: "Board", memberCount: 2 }],
    })) as never);
    render(
      <QueryClientProvider client={new QueryClient()}>
        <Editor initial={[{ level: "CLUB_MEMBERS", groupIds: [] }, { level: "CLUB_MEMBERS", groupIds: [] }]} />
      </QueryClientProvider>,
    );

    expect(screen.getByText("Club members can read · Club members can post")).toBeTruthy();
    const someGroups = screen.getAllByRole("radio", { name: "Some groups" });
    fireEvent.click(someGroups[1]!);                                                   // who can post
    fireEvent.click(await screen.findByRole("checkbox", { name: /Board/ }, { timeout: 3000 }));
    expect(screen.getByText("Club members can read · Board can post")).toBeTruthy();

    fireEvent.click(screen.getAllByRole("radio", { name: "Everyone" })[1]!);
    expect(screen.getByText("Posting can't be wider than reading.")).toBeTruthy();
  });
});
