import type { RouteObject } from "react-router";
import { createBrowserRouter, Navigate, Outlet } from "react-router";
import { LoginPage, RequireAdmin, RequireAuth } from "@/features/auth";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { ErrorPage } from "./ErrorPage";
import { AppLayout } from "./layouts/AppLayout";
import { NotFoundPage } from "./NotFoundPage";

/**
 * Every route of 08-frontend §4. Exported separately so tests can mount it in a memory router. Each page is loaded
 * lazily from its feature's public index, so each feature becomes its own chunk.
 */
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
          { index: true, lazy: () => import("@/features/discovery").then((m) => ({ Component: m.LandingPage })) },
          { path: "clubs", lazy: () => import("@/features/discovery").then((m) => ({ Component: m.ClubDirectoryPage })) },
          { path: "clubs/:slug/about", lazy: () => import("@/features/discovery").then((m) => ({ Component: m.PublicClubProfilePage })) },
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
                  { path: "s/:spaceSlug/:channelSlug", lazy: () => import("@/features/spaces").then((m) => ({ Component: m.ChannelPage })) },
                  { path: "events", lazy: () => import("@/features/calendar").then((m) => ({ Component: m.ClubEventsPage })) },
                  { path: "members", lazy: () => import("@/features/membership").then((m) => ({ Component: m.MembersPage })) },
                  { path: "history", lazy: () => import("@/features/history").then((m) => ({ Component: m.ClubHistoryPage })) },
                  {
                    path: "settings",
                    lazy: () => import("@/features/clubs").then((m) => ({ Component: m.ClubSettingsLayout })),
                    children: [
                      { index: true, lazy: () => import("@/features/clubs").then((m) => ({ Component: m.ClubSettingsIndex })) },
                      { path: "profile", lazy: () => import("@/features/clubs").then((m) => ({ Component: m.ClubSettingsProfile })) },
                      { path: "requests", lazy: () => import("@/features/membership").then((m) => ({ Component: m.JoinRequestsPage })) },
                      { path: "roles", lazy: () => import("@/features/membership").then((m) => ({ Component: m.RolesEditor })) },
                      { path: "groups", lazy: () => import("@/features/groups").then((m) => ({ Component: m.GroupsSettings })) },
                      { path: "spaces", lazy: () => import("@/features/spaces").then((m) => ({ Component: m.SpaceSettings })) },
                      { path: "ownership", lazy: () => import("@/features/membership").then((m) => ({ Component: m.OwnershipPanel })) },
                    ],
                  },
                ],
              },
              { path: "posts/:postId", lazy: () => import("@/features/forum").then((m) => ({ Component: m.PostRedirectPage })) },
              { path: "events/:eventId", lazy: () => import("@/features/calendar").then((m) => ({ Component: m.EventPage })) },
              { path: "calendar", lazy: () => import("@/features/calendar").then((m) => ({ Component: m.CampusCalendarPage })) },
              { path: "notifications", lazy: () => import("@/features/notifications").then((m) => ({ Component: m.NotificationsPage })) },
              { path: "settings", lazy: () => import("./SettingsPage").then((m) => ({ Component: m.SettingsPage })) },
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
                  { path: "revivals", lazy: () => import("@/features/admin").then((m) => ({ Component: m.RevivalQueuePage })) },
                  { path: "clubs", lazy: () => import("@/features/admin").then((m) => ({ Component: m.AdminClubsPage })) },
                  { path: "users", lazy: () => import("@/features/admin").then((m) => ({ Component: m.UsersPage })) },
                  { path: "campus-groups", lazy: () => import("@/features/admin").then((m) => ({ Component: m.CampusGroupsPage })) },
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
