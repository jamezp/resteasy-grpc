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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import dev.resteasy.grpc.bridge.generator.protobuf.JavabufTranslatorGenerator;
import dev.resteasy.grpc.bridge.generator.protobuf.ReaderWriterGenerator;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "translate")
public class ProtoTranslateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String[] args2 = {
                Directories.resolveGeneratedSources(project).toString(),
                "dev.resteasy.grpc.example.CC1"
        };
        JavabufTranslatorGenerator.main(args2);
        final String[] args3 = {
                Directories.resolveGeneratedSources(project).toString(),
                "dev.resteasy.grpc.example.CC1_proto",
                "CC1"
        };
        ReaderWriterGenerator.main(args3);
    }
}
