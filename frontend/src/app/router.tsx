// Placeholder: replaced by the full route table`
import { useQuery } from "@tanstack/react-query";
import { createBrowserRouter } from "react-router";
import { api } from "@/shared/api/client";

function PipelineCheck() {
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: async (): Promise<string[]> => (await api.GET("/api/public/categories")).data ?? [],
  });
  return (
    <div className="p-8">
      <p className="text-2xl font-semibold text-primary">Virtual Clubroom</p>
      <p className="mt-2 text-sm text-muted-foreground">
        {categories.isPending
          ? "Loading categories…"
          : categories.isError
            ? `API error: ${categories.error.message}`
            : categories.data.join(" · ")}
      </p>
    </div>
  );
}

export const router = createBrowserRouter([{ path: "*", element: <PipelineCheck /> }]);