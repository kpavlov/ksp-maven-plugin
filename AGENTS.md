# Info for AI Coding Agents

## Development Guidelines

- Use `git mv` command when renaming files to preserve git history, if possible
- Never commit or push changes to git automatically. It should be done manually.
- Ensure new code follows existing code style and design patterns.

### Code Style

#### Kotlin

- Follow Kotlin coding conventions
- Use the provided `.editorconfig` for consistent formatting
- Use Kotlin typesafe DSL builders where possible and prioritize fluent builders style over standard builder methods.
  If DSL builders produce less readable code, use standard setter methods.
- Prefer DSL builder style (method with lambda blocks) over constructors, if possible.
- Use Kotlin's `val` for immutable properties and `var` for mutable properties. Consider using `lateinit var` instead of nullable types, if possible.
- Use fully qualified imports instead of star imports
- Ensure to preserve backward compatibility when making changes
- Make code look like it is written by Kotlin Champion      

#### Java

- Use the provided `.editorconfig` for consistent formatting
- For Java code, prefer fluent DSL style over standard bean getters and setter methods
- Make code look like it is written by Java Champion

### Testing

- Write comprehensive tests for new features
- **Prioritize test readability**
- Avoid creating too many test methods. If multiple parameters can be tested in one scenario, go for it. Consider using parametrized tests.
- Use function `Names with backticks` for test methods in Kotlin, e.g. "fun `should return 200 OK`()"
- Avoid writing KDocs for tests, keep code self-documenting
- Write Kotlin tests with [kotlin-test](https://github.com/JetBrains/kotlin/tree/master/libraries/kotlin.test),
  [mockk](https://mockk.io/) and [Kotest-assertions](https://kotest.io/docs/assertions/assertions.html)
  with infix form assertions `shouldBe` instead of `assertEquals`.
- Use Kotest's `withClue("<failure reason>")` to describe failure reasons, but only when the assertion is NOT obvious.
  Remove obvious cases for simplicity.
- If multiple assertions are maid against nullable field, first check for null, e.g.: `params shoulNotBeNull { params.id shouldBe 1 }`
- Use `assertSoftly(subject) { ... }` to perform multiple assertions. Never use `assertSoftly { }` to verify properties
  of different subjects, or when there is only one assertion per subject. Avoid using `assertSoftly(this) { ... }`
  - When asked to write tests in Java: use JUnit5, Mockito, AssertJ core

### Documentation

- Update README files when adding new features
- Document API changes in the appropriate module's documentation
- Keep documentation concise and straight to the point.
- Always verify that documentation matches the code and is always truthful. Fix discrepancies between code and documentation.
- Write tutorial in Markdown format in README.md
- Make sure that in production code interfaces and abstract classes are properly documented. Avoid adding KDocs to override functions to avoid verbosity.
- Update KDocs when api is changed.
- When referring classes in KDoc, use references: `[SendMessageRequest]` instead of `SendMessageRequest`.
- Add brief code examples to KDoc
- Add links to specifications, if known. Double-check that the link actual and pointing exactly to the specification.
  Never add broken or not accurate links.

