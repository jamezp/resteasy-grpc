/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import dev.resteasy.grpc.bridge.generator.Generator;
import dev.resteasy.grpc.bridge.generator.ServiceGrpcExtender;
import dev.resteasy.grpc.bridge.generator.protobuf.NewJavaToProtobufGenerator;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "generate")
public class ProtoCompileMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(alias = "package-name", property = "resteasy.grpc.package.name", required = true)
    private String packageName;

    @Parameter(alias = "class-name", property = "resteasy.grpc.class.name", required = true)
    private String className;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Get the source directory
        final List<String> compileSourceRoots = project.getCompileSourceRoots();
        for (String sourceRoot : compileSourceRoots) {
            final var context = new MavenGenerationContext(Path.of(sourceRoot), Path.of(project.getBuild().getDirectory()),
                    packageName, className);
            // Generate a .proto file for each required source file
            final Generator generator = new NewJavaToProtobufGenerator();
            try {
                generator.generate(context);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to generate the protobuf files.", e);
            }

            final String[] args4 = {
                    "CC1",
                    "GrpcServlet",
                    "dev.resteasy.grpc.example",
                    project.getBuild().getSourceDirectory() + File.separator + ".." + File.separator + "proto",
                    Directories.resolveGeneratedSources(project).toString()
            };
            ServiceGrpcExtender.main(args4);
        }
        // Add the generated sources directory
        project.addCompileSourceRoot(Directories.resolveGeneratedSources(project).toString());
        project.addCompileSourceRoot(Directories.resolveGeneratedSources(project).resolve("..").resolve("java").toString());
    }
}
