#!/usr/bin/env bash
# Writes deploy/seaweedfs/s3.prod.json from S3_ACCESS_KEY / S3_SECRET_KEY in .env (deploy.sh runs it).
# compose.prod.yaml mounts that file instead of the committed s3.json, whose default secret is public on GitHub:
# with it, anyone could sign uploads or deletions through the demo's /media-* paths.
set -euo pipefail
cd "$(dirname "$0")/../.."                    # → the repository root
value() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | sed -E 's/^"(.*)"$/\1/'; }
access=$(value S3_ACCESS_KEY); secret=$(value S3_SECRET_KEY)
if [ -z "$access" ] || [ -z "$secret" ]; then echo "S3_ACCESS_KEY and S3_SECRET_KEY must be set in .env" >&2; exit 1; fi
if [ "$secret" = "change-me-s3-secret" ]; then echo "S3_SECRET_KEY is still the public default: generate one (openssl rand -hex 24)" >&2; exit 1; fi
if [[ ! "$access$secret" =~ ^[A-Za-z0-9._-]+$ ]]; then echo "Use only letters, digits, '.', '_' and '-' in the S3 keys" >&2; exit 1; fi
umask 077
cat > deploy/seaweedfs/s3.prod.json <<JSON
{
  "identities": [
    {
      "name": "vclubroom-backend",
      "credentials": [{ "accessKey": "$access", "secretKey": "$secret" }],
      "actions": ["Admin", "Read", "Write", "List", "Tagging"]
    },
    {
      "name": "anonymous",
      "actions": ["Read:media-public"]
    }
  ]
}
JSON
chmod 644 deploy/seaweedfs/s3.prod.json       # read by the SeaweedFS container user
echo "wrote deploy/seaweedfs/s3.prod.json"
