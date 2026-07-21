.DEFAULT_GOAL := test

.PHONY: up down test test-backend test-frontend test-worker test-infrastructure

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
