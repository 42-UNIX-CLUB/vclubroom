import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { PageHeader } from "@/shared/ui/PageHeader";
import { SITE_NAME, usePageTitle } from "./usePageTitle";

afterEach(cleanup);

function Layout({ children }: { children: React.ReactNode }) {
  usePageTitle("Events", "Chess");
  return <>{children}</>;
}

describe("usePageTitle", () => {
  it("names the tab after the page, and gives it back when the page goes", () => {
    const { unmount } = render(<PageHeader title="Notifications" />);
    expect(document.title).toBe(`Notifications · ${SITE_NAME}`);
    unmount();
    expect(document.title).toBe(SITE_NAME);
  });

  it("lets a layout's title win over its page's", () => {
    render(<Layout><PageHeader title="Settings" /></Layout>);
    expect(document.title).toBe(`Events · Chess · ${SITE_NAME}`);
  });

  it("skips parts that are not known yet", () => {
    function Page() {
      usePageTitle(undefined);
      return null;
    }
    render(<Page />);
    expect(document.title).toBe(SITE_NAME);
  });
});
