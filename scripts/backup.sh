#!/usr/bin/env bash
# Backs up the production stack's data: the database (pg_dump), uploaded files (SeaweedFS) and the ticket tracker
# (Forgejo). Run it on the server, from the demo clone, by hand or from cron (09-deployment §12):
#   0 4 * * * /home/deploy/vclubroom/scripts/backup.sh >> /home/deploy/vclubroom-backups/backup.log 2>&1
# Each run writes BACKUP_DIR/<date-time>/ (removed again if the run fails) and deletes runs older than BACKUP_KEEP_DAYS. Copy BACKUP_DIR off the server
# too: a backup on the same disk doesn't survive losing the server.
set -euo pipefail
cd "$(dirname "$0")/.."                                    # → the repository root
PROJECT=${BACKUP_PROJECT:-vclubroom-demo}                  # deploy.sh's project name
BACKUP_DIR=${BACKUP_DIR:-$HOME/vclubroom-backups}          # outside the clone: deploy.sh refuses a dirty one
KEEP_DAYS=${BACKUP_KEEP_DAYS:-14}
compose() { docker compose -p "$PROJECT" -f compose.yaml -f compose.prod.yaml "$@"; }

out="$BACKUP_DIR/$(date +%F-%H%M%S)"
mkdir -p "$out"
trap '[ "$?" -eq 0 ] || { rm -rf "$out"; echo "✖ Backup failed: nothing kept from this run." >&2; }' EXIT
echo "Backing up $PROJECT into $out"

# The database, in pg_dump's custom format (pg_restore can restore all of it, or one table).
compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > "$out/database.dump"
[ -s "$out/database.dump" ] || { echo "✖ The database dump is empty." >&2; exit 1; }

# Volumes. `docker volume inspect` fails on a wrong name: `docker run -v` would create an empty volume and archive it.
# They're archived while the services run, so the copy is crash-consistent (like after a power cut), not quiesced.
for volume in seaweed forgejo; do
  name="${PROJECT}_${volume}"
  docker volume inspect "$name" >/dev/null || { echo "✖ No volume $name: is $PROJECT running?" >&2; exit 1; }
  docker run --rm -v "$name":/data:ro -v "$out":/backup alpine tar czf "/backup/$volume.tar.gz" -C /data .
done

ls -lh "$out"
# Only this script's own run folders (date +%F-%H%M%S): a mistyped BACKUP_DIR must not lose anything else.
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -mtime +"$KEEP_DAYS" \
  -name '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]' -exec rm -rf {} +
echo "✓ Backup done"
