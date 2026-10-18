// src/features/calendar/mockCalendar.ts
const clubs = ["42 Robotics", "Game Dev Club", "Linux Club", "Photo Club"];

export function mockCalendar(from: string, to: string, scope: "all" | "mine") {
  const items = [];
  for (let d = new Date(from); d < new Date(to); d.setDate(d.getDate() + 1)) {
    if (d.getDate() % 2 === 0) continue;

    const start = new Date(d);
    start.setHours(18, 0, 0, 0);
    const end = new Date(start);
    end.setHours(20);

    items.push({
      id: d.toISOString().slice(0, 10),
      clubName: clubs[d.getDate() % clubs.length],
      title: "Weekly meetup",
      start: start.toISOString(),
      end: end.toISOString(),
      status: d.getDate() % 7 === 0 ? "CANCELLED" : "SCHEDULED",
    });
  }
  return scope === "mine" ? items.filter((i) => i.clubName === "Linux Club") : items;
}
