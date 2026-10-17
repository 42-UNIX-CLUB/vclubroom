import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import "./app/theme.css";
import { Providers } from "./app/providers";
import { router } from "./app/router";
import { bootstrapCsrf } from "./shared/api/client";

void bootstrapCsrf(); // ensures the XSRF-TOKEN cookie exists before the first POST

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Providers>
      <RouterProvider router={router} />
    </Providers>
  </StrictMode>,
);
