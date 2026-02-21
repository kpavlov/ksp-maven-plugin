package me.kpavlov.ksp.maven

import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException

/**
 * Public entry point for invoking [KspMojoExecutor] from Java Mojos.
 *
 * This facade exposes a single static [execute] method, keeping the
 * [internal][KspMojoExecutor] class hidden from Java callers and preventing
 * accidental direct instantiation outside this module.
 */
object KspMojoExecutors {
    /**
     * Executes KSP processing with the given parameters.
     *
     * @param params the mojo parameters providing project context and configuration
     * @throws MojoExecutionException if KSP execution fails
     * @throws MojoFailureException if KSP processing reports errors
     */
    @JvmStatic
    @Throws(MojoExecutionException::class, MojoFailureException::class)
    fun execute(params: KspMojoParameters) = KspMojoExecutor(params).execute()
}
