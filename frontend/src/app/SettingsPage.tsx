import { useEffect } from "react";
import { useLocation } from "react-router";
import { PasswordForm, useMe } from "@/features/auth";
import { CalendarFeedPanel } from "@/features/calendar";
import { AppsPanel, TokensPanel } from "@/features/developer";
import { PreferencesForm } from "@/features/notifications";
import { PageHeader } from "@/shared/ui/PageHeader";
import { ThemeToggle } from "@/shared/ui/ThemeToggle";

/** /settings: my own settings, one section per feature (08-frontend §3). */
export function SettingsPage() {
  const { data: me } = useMe();
  const { hash } = useLocation();
  useEffect(() => {                                        // /settings#tokens, from a token's expiry notification
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView?.();
  }, [hash]);
  return (
    <>
      <PageHeader title="Settings" />
      <div className="max-w-2xl space-y-10">
        <section aria-labelledby="theme-heading" className="space-y-3">
          <h2 id="theme-heading" className="text-lg font-semibold">Theme Toggle</h2>
          <ThemeToggle />
        </section>
        <section aria-labelledby="notifications-heading" className="space-y-3">
          <h2 id="notifications-heading" className="text-lg font-semibold">Notifications</h2>
          <p className="text-sm text-muted-foreground">Choose how you hear about each thing. Changes are saved as you make them.</p>
          <PreferencesForm />
        </section>
        <section aria-labelledby="calendar-heading" className="space-y-3">
          <h2 id="calendar-heading" className="text-lg font-semibold">Calendar app</h2>
          <CalendarFeedPanel />
        </section>
        <section id="tokens" aria-labelledby="tokens-heading" className="space-y-3">
          <h2 id="tokens-heading" className="text-lg font-semibold">Developer tokens</h2>
          <TokensPanel />
        </section>
        <section id="apps" aria-labelledby="apps-heading" className="space-y-3">
          <h2 id="apps-heading" className="text-lg font-semibold">Your apps</h2>
          <AppsPanel />
        </section>
        {me?.provider === "LOCAL" && (
          <section aria-labelledby="password-heading" className="space-y-3">
            <h2 id="password-heading" className="text-lg font-semibold">Password</h2>
            <PasswordForm />
          </section>
        )}
      </div>
    </>
  );
}
