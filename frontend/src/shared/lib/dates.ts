/** Every date is shown in Lisbon time, whatever the browser's time zone (08-frontend §10). */
const TIME_ZONE = "Europe/Lisbon";
const LOCALE = "en-GB";

const dateFormat = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TIME_ZONE, day: "numeric", month: "short", year: "numeric",
});
const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TIME_ZONE, day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit",
});
const timeFormat = new Intl.DateTimeFormat(LOCALE, { timeZone: TIME_ZONE, hour: "2-digit", minute: "2-digit" });
const relativeFormat = new Intl.RelativeTimeFormat(LOCALE, { numeric: "auto" });

export type DateInput = string | number | Date;

const toDate = (value: DateInput) => (value instanceof Date ? value : new Date(value));

/** "19 Oct 2026" */
export function formatDate(value: DateInput): string {
  return dateFormat.format(toDate(value));
}

/** "19 Oct 2026, 18:00" */
export function formatDateTime(value: DateInput): string {
  return dateTimeFormat.format(toDate(value));
}

/** "18:00" */
export function formatTime(value: DateInput): string {
  return timeFormat.format(toDate(value));
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["year", 31_536_000], ["month", 2_592_000], ["week", 604_800], ["day", 86_400], ["hour", 3_600], ["minute", 60],
];

/** "5 minutes ago", "yesterday", "in 2 hours"; under a minute: "now". */
export function formatRelative(value: DateInput, now: Date = new Date()): string {
  const seconds = Math.round((toDate(value).getTime() - now.getTime()) / 1000);
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) return relativeFormat.format(Math.round(seconds / size), unit);
  }
  return relativeFormat.format(0, "second");
}

const inputParts = new Intl.DateTimeFormat("en-CA", {
  timeZone: TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
});

/** An instant as the value of an <input type="datetime-local">, in Lisbon time: "2026-10-23T18:00". */
export function toLisbonInput(value: DateInput): string {
  const part = Object.fromEntries(inputParts.formatToParts(toDate(value)).map(({ type, value: v }) => [type, v]));
  return `${part.year}-${part.month}-${part.day}T${part.hour}:${part.minute}`;
}

/**
 * The value of an <input type="datetime-local">, read as Lisbon time, as an ISO instant. The input itself has no
 * time zone, and the browser's may not be Lisbon's: guess the UTC instant, then correct it by Lisbon's offset.
 */
export function fromLisbonInput(input: string): string {
  const [date = "", time = ""] = input.split("T");
  const [year, month, day] = date.split("-").map(Number);
  const [hour, minute] = time.split(":").map(Number);
  const wall = Date.UTC(year!, month! - 1, day!, hour!, minute!);
  const offset = (instant: number) => Date.parse(`${toLisbonInput(instant)}:00Z`) - instant;   // Lisbon minus UTC
  let instant = wall - offset(wall);
  instant = wall - offset(instant);                                       // right on both sides of a DST change
  return new Date(instant).toISOString();
}
