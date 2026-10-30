import { PasswordForm, useMe } from "@/features/auth";
import { CalendarFeedPanel } from "@/features/calendar";
import { PreferencesForm } from "@/features/notifications";
import { PageHeader } from "@/shared/ui/PageHeader";
import { ThemeToggle } from "@/shared/ui/ThemeToggle";

/** /settings: my own settings, one section per feature (08-frontend §3). */
export function SettingsPage() {
  const { data: me } = useMe();
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
