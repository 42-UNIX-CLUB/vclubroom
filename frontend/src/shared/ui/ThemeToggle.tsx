import { MoonIcon, SunIcon } from "lucide-react";
import { useThemeStore } from "@/shared/lib/theme";
import { Button } from "./button";

export function ThemeToggle() {
  const theme = useThemeStore((state) => state.theme);
  const toggle = useThemeStore((state) => state.toggle);
  const label = theme === "dark" ? "Switch to light theme" : "Switch to dark theme";
  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label={label} title={label}>
      {theme === "dark" ? <SunIcon /> : <MoonIcon />}
    </Button>
  );
}