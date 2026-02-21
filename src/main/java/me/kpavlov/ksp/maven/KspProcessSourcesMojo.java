package me.kpavlov.ksp.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Processes Kotlin source files with KSP (Kotlin Symbol Processing) for main sources.
 *
 * <p>This mojo executes during the generate-sources phase and runs KSP processors
 * on main source files to generate additional source code, resources, and other artifacts.</p>
 *
 * <p>The generated sources are automatically added to the compilation classpath unless
 * {@code addGeneratedSourcesToCompile} is set to false.</p>
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
 *         <goal>process</goal>
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
        name = "process",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
public class KspProcessSourcesMojo extends AbstractKspProcessMojo {

    /**
     * Skip KSP processing for main sources.
     *
     * <p>This can be set via the command line:
     * {@code mvn compile -Dksp.skip=true}</p>
     *
     * @since 0.1.0
     */
    @Parameter(property = "ksp.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public ProcessingScope getScope() {
        return ProcessingScope.MAIN;
    }

    @Override
    protected boolean isSkip() {
        return skip;
    }
}
