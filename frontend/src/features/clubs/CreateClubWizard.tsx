import { zodResolver } from "@hookform/resolvers/zod";
import { CircleCheckIcon } from "lucide-react";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { Link } from "react-router";
import { ApiError } from "@/shared/api/errors";
import { Badge } from "@/shared/ui/badge";
import { Button, buttonVariants } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";
import { Markdown } from "@/shared/ui/Markdown";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { StatusBadge, type ClubStatus } from "@/shared/ui/StatusBadge";
import { Textarea } from "@/shared/ui/textarea";
import { type Club, useCategories, useRequestClub } from "./api";
import { clubFormSchema, emptyClubForm, parseTags, toCreateClubRequest, type ClubFormValues } from "./clubForm";

const STEPS = [
  { title: "Basics", fields: ["name", "tagline", "category"] },
  { title: "Description and tags", fields: ["description", "tags"] },
  { title: "Preview", fields: [] },
] as const satisfies readonly { title: string; fields: readonly (keyof ClubFormValues)[] }[];

/** 08-frontend §9.3: basics → description and tags → preview → "submitted for review". */
export function CreateClubWizard() {
  const [step, setStep] = useState(0);
  const [submitted, setSubmitted] = useState<Club | null>(null);
  const form = useForm({ resolver: zodResolver(clubFormSchema), defaultValues: emptyClubForm, mode: "onTouched" });
  const requestClub = useRequestClub();

  async function next() {
    if (await form.trigger([...STEPS[step].fields])) setStep(step + 1);
  }

  const submit = form.handleSubmit(async (values) => {
    try {
      setSubmitted(await requestClub.mutateAsync(toCreateClubRequest(values)));
    } catch (error) {
      form.setError("root", {
        message: error instanceof ApiError && error.code === "TOO_MANY_PENDING_REQUESTS"
          ? "You already have 3 requests waiting for review. Wait for an admin's decision first."
          : error instanceof ApiError && error.problem?.detail ? error.problem.detail : "The request failed. Please try again.",
      });
    }
  });

  if (submitted) {
    return (
      <EmptyState
        icon={CircleCheckIcon}
        title="Request sent"
        description={`${submitted.name} is waiting for an admin's approval. Until then only you and the admins can see it.`}
        action={
          <div className="flex flex-col items-center gap-3">
            <StatusBadge status={submitted.status as ClubStatus} />
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => { form.reset(emptyClubForm); setStep(0); setSubmitted(null); }}>
                Request another club
              </Button>
              <Link to="/clubs" className={buttonVariants()}>Browse clubs</Link>
            </div>
          </div>
        }
      />
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardDescription>Step {step + 1} of {STEPS.length}</CardDescription>
        <CardTitle>{STEPS[step].title}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} noValidate className="space-y-6">
          {step === 0 && <BasicsStep form={form} />}
          {step === 1 && <DescriptionStep form={form} />}
          {step === 2 && <PreviewStep values={form.getValues()} />}
          <FieldError errors={[form.formState.errors.root]} />
          <div className="flex justify-between">
            <Button type="button" variant="ghost" disabled={step === 0} onClick={() => setStep(step - 1)}>Back</Button>
            {/* Distinct keys: React must not turn the clicked "Next" button into the submit button, or the
                browser submits the form on that very click and the preview step is skipped. */}
            {step < STEPS.length - 1 ? (
              <Button key="next" type="button" onClick={next}>Next</Button>
            ) : (
              <Button key="submit" type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? "Sending…" : "Submit for review"}
              </Button>
            )}
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

type WizardForm = ReturnType<typeof useForm<ClubFormValues>>;

function BasicsStep({ form }: { form: WizardForm }) {
  const { data: categories = [] } = useCategories();
  const { errors } = form.formState;
  return (
    <FieldGroup>
      <Field data-invalid={!!errors.name}>
        <FieldLabel htmlFor="name">Name</FieldLabel>
        <Input id="name" placeholder="Chess Club" aria-invalid={!!errors.name} {...form.register("name")} />
        <FieldError errors={[errors.name]} />
      </Field>
      <Field data-invalid={!!errors.tagline}>
        <FieldLabel htmlFor="tagline">Tagline</FieldLabel>
        <Input id="tagline" placeholder="Blitz every Friday at 18:00" aria-invalid={!!errors.tagline} {...form.register("tagline")} />
        <FieldDescription>One sentence shown on the club card. Optional.</FieldDescription>
        <FieldError errors={[errors.tagline]} />
      </Field>
      <Controller
        control={form.control}
        name="category"
        render={({ field, fieldState }) => (
          <Field data-invalid={fieldState.invalid}>
            <FieldLabel htmlFor="category">Category</FieldLabel>
            <Select
              value={field.value || null}
              onValueChange={(value) => field.onChange(value ?? "")}
              items={categories.map((category) => ({ value: category, label: category }))}
            >
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
    </FieldGroup>
  );
}

function DescriptionStep({ form }: { form: WizardForm }) {
  const { errors } = form.formState;
  const tags = parseTags(form.watch("tags"));
  return (
    <FieldGroup>
      <Field data-invalid={!!errors.description}>
        <FieldLabel htmlFor="description">Description</FieldLabel>
        <Textarea id="description" rows={8} placeholder="What do you do, when do you meet, who is it for?"
          aria-invalid={!!errors.description} {...form.register("description")} />
        <FieldDescription>Markdown works: **bold**, lists, [links](https://…). Optional.</FieldDescription>
        <FieldError errors={[errors.description]} />
      </Field>
      <Field data-invalid={!!errors.tags}>
        <FieldLabel htmlFor="tags">Tags</FieldLabel>
        <Input id="tags" placeholder="chess, board-games, beginners" aria-invalid={!!errors.tags} {...form.register("tags")} />
        <FieldDescription>Up to 10, separated by commas or spaces. They help people find the club.</FieldDescription>
        {tags.length > 0 && <div className="flex flex-wrap gap-1">{tags.map((tag) => <Badge key={tag} variant="secondary">#{tag}</Badge>)}</div>}
        <FieldError errors={[errors.tags]} />
      </Field>
    </FieldGroup>
  );
}

function PreviewStep({ values }: { values: ClubFormValues }) {
  const tags = parseTags(values.tags);
  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h2 className="text-xl font-semibold">{values.name.trim()}</h2>
        {values.tagline.trim() && <p className="text-muted-foreground">{values.tagline.trim()}</p>}
      </div>
      <div className="flex flex-wrap gap-1">
        <Badge>{values.category}</Badge>
        {tags.map((tag) => <Badge key={tag} variant="secondary">#{tag}</Badge>)}
      </div>
      {values.description.trim()
        ? <Markdown>{values.description}</Markdown>
        : <p className="text-sm text-muted-foreground">No description yet.</p>}
      <p className="rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">
        An admin reviews every new club. Until it is approved, only you and the admins can see it.
      </p>
    </div>
  );
}