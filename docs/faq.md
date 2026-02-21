# FAQ & Troubleshooting

## Kotlin Daemon Connection Failures

**Error:** `Failed to execute goal org.jetbrains.kotlin:kotlin-maven-plugin:X.X.X:compile: Compilation failure - Failed connecting to the daemon in 4 retries`

This error occurs when the Kotlin compiler daemon becomes unresponsive or fails to start. Common causes include:

- Resource exhaustion (memory, file handles)
- Stale daemon processes from previous builds
- Port/socket conflicts
- Corrupted daemon state

**Solutions:**

1. **Kill existing Kotlin daemon processes:**

   ```bash
   pkill -f "kotlin-daemon"
   ```

   Or manually find and kill:

   ```bash
   ps aux | grep kotlin-daemon | grep -v grep | awk '{print $2}' | xargs kill -9
   ```

2. **Clear daemon cache:**

   ```bash
   rm -rf ~/.kotlin/daemon
   ```

3. **Clean rebuild:**

   ```bash
   mvn clean compile
   ```

4. **Increase JVM memory** by setting environment variable:

   ```bash
   export MAVEN_OPTS="${MAVEN_OPTS} -Xmx2g"
   mvn compile
   ```

5. **Disable daemon temporarily** (fallback for debugging):

   ```bash
   mvn compile -Dkotlin.compiler.execution.strategy=in-process
   ```

6. **Run with verbose logging** to diagnose the issue:

   ```bash
   mvn compile -X
   ```
