.PHONY: build
build:
	@mvn clean verify install && \
		(cd sample-project && mvn test)

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

.PHONY:
ci:
		mvn -Dgpg.skip=true verify site -P release && \
		mvn install -DskipTests && \
		(cd sample-project && mvn test)
