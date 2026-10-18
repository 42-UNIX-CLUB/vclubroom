import { z } from "zod";
import type { CreateClubInput } from "./api";

const TAG = /^[a-z0-9-]{2,24}$/;

/** "Chess, #board-games  chess" → ["chess", "board-games"]: lowercase, no "#", no duplicates. */
export function parseTags(input: string): string[] {
  const tags = input.split(/[\s,]+/).map((tag) => tag.trim().toLowerCase().replace(/^#/, "")).filter(Boolean);
  return [...new Set(tags)];
}

/** Same limits as the backend's CreateClubRequest, so most mistakes are caught before submitting. */
export const clubFormSchema = z.object({
  name: z.string().trim().min(3, "Use at least 3 characters.").max(60, "Use at most 60 characters."),
  tagline: z.string().trim().max(140, "Use at most 140 characters."),
  category: z.string().min(1, "Choose a category."),
  description: z.string().trim().max(5000, "Use at most 5000 characters."),
  tags: z.string()
    .refine((value) => parseTags(value).length <= 10, "Use at most 10 tags.")
    .refine((value) => parseTags(value).every((tag) => TAG.test(tag)),
      "Tags are 2–24 lowercase letters, digits or hyphens."),
});

export type ClubFormValues = z.input<typeof clubFormSchema>;

export const emptyClubForm: ClubFormValues = { name: "", tagline: "", category: "", description: "", tags: "" };

export function toCreateClubRequest(values: ClubFormValues): CreateClubInput {
  return {
    name: values.name.trim(),
    tagline: values.tagline.trim() || undefined,
    description: values.description.trim() || undefined,
    category: values.category,
    tags: parseTags(values.tags),
  };
}