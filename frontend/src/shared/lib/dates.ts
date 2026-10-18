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