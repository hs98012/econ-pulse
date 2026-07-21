#!/usr/bin/env bash
set -euo pipefail

echo "This command now delegates to the full local-only query-plan analysis." >&2
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/analyze-query-plans.sh"
