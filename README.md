# Virtual Clubroom — UNIX_CLUB

> Discover, join and run 42 Portugal's student clubs, and keep them alive across generations.

**Live demo:** <https://42clubroom.ensui.dev> · **Team:** UNIX_CLUB (see [Team](#team))

![Join a club in 3 clicks](docs/media/join-flow.gif)

## Why

Clubs are hard to find, their information is scattered, and a club disappears when its members graduate.
Virtual Clubroom gives every club its own space, makes joining a three-click flow, and keeps clubs alive through
co-ownership, ownership transfer, archival and history in eras.

## Features

| Subject requirement | Status | Where |
|---|---|---|
| Roles: owner, member, non-member, admin | ✅ | Every club page adapts to who is looking |
| Login with 42 intra, and with a password | ✅ | `/login` |
| Main landing page | ✅ | `/` |
| Join clubs through the site (open or on approval) | ✅ | Club page › **Join** |
| Club pages, and requesting a new club (admin approval) | ✅ | `/clubs/new`, `/c/<club>` |
| Posts with images, videos and audio | ✅ | Club › **Posts** |
| Comments with replies, and emoji reactions | ✅ | Under every post |
| Event calendar: per club and campus-wide | ✅ | `/calendar`, Club › **Events** |
| Ownership transfer and co-owners | ✅ | Club › Settings › **Ownership** |
| Club archival (everything stays readable) | ✅ | Admin › **All clubs** |
| Club history: eras, timeline, all past posts and comments | ✅ | Club › **History** |
| Community spaces with read/write rules | ✅ | Club sidebar |
| Extras in progress: custom club roles, RSVPs, real-time chat, notifications, revival | 🚧 | |

✅ done · 🚧 in progress

## Screenshots

| | |
|---|---|
| ![Landing page](docs/media/landing.png) | ![A club page](docs/media/club.png) |
| ![Posts with media](docs/media/posts.png) | ![Campus calendar](docs/media/calendar.png) |
| ![Club history](docs/media/history.png) | ![Admin console](docs/media/admin.png) |

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Modulith (a modular monolith whose module boundaries are verified by a test, events through a transactional outbox), Spring Security (OAuth2 login with 42, sessions in Redis), JPA and Flyway.
- **Frontend:** React 19, TypeScript, Vite, TanStack Query, React Router, Tailwind CSS v4 and shadcn/ui, FullCalendar.
- **Data and infrastructure:** PostgreSQL 17, Redis 8, SeaweedFS (S3-compatible storage), Docker Compose, nginx, Cloudflare Tunnel.

## Architecture

- **13 modules** (identity, club, membership, space, forum, media, calendar, discovery, history, …), each with its own database schema. Spring Modulith's test fails the build if one reaches into another's internals.
- **Events between modules** go through an outbox table in PostgreSQL: approving a club creates its owner, its spaces, its directory card and its first timeline entry, each in its own transaction, retried until it succeeds.
- **Stateless backend instances:** sessions live in Redis, so the demo runs two backend replicas behind nginx.
- **Media go straight to object storage:** the browser uploads with a presigned URL, and the backend only checks the result.
- **Read models** serve the public directory and the club timeline, so heavy reads never touch the write tables.

## Run it

### With Docker (production-like)

```bash
cp .env.example .env     # set APP_PUBLIC_URL=http://localhost:8080 and COOKIE_SECURE=false;
                         # FT_CLIENT_ID / FT_CLIENT_SECRET for Login with 42 (the password login works without)
deploy/seaweedfs/render-s3-config.sh      # storage credentials from .env (needs a non-default S3_SECRET_KEY)
docker compose -f compose.yaml -f compose.prod.yaml --profile demo up -d --build
```

Open <http://localhost:8080> and log in with the admin account from `.env` (`APP_BOOTSTRAP_ADMIN_USERNAME` / `APP_BOOTSTRAP_ADMIN_PASSWORD`).

### For development

```bash
cp .env.example .env
docker compose -f compose.yaml -f compose.dev.yaml up -d      # PostgreSQL, Redis, SeaweedFS
cd backend && ./run-dev.sh                                     # Spring Boot on :8080 (JDK 21)
cd frontend && npm ci && npm run dev                           # Vite on :5173
```

Tests: `./mvnw verify` in `backend/` (Testcontainers needs Docker), and `npm run lint && npm test && npm run build` in `frontend/`.

## Team

| Member | Main work |
|---|---|
| … | … |
