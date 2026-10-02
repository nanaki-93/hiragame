.DEFAULT_GOAL := help

# Use the selected JDK 21 (JAVA_HOME) and the repository's pinned Gradle wrapper.
GRADLE ?= ./gradlew
GRADLE_ARGS ?= --no-daemon

.PHONY: help run stop build

help:
	@printf '%s\n' \
	  'make run    Start Hiragame and rebuild on source changes' \
	  '            http://localhost:8081/hiragame/' \
	  'make stop   Stop the Kobweb development server' \
	  'make build  Assemble the static production app' \
	  '' \
	  'Requires JDK 21. Set JAVA_HOME to select your JDK.'

run:
	$(GRADLE) $(GRADLE_ARGS) :site:kobwebStart --continuous

stop:
	$(GRADLE) $(GRADLE_ARGS) :site:kobwebStop

build:
	$(GRADLE) $(GRADLE_ARGS) :site:reorganizeOutput
