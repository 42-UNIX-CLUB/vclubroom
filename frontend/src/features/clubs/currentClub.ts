import { useOutletContext } from "react-router";
import type { Club } from "./api";

/** The club of the current /c/:slug page, loaded once by ClubLayout. Works in every page under /c/:slug. */
export function useCurrentClub(): Club {
  return useOutletContext<Club>();
}
