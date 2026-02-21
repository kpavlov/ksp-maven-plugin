package me.kpavlov.ksp.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Processes Kotlin test source files with KSP (Kotlin Symbol Processing).
 *
 * <p>This mojo executes during the generate-test-sources phase and runs KSP processors
 * on test source files to generate additional test sources, resources, and other artifacts.</p>
 *
 * <p>The generated test sources are automatically added to the test compilation classpath
 * unless {@code addGeneratedSourcesToCompile} is set to false.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This mojo is thread-safe and supports parallel Maven builds. Each execution creates
 * isolated KSP processing instances with no shared mutable state.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * <plugin>
 *   <groupId>me.kpavlov.ksp.maven</groupId>
 *   <artifactId>ksp-maven-plugin</artifactId>
 *   <executions>
 *     <execution>
 *       <goals>
 *         <goal>process-test</goal>
 *       </goals>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 *
 * @author Konstantin Pavlov
 * @since 0.1.0
 */
@Mojo(
        name = "process-test",
        defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class KspProcessTestSourcesMojo extends AbstractKspProcessMojo {

    /**
     * Skip KSP processing for test sources.
     *
     * <p>This can be set via the command line:
     * {@code mvn test -Dksp.skipTest=true}</p>
     *
     * @since 0.1.0
     */
    @Parameter(property = "ksp.skipTest", defaultValue = "false")
    private boolean skipTest;

    @Override
    public ProcessingScope getScope() {
        return ProcessingScope.TEST;
    }

    @Override
    protected boolean isSkip() {
        return skipTest;
    }
}
