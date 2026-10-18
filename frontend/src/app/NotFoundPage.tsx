import { SearchXIcon } from "lucide-react";
import { Link } from "react-router";
import { buttonVariants } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";

export function NotFoundPage() {
  return (
    <EmptyState
      icon={SearchXIcon}
      title="Page not found"
      description="This page does not exist, or it moved. Clubs that were archived are still in the directory."
      action={<Link to="/" className={buttonVariants({ variant: "outline" })}>Back home</Link>}
    />
  );
}