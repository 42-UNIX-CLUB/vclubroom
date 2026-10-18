import { PageHeader } from "@/shared/ui/PageHeader";
import { CreateClubWizard } from "./CreateClubWizard";
import { MyClubRequests } from "./MyClubRequests";

/** /clubs/new: the creation wizard, with the user's earlier requests alongside. */
export function CreateClubPage() {
  return (
    <>
      <PageHeader title="Start a club" description="Tell us about your club. An admin reviews it before it goes public." />
      <div className="grid items-start gap-6 lg:grid-cols-[1fr_20rem]">
        <CreateClubWizard />
        <MyClubRequests />
      </div>
    </>
  );
}