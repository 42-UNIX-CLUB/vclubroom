#!/usr/bin/env bash
# Starts the backend for local development against compose services (Mailpit for email, Vite origin).
set -euo pipefail
cd "$(dirname "$0")"
set -a; source ../.env; set +a
export APP_PUBLIC_URL=http://localhost:5173 S3_PUBLIC_ENDPOINT=http://localhost:5173 COOKIE_SECURE=false \
       SMTP_HOST=localhost SMTP_PORT=1025 SMTP_AUTH=false SMTP_STARTTLS=false SMTP_SSL=false
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
