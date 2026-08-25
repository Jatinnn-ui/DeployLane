#!/usr/bin/env bash
#
# Creates .env from .env.example with freshly generated secrets.
#
# DeployForge refuses to start without JWT_SECRET and ENCRYPTION_KEY, because GitHub tokens and
# environment variables are encrypted with the latter. Both are generated here with openssl.
#
# An existing .env is never overwritten: losing ENCRYPTION_KEY makes stored secrets unrecoverable.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
example="$root/.env.example"
target="$root/.env"

if [[ ! -f "$example" ]]; then
  echo "error: .env.example not found at $example" >&2
  exit 1
fi

if [[ -f "$target" && "${1:-}" != "--force" ]]; then
  echo ".env already exists. Refusing to overwrite it."
  echo "Losing ENCRYPTION_KEY makes every stored secret unrecoverable."
  echo "Re-run with --force only if you are certain."
  exit 1
fi

if ! command -v openssl >/dev/null 2>&1; then
  echo "error: openssl is required" >&2
  exit 1
fi

# HS256 needs at least 32 bytes; 48 gives margin. AES-256 needs exactly 32.
jwt_secret="$(openssl rand -base64 48 | tr -d '\n')"
encryption_key="$(openssl rand -base64 32 | tr -d '\n')"

python3 - "$example" "$target" "$jwt_secret" "$encryption_key" <<'PY' 2>/dev/null || {
  # Fall back to sed when python3 is unavailable. Uses | as the delimiter because base64 contains /.
  sed -e "s|^JWT_SECRET=.*|JWT_SECRET=${jwt_secret}|" \
      -e "s|^ENCRYPTION_KEY=.*|ENCRYPTION_KEY=${encryption_key}|" \
      "$example" > "$target"
}
import sys

source, target, jwt_secret, encryption_key = sys.argv[1:5]
lines = []
with open(source, encoding='utf-8') as handle:
    for line in handle:
        if line.startswith('JWT_SECRET='):
            lines.append(f'JWT_SECRET={jwt_secret}\n')
        elif line.startswith('ENCRYPTION_KEY='):
            lines.append(f'ENCRYPTION_KEY={encryption_key}\n')
        else:
            lines.append(line)
with open(target, 'w', encoding='utf-8') as handle:
    handle.writelines(lines)
PY

chmod 600 "$target"

cat <<'EOF'

Wrote .env with generated JWT_SECRET and ENCRYPTION_KEY.

Next steps:
  1. Create a GitHub OAuth app and set GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET.
     Callback URL: http://localhost:8080/api/v1/auth/github/callback
  2. docker compose up -d postgres redis
  3. cd backend && ./mvnw spring-boot:run
  4. cd frontend && npm install && npm run dev

Back up ENCRYPTION_KEY. Without it, stored GitHub tokens and environment variables
cannot be decrypted.
EOF
