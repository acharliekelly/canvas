.DEFAULT_GOAL := test

.PHONY: up down test test-backend test-frontend test-worker

up:
	docker compose up --build

down:
	docker compose down

test: test-backend test-frontend test-worker

test-backend:
	docker build --target test -t canvas-backend-test backend
	docker run --rm canvas-backend-test

test-frontend:
	docker build --target test -t canvas-frontend-test frontend
	docker run --rm canvas-frontend-test

test-worker:
	docker build --target test -t canvas-worker-test caption-worker
	docker run --rm canvas-worker-test
