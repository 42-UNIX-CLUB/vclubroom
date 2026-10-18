import { ShieldAlertIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router";
import { buttonVariants } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { RequireAuth } from "./RequireAuth";
import { useMe } from "./useMe";

/** Admin console guard. The backend enforces it anyway (/api/admin/** needs ROLE_ADMIN). */
export function RequireAdmin({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <AdminOnly>{children}</AdminOnly>
    </RequireAuth>
  );
}

function AdminOnly({ children }: { children: ReactNode }) {
  const { data: me } = useMe();
  if (!me?.admin) {
    return (
      <EmptyState
        icon={ShieldAlertIcon}
        title="Admins only"
        description="This part of Virtual Clubroom is reserved for the bocal and club administrators."
        action={<Link to="/" className={buttonVariants({ variant: "outline" })}>Back home</Link>}
      />
    );
  }
  return <>{children}</>;
}