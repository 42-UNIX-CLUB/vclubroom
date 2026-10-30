import { useQueryClient } from "@tanstack/react-query";
import { LifeBuoyIcon, LogOutIcon, SettingsIcon, ShieldIcon } from "lucide-react";
import { useLocation, useNavigate } from "react-router";
import { toast } from "sonner";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel,
  DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/shared/ui/dropdown-menu";
import { logout } from "./logout";
import type { Me } from "./useMe";

/** Avatar in the top bar: Settings, Help & feedback, Admin console (admins only), Log out. */
export function UserMenu({ me }: { me: Me }) {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const name = me.displayName || me.login || "Me";

  async function onLogout() {
    try {
      await logout();
    } catch {
      toast.error("Logout failed. Please try again.");
      return;
    }
    queryClient.clear();                     // nothing cached for this user survives
    queryClient.setQueryData(["me"], null);  // the top bar switches to "Log in" immediately
    navigate("/", { replace: true });
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="Account menu"
        className="rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <ClubAvatar name={name} url={me.avatarUrl} />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuGroup>
          <DropdownMenuLabel>
            <span className="block text-sm text-foreground">{name}</span>
            <span className="block">{me.login}</span>
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => navigate("/settings")}>
          <SettingsIcon /> Settings
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => navigate(`/support/new?from=${encodeURIComponent(location.pathname + location.search)}`)}>
          <LifeBuoyIcon /> Help & feedback
        </DropdownMenuItem>
        {me.admin && (
          <DropdownMenuItem onClick={() => navigate("/admin")}>
            <ShieldIcon /> Admin console
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onClick={onLogout}>
          <LogOutIcon /> Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
