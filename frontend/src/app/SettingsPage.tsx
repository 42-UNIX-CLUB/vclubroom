import { PreferencesForm } from "@/features/notifications";
import { PageHeader } from "@/shared/ui/PageHeader";

/** /settings: my own settings, one section per feature (08-frontend §3). E11-S05 adds the calendar feed. */
export function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" />
      <section aria-labelledby="notifications-heading" className="max-w-2xl space-y-3">
        <h2 id="notifications-heading" className="text-lg font-semibold">Notifications</h2>
        <p className="text-sm text-muted-foreground">Choose how you hear about each thing. Changes are saved as you make them.</p>
        <PreferencesForm />
      </section>
    </>
  );
}
