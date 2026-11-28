import { useEffect } from "react";

export const SITE_NAME = "Virtual Clubroom";

/**
 * The browser tab's title, and what screen readers announce after a navigation (AppLayout): "Members · Chess · Virtual
 * Clubroom". Back to the site's name when the page goes, so a page without a title never shows the previous one's.
 * A layout's title wins over its pages' (a parent's effect runs after its children's): ClubLayout names the section.
 */
export function usePageTitle(...parts: (string | null | undefined)[]) {
  const title = [...parts.filter((part): part is string => !!part), SITE_NAME].join(" · ");
  useEffect(() => {
    document.title = title;
    return () => {
      document.title = SITE_NAME;
    };
  }, [title]);
}
