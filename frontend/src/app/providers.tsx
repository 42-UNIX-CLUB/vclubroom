import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useThemeStore } from "@/shared/lib/theme";
import { Toaster } from "@/shared/ui/sonner";
import { TooltipProvider } from "@/shared/ui/tooltip";
import { queryClient } from "./queryClient";

export function Providers({ children }: { children: ReactNode }) {
  const theme = useThemeStore((state) => state.theme);
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        {children}
        <Toaster richColors theme={theme} />
      </TooltipProvider>
    </QueryClientProvider>
  );
}