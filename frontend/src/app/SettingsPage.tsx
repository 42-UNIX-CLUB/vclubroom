import { PasswordForm, useMe } from "@/features/auth";
import { PreferencesForm } from "@/features/notifications";
import { PageHeader } from "@/shared/ui/PageHeader";

/** /settings: my own settings, one section per feature (08-frontend §3). E11-S05 adds the calendar feed. */
export function SettingsPage() {
  const { data: me } = useMe();
  return (
    <>
      <PageHeader title="Settings" />
      <div className="max-w-2xl space-y-10">
        <section aria-labelledby="notifications-heading" className="space-y-3">
          <h2 id="notifications-heading" className="text-lg font-semibold">Notifications</h2>
          <p className="text-sm text-muted-foreground">Choose how you hear about each thing. Changes are saved as you make them.</p>
          <PreferencesForm />
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
