import { create } from "zustand";
import { persist } from "zustand/middleware";

export type Theme = "light" | "dark";

interface ThemeState {
  theme: Theme;
  toggle: () => void;
}

/** First visit follows the OS setting; the choice is then remembered in localStorage. */
const prefersDark = window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      theme: prefersDark ? "dark" : "light",
      toggle: () => set((state) => ({ theme: state.theme === "dark" ? "light" : "dark" })),
    }),
    { name: "vclubroom-theme" },
  ),
);

/** theme.css switches every token on the `.dark` class (@custom-variant dark). */
function applyTheme(theme: Theme) {
  document.documentElement.classList.toggle("dark", theme === "dark");
}

applyTheme(useThemeStore.getState().theme);
useThemeStore.subscribe((state) => applyTheme(state.theme));