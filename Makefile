PROJECT_VERSION := $(shell grep -m1 '<version>' pom.xml | sed -e 's/.*<version>\(.*\)<\/version>.*/\1/')

LOCAL_REPO := $(shell pwd)/target/local-repo

.PHONY: build
build:
	@echo "ℹ️🔢 Project version: $(PROJECT_VERSION)"
	@echo "👷 Building and installing project..."
	@mvn clean verify install && \
		echo "🧩 Testing with sample project" && \
		(cd sample-project && mvn -T4C -Dmaven.repo.local=$(LOCAL_REPO) -Dksp.plugin.version=$(PROJECT_VERSION) clean test) && \
		echo "✅ Done"

.PHONY: site
site:apidocs
	  mvn -Dgpg.sign=false clean site dokka:dokka -P release

.PHONY: apidocs
apidocs:
	  mvn dokka:dokka -P release

.PHONY: lint
lint:prepare
	  ktlint && \
    mvn spotless:check

# https://docs.openrewrite.org/recipes/maven/bestpractices
.PHONY:format
format:prepare
	  ktlint --format && \
  	mvn spotless:apply && \
	  mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
				-Drewrite.activeRecipes=org.openrewrite.maven.BestPractices \
				-Drewrite.exportDatatables=true

.PHONY:prepare
prepare:
	  brew install ktlint --quiet

.PHONY:all
all: format lint build

.PHONY:ci
ci:
	@echo "ℹ️🔢 Project version: $(PROJECT_VERSION)"
	@#pkill -f "kotlin-daemon"
	@echo "👷 Building project..."
	@mvn -Dgpg.skip=true clean verify site -P release && \
		echo "🧩 Testing with sample project" && \
		(cd sample-project && mvn -Dmaven.repo.local=$(LOCAL_REPO) -Dksp.plugin.version=$(PROJECT_VERSION) clean test) && \
		echo "🧩 Testing with arrow-optics-test" && \
		(cd test-projects/arrow-optics && mvn -Dmaven.repo.local=$(LOCAL_REPO) -Dksp.plugin.version=$(PROJECT_VERSION) clean test) && \
		echo "✅ Done"
.PHONY:sample
sample:
	@echo "🖼️ Building sample project"
	@(cd sample-project && mvn test)
