import { TriangleAlertIcon } from "lucide-react";
import { isRouteErrorResponse, Link, useRouteError } from "react-router";
import { buttonVariants } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { NotFoundPage } from "./NotFoundPage";

/** errorElement of the routes: a page (or the layout) threw while rendering or loading. */
export function ErrorPage() {
  const error = useRouteError();
  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      {isRouteErrorResponse(error) && error.status === 404 ? (
        <NotFoundPage />
      ) : (
        <EmptyState
          icon={TriangleAlertIcon}
          title="Something went wrong"
          description="An unexpected error stopped this page. Try again, or go back home."
          action={<Link to="/" className={buttonVariants({ variant: "outline" })}>Back home</Link>}
        />
      )}
    </div>
  );
}