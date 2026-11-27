import { useEffect } from "react";
import { useNavigate } from "react-router";
import { loadPage } from "@/shared/lib/loadPage";
import { resumesOnServer, takeReturnTo } from "./returnTo";
import { useMe } from "./useMe";

/** Mounted in AppLayout: after Login with 42 lands on "/", resume the page the user came from. */
export function PostLoginRedirect() {
  const { data: me } = useMe();
  const navigate = useNavigate();
  useEffect(() => {
    if (!me) return;
    const target = takeReturnTo();
    if (!target || target === "/") return;
    if (resumesOnServer(target)) loadPage(target, { replace: true });
    else navigate(target, { replace: true });
  }, [me, navigate]);
  return null;
}
