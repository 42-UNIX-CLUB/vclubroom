import type { RouteObject } from "react-router";
import { createBrowserRouter, Navigate, Outlet } from "react-router";
import { LoginPage, RequireAdmin, RequireAuth } from "@/features/auth";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { PlaceholderPage } from "@/shared/ui/PlaceholderPage";
import { ErrorPage } from "./ErrorPage";
import { AppLayout } from "./layouts/AppLayout";
import { NotFoundPage } from "./NotFoundPage";

/**
 * Placeholder until the named story lands. Then replace it with the feature's page, loaded lazily
 * from the feature's public index so each feature becomes its own chunk, e.g.:
 *   { path: "clubs", lazy: () => import("@/features/discovery").then((m) => ({ Component: m.ClubDirectoryPage })) },
 */
const soon = (title: string, story: string) => <PlaceholderPage title={title} story={story} />;

/** Every route of 08-frontend §4. Exported separately so tests can mount it in a memory router. */
export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    errorElement: <ErrorPage />,          // the layout itself failed
    HydrateFallback: FullPageSpinner,     // shown while lazy routes load on the first page view
    children: [
      {
        errorElement: <ErrorPage />,      // a page failed: the top bar stays
        children: [
          // Public
          { index: true, element: soon("Welcome to Virtual Clubroom", "E06-S02") },
          { path: "clubs", element: soon("Clubs", "E06-S03") },
          { path: "clubs/:slug/about", element: soon("Club profile", "E06-S03") },
          { path: "login", element: <LoginPage /> },

          // Logged-in users
          {
            element: <RequireAuth><Outlet /></RequireAuth>,
            children: [
              { path: "clubs/new", lazy: () => import("@/features/clubs").then((m) => ({ Component: m.CreateClubPage })) },
              {
                path: "c/:slug",
                lazy: () => import("@/features/clubs").then((m) => ({ Component: m.ClubLayout })),
                children: [
                  { index: true, lazy: () => import("@/features/clubs").then((m) => ({ Component: m.ClubHome })) },
                  { path: "s/:spaceSlug/:channelSlug", element: soon("Channel", "E05-S02") },
                  { path: "events", element: soon("Club events", "E11-S02") },
                  { path: "members", element: soon("Members", "E07-S04") },
                  { path: "history", element: soon("Club history", "E14-S03") },
                  { path: "settings", element: <Navigate to="profile" replace /> },
                  { path: "settings/profile", element: soon("Club profile settings", "E04-S04") },
                  { path: "settings/roles", element: soon("Roles", "E13-S02") },
                  { path: "settings/groups", element: soon("Groups", "E13-S03") },
                  { path: "settings/spaces", element: soon("Spaces", "E05-S03") },
                  { path: "settings/requests", element: soon("Join requests", "E07-S03") },
                  { path: "settings/ownership", element: soon("Ownership", "E13-S01") },
                ],
              },
              { path: "posts/:postId", element: soon("Post", "E12-S01") },
              { path: "events/:eventId", element: soon("Event", "E11-S01") },
//               { path: "calendar", element: soon("Campus calendar", "E11-S02") },
              {
                path: "calendar",
                lazy: () =>
                  import("@/features/calendar/CampusCalendarPage").then((m) => ({
                    Component: m.CampusCalendarPage,
                  })),
              },
              { path: "notifications", element: soon("Notifications", "E12-S01") },
              { path: "settings", element: soon("Settings", "E12-S02") },
            ],
          },

          // Admins: the whole console (layout + pages) is one lazily loaded chunk of the admin feature
          {
            path: "admin",
            element: <RequireAdmin><Outlet /></RequireAdmin>,
            children: [
              {
                lazy: () => import("@/features/admin").then((m) => ({ Component: m.AdminLayout })),
                children: [
                  { index: true, element: <Navigate to="approvals" replace /> },
                  { path: "approvals", lazy: () => import("@/features/admin").then((m) => ({ Component: m.ApprovalQueuePage })) },
                  { path: "revivals", element: soon("Revival requests", "E14-S02") },
                  { path: "clubs", lazy: () => import("@/features/admin").then((m) => ({ Component: m.AdminClubsPage })) },
                  { path: "users", element: soon("Users", "E15-S02") },
                  { path: "campus-groups", element: soon("Campus groups", "E15-S03") },
                ],
              },
            ],
          },

          { path: "*", element: <NotFoundPage /> },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
