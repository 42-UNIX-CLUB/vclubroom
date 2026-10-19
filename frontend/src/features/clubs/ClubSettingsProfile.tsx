import { zodResolver } from "@hookform/resolvers/zod";
import { ImageIcon } from "lucide-react";
import { useRef, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { type MediaPurpose, uploadMedia } from "@/features/media";
import { ApiError } from "@/shared/api/errors";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { Switch } from "@/shared/ui/switch";
import { Textarea } from "@/shared/ui/textarea";
import { type Club, type UpdateClubInput, useCategories, useUpdateClub } from "./api";
import { clubFormSchema, parseTags, type ClubFormValues } from "./clubForm";
import { useCurrentClub } from "./currentClub";

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;                 // app.media.limits for images (R-12)

function formValues(club: Club): ClubFormValues {
  return {
    name: club.name ?? "", tagline: club.tagline ?? "", category: club.category ?? "",
    description: club.description ?? "", tags: (club.tags ?? []).join(", "),
  };
}

/** Only what changed is sent (PATCH): another manager's edit of a different field is not undone. */
function changes(club: Club, values: ClubFormValues): UpdateClubInput {
  const body: UpdateClubInput = {};
  if (values.name.trim() !== club.name) body.name = values.name.trim();
  if (values.tagline.trim() !== (club.tagline ?? "")) body.tagline = values.tagline.trim();
  if (values.description.trim() !== (club.description ?? "")) body.description = values.description.trim();
  if (values.category !== club.category) body.category = values.category;
  const tags = parseTags(values.tags);
  if (tags.join(",") !== (club.tags ?? []).join(",")) body.tags = tags;
  return body;
}

/**
 * Club settings › Profile (E04-S04, 08-frontend §9.3), for CLUB_SETTINGS_MANAGE: the texts, the avatar and banner
 * (uploaded straight to storage, attached on save), and the join policy. Turning OPEN on lets everyone waiting in.
 */
export function ClubSettingsProfile() {
  const club = useCurrentClub();
  const { data: categories = [] } = useCategories();
  const update = useUpdateClub(club.id!);
  const form = useForm({ resolver: zodResolver(clubFormSchema), values: formValues(club), mode: "onTouched" });
  const [open, setOpen] = useState(club.joinPolicy === "OPEN");
  const [avatar, setAvatar] = useState<Picked | null>(null);
  const [banner, setBanner] = useState<Picked | null>(null);
  const uploading = avatar?.uploading || banner?.uploading;
  const { errors } = form.formState;

  const save = form.handleSubmit(async (values) => {
    const body = changes(club, values);
    if (avatar?.mediaId) body.avatarMediaId = avatar.mediaId;
    if (banner?.mediaId) body.bannerMediaId = banner.mediaId;
    if (open !== (club.joinPolicy === "OPEN")) body.joinPolicy = open ? "OPEN" : "APPROVAL";
    if (Object.keys(body).length === 0) {
      toast.info("Nothing to save.");
      return;
    }
    try {
      await update.mutateAsync(body);
      setAvatar(null);
      setBanner(null);
      toast.success(body.joinPolicy === "OPEN" ? "Saved. Anyone can join now, and everyone waiting is in." : "Saved.");
    } catch (error) {
      form.setError("root", { message: error instanceof ApiError && error.problem?.detail ? error.problem.detail : "Saving failed. Please try again." });
    }
  });

  return (
    <form onSubmit={save} noValidate className="max-w-2xl space-y-8">
      <section aria-labelledby="images-heading" className="space-y-4">
        <h2 id="images-heading" className="text-base font-semibold">Images</h2>
        <ImageField label="Avatar" purpose="CLUB_AVATAR" picked={avatar} onPicked={setAvatar}
          preview={(url) => <ClubAvatar name={club.name ?? ""} url={url ?? club.avatarUrl} size="lg" />} />
        <ImageField label="Banner" purpose="CLUB_BANNER" picked={banner} onPicked={setBanner}
          preview={(url) => (url ?? club.bannerUrl)
            ? <img src={url ?? club.bannerUrl!} alt="" className="h-20 w-48 rounded-lg object-cover" />
            : <div className="flex h-20 w-48 items-center justify-center rounded-lg bg-muted"><ImageIcon className="size-5 text-muted-foreground" aria-hidden="true" /></div>} />
      </section>

      <FieldGroup>
        <Field data-invalid={!!errors.name}>
          <FieldLabel htmlFor="name">Name</FieldLabel>
          <Input id="name" aria-invalid={!!errors.name} {...form.register("name")} />
          <FieldDescription>The address stays /c/{club.slug}.</FieldDescription>
          <FieldError errors={[errors.name]} />
        </Field>
        <Field data-invalid={!!errors.tagline}>
          <FieldLabel htmlFor="tagline">Tagline</FieldLabel>
          <Input id="tagline" aria-invalid={!!errors.tagline} {...form.register("tagline")} />
          <FieldError errors={[errors.tagline]} />
        </Field>
        <Controller
          control={form.control}
          name="category"
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="category">Category</FieldLabel>
              <Select value={field.value || null} onValueChange={(value) => field.onChange(value ?? "")}
                items={categories.map((category) => ({ value: category, label: category }))}>
                <SelectTrigger id="category" className="w-full" aria-invalid={fieldState.invalid} onBlur={field.onBlur}>
                  <SelectValue placeholder="Choose a category" />
                </SelectTrigger>
                <SelectContent>
                  {categories.map((category) => <SelectItem key={category} value={category}>{category}</SelectItem>)}
                </SelectContent>
              </Select>
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />
        <Field data-invalid={!!errors.description}>
          <FieldLabel htmlFor="description">Description</FieldLabel>
          <Textarea id="description" rows={8} aria-invalid={!!errors.description} {...form.register("description")} />
          <FieldDescription>Markdown works: **bold**, lists, [links](https://…).</FieldDescription>
          <FieldError errors={[errors.description]} />
        </Field>
        <Field data-invalid={!!errors.tags}>
          <FieldLabel htmlFor="tags">Tags</FieldLabel>
          <Input id="tags" aria-invalid={!!errors.tags} {...form.register("tags")} />
          <div className="flex flex-wrap gap-1">{parseTags(form.watch("tags")).map((tag) => <Badge key={tag} variant="secondary">#{tag}</Badge>)}</div>
          <FieldError errors={[errors.tags]} />
        </Field>
      </FieldGroup>

      <section aria-labelledby="joining-heading" className="space-y-2">
        <h2 id="joining-heading" className="text-base font-semibold">Joining</h2>
        <label className="flex items-center gap-3 text-sm">
          <Switch checked={open} onCheckedChange={setOpen} />
          Anyone can join, without approval
        </label>
        <p className="text-sm text-muted-foreground">
          {open
            ? "Students join with one click. Turning this on also lets in everyone who is waiting."
            : "Students ask to join, and members with the right role approve them in Join requests."}
        </p>
      </section>

      <FieldError errors={[errors.root]} />
      <Button type="submit" disabled={form.formState.isSubmitting || !!uploading}>
        {uploading ? "Uploading…" : form.formState.isSubmitting ? "Saving…" : "Save changes"}
      </Button>
    </form>
  );
}

interface Picked { previewUrl: string; mediaId?: string; uploading: boolean; progress: number }

/** One image: a preview, and a button that uploads the chosen file at once (it is attached when the form is saved). */
function ImageField({ label, purpose, picked, onPicked, preview }: {
  label: string;
  purpose: MediaPurpose;
  picked: Picked | null;
  onPicked: (picked: Picked | null) => void;
  preview: (url: string | undefined) => React.ReactNode;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  async function choose(file: File | undefined) {
    if (!file) return;
    setError(null);
    if (!file.type.startsWith("image/")) return setError("Choose an image.");
    if (file.size > MAX_IMAGE_BYTES) return setError("Too large: images can be up to 10 MB.");
    if (picked?.previewUrl) URL.revokeObjectURL(picked.previewUrl);
    const previewUrl = URL.createObjectURL(file);
    onPicked({ previewUrl, uploading: true, progress: 0 });
    try {
      const mediaId = await uploadMedia(file, purpose, (progress) => onPicked({ previewUrl, uploading: true, progress }));
      onPicked({ previewUrl, mediaId, uploading: false, progress: 100 });
    } catch {
      onPicked(null);
      setError("The upload failed. Please try again.");
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-4">
      {preview(picked?.previewUrl)}
      <div className="space-y-1">
        <p className="text-sm font-medium">{label}</p>
        <input ref={input} type="file" accept="image/*" className="hidden" tabIndex={-1} aria-label={`${label} file`}
          onChange={(event) => { void choose(event.target.files?.[0]); event.target.value = ""; }} />
        <Button type="button" variant="outline" size="sm" disabled={picked?.uploading} onClick={() => input.current?.click()}>
          {picked?.uploading ? `Uploading… ${picked.progress}%` : `Change ${label.toLowerCase()}`}
        </Button>
        {picked?.mediaId && <p className="text-xs text-muted-foreground">New {label.toLowerCase()} ready: save to use it.</p>}
        {error && <p role="alert" className="text-xs text-destructive">{error}</p>}
      </div>
    </div>
  );
}
