# eXeLearning integration for Liferay: developer entry point.
# Run "make help" for the list of targets and variables.

SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help

# Development settings (credentials, ports) live in .env, which Docker Compose,
# the Node helper scripts and Playwright read directly.
GRADLE ?= scripts/gradlew.sh
COMPOSE ?= docker compose
NODE_SCRIPT := node --env-file=.env scripts/liferay.mjs
VERSION ?=
EXELEARNING_EDITOR_REF ?= $(shell tr -d '[:space:]' < .editor-version)
EXELEARNING_EDITOR_REF_TYPE ?= tag
export EXELEARNING_EDITOR_REF EXELEARNING_EDITOR_REF_TYPE

JAVA_MODULES := :modules:exelearning-web :modules:exelearning-editor
gradle_tasks = $(foreach m,$(JAVA_MODULES),$(m):$(1))

WEB_JAR = modules/exelearning-web/build/libs/org.exelearning.liferay.web-*.jar
EDITOR_JAR = modules/exelearning-editor/build/libs/org.exelearning.liferay.editor-*.jar

.PHONY: help deps up upd down restart reset ps logs shell build build-frontend \
	build-java build-editor deploy seed health wait lint fix test-unit \
	test-integration test-e2e test-coverage test check package clean env-check \
	update-skills screenshots

help: ## Show this help
	@echo "eXeLearning for Liferay - make targets"
	@echo
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo "Variables:"
	@echo "  LIFERAY_URL        portal URL for scripts and tests (default http://localhost:LIFERAY_PORT)"
	@echo "  EXELEARNING_EDITOR_REF=$(EXELEARNING_EDITOR_REF)  EXELEARNING_EDITOR_REF_TYPE=$(EXELEARNING_EDITOR_REF_TYPE) (tag|branch|commit|release)"
	@echo "  VERSION=x.y.z      release version for 'make package'"
	@echo "  PLAYWRIGHT_ARGS=   extra arguments for Playwright (for example --headed)"

env-check:
	@test -f .env || { echo "Missing .env: run 'cp .env.example .env' and set the passwords." >&2; exit 1; }

deps: ## Install development dependencies (npm packages, Playwright Chromium, Gradle cache)
	npm ci
	npx playwright install chromium
	$(GRADLE) --console=plain -q :modules:exelearning-web:dependencies --configuration testRuntimeClasspath > /dev/null

up: env-check build ## Build, start Liferay and PostgreSQL, deploy the integration and wait until it is ready
	$(COMPOSE) up -d
	$(NODE_SCRIPT) wait
	$(MAKE) deploy

upd: env-check ## Start the containers in the background without building or waiting
	$(COMPOSE) up -d

down: ## Stop the containers; data volumes are preserved
	$(COMPOSE) down

restart: env-check ## Restart Liferay and wait until the integration is ready again
	$(COMPOSE) restart liferay
	$(NODE_SCRIPT) wait --integration

reset: ## DESTRUCTIVE: delete containers and all data volumes, then start from scratch
	$(COMPOSE) down -v --remove-orphans
	rm -rf build/deployed
	$(MAKE) up

ps: ## Show container status
	$(COMPOSE) ps

logs: ## Follow the Liferay log
	$(COMPOSE) logs -f --tail=200 liferay

shell: ## Open a shell in the Liferay container
	$(COMPOSE) exec liferay bash

build: build-editor build-frontend build-java ## Build all deployable components

build-frontend: ## Bundle the TypeScript custom elements
	npm run build

build-java: build-frontend ## Build the OSGi bundles (requires the editor build)
	$(GRADLE) --console=plain $(call gradle_tasks,jar)

build-editor: ## Build the static eXeLearning editor (cached per ref)
	scripts/build-editor.sh

deploy: env-check ## Copy changed bundles into the running Liferay and wait until active (FORCE=1 copies all)
	@ls $(WEB_JAR) $(EDITOR_JAR) >/dev/null || { echo "Bundles not built: run 'make build'." >&2; exit 1; }
	mkdir -p build/deployed
	for jar in $(EDITOR_JAR) $(WEB_JAR); do \
		name="$$(basename "$$jar")"; \
		sum="$$(shasum -a 256 "$$jar" | cut -d' ' -f1)"; \
		if [ "$$(cat "build/deployed/$$name" 2>/dev/null)" = "$$sum" ] && [ -z "$(FORCE)" ]; then echo "$$name unchanged"; continue; fi; \
		$(COMPOSE) exec -T liferay sh -c "cat > /tmp/$$name && mv /tmp/$$name /opt/liferay/deploy/$$name" < "$$jar"; \
		echo "$$sum" > "build/deployed/$$name"; \
	done
	$(COMPOSE) exec -T liferay sh -c 'for i in $$(seq 1 120); do ls /opt/liferay/deploy/*.jar >/dev/null 2>&1 || exit 0; sleep 1; done; echo "Bundles were not picked up from the deploy directory" >&2; exit 1'
	$(NODE_SCRIPT) wait --integration

seed: env-check ## Create the demo site, users, resource and pages (idempotent)
	$(NODE_SCRIPT) seed

health: env-check ## Check that the portal and every part of the integration respond
	$(NODE_SCRIPT) health

wait: env-check ## Wait until the portal answers (used before deploying)
	$(NODE_SCRIPT) wait

lint: ## Run every linter and formatting check (no changes)
	npx biome ci .
	npx tsc -p tsconfig.json --noEmit
	$(GRADLE) --console=plain $(call gradle_tasks,checkSourceFormatting) :modules:exelearning-web:compileTestJava
	shellcheck scripts/*.sh
	actionlint
	$(COMPOSE) --env-file .env.example config -q

fix: ## Apply automatic formatting fixes (Biome, Liferay Source Formatter)
	npx biome check --write .
	$(GRADLE) --console=plain $(call gradle_tasks,formatSource)

test-unit: ## Run Java and TypeScript unit tests
	npx vitest run
	$(GRADLE) --console=plain :modules:exelearning-web:test

test-coverage: ## Run unit tests and enforce 90% line coverage for Java and TypeScript
	npx vitest run --coverage
	$(GRADLE) --console=plain :modules:exelearning-web:jacocoTestCoverageVerification
	node scripts/coverage-summary.mjs

test-integration: env-check ## Run black-box integration tests against the running portal
	npx playwright test --project=integration $(PLAYWRIGHT_ARGS)

test-e2e: env-check ## Run Playwright end-to-end tests against the running portal
	npx playwright test --project=e2e-chromium $(PLAYWRIGHT_ARGS)

screenshots: env-check ## Regenerate docs/images against the running, seeded portal
	npx playwright test --project=screenshots $(PLAYWRIGHT_ARGS)

test: test-coverage test-integration test-e2e ## Run all test suites (requires the running stack)

check: lint test build ## Full local quality gate: lint, all tests and build

package: ## Build release artifacts: make package VERSION=x.y.z
	@test -n "$(VERSION)" || { echo "Usage: make package VERSION=x.y.z" >&2; exit 1; }
	scripts/package.sh "$(VERSION)"

update-skills: ## Update vendored agent skills from upstream (gh skill)
	gh skill update --all --dir .agents/skills

clean: ## Remove build outputs (keeps the editor source cache in .cache)
	$(GRADLE) --console=plain $(call gradle_tasks,clean)
	rm -rf frontend/dist build/reports dist test-results playwright-report
