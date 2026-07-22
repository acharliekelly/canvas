.DEFAULT_GOAL := verify

ifneq ($(wildcard caption-worker/.venv/bin/python),)
export PATH := $(CURDIR)/caption-worker/.venv/bin:$(PATH)
endif

.PHONY: up down test test-backend test-frontend test-worker test-infrastructure verify e2e \
	e2e-persistence-check reset-local-data

up:
	docker compose up --build

down:
	docker compose down

test: test-backend test-frontend test-worker test-infrastructure

test-backend:
	cd backend && ./mvnw test

test-frontend:
	docker build --target test -t canvas-frontend-test frontend
	docker run --rm canvas-frontend-test

test-worker:
	docker build --target test -t canvas-worker-test caption-worker
	docker run --rm canvas-worker-test

test-infrastructure:
	sh infrastructure/minio/configuration-test.sh

verify:
	cd backend && ./mvnw verify
	cd frontend && npm ci && npm run lint && npm run typecheck && npm test -- --run && npm run build
	cd caption-worker && python -m pytest -q
	$(MAKE) test-infrastructure
	docker compose config --quiet

e2e:
	@set -eu; \
		trap 'docker compose stop' EXIT INT TERM; \
		docker compose up --build --wait; \
		if [ -f .env ]; then set -a; . ./.env; set +a; fi; \
		cd frontend; \
		npm ci; \
		npm run e2e

e2e-persistence-check:
	docker compose up --wait --no-build
	@set -eu; \
		if [ -f .env ]; then set -a; . ./.env; set +a; fi; \
		cd frontend; \
		npm ci; \
		npm run e2e:persistence

reset-local-data:
	@if [ "$(CONFIRM)" != "1" ]; then \
		echo "Refusing to delete local PostgreSQL and MinIO volumes. Re-run with CONFIRM=1." >&2; \
		exit 1; \
	fi
	docker compose down --volumes --remove-orphans
