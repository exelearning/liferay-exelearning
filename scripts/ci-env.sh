#!/usr/bin/env bash
# Creates a .env with random throwaway credentials for CI runs.
set -euo pipefail

cd "$(dirname "$0")/.."

random() {
	openssl rand -hex 16
}

sed \
	-e "s/^LIFERAY_ADMIN_PASSWORD=.*/LIFERAY_ADMIN_PASSWORD=Ci-$(random)/" \
	-e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(random)/" \
	-e "s/^SEED_USER_PASSWORD=.*/SEED_USER_PASSWORD=Ci-$(random)/" \
	.env.example > .env
