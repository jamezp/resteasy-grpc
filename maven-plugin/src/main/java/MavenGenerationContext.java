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

import java.nio.file.Path;

import dev.resteasy.grpc.bridge.generator.GenerationContext;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class MavenGenerationContext implements GenerationContext {
    private final Path sourceDir;
    private final Path targetDir;
    private final String packageName;
    private final String className;

    MavenGenerationContext(final Path sourceDir, final Path targetDir, final String packageName, final String className) {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.packageName = packageName;
        this.className = className;
    }

    @Override
    public Path sourceDirectory() {
        return sourceDir;
    }

    @Override
    public Path targetDirectory() {
        return targetDir;
    }

    @Override
    public String packageName() {
        return packageName;
    }

    @Override
    public String className() {
        return className;
    }
}
