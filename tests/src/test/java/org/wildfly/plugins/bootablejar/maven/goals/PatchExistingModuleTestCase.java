/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugins.bootablejar.maven.goals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.metadata.ContentModification;

import org.junit.Test;
import org.wildfly.plugins.bootablejar.patching.ContentModificationUtils;
import org.wildfly.plugins.bootablejar.patching.Module;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.AS_DISTRIBUTION;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.FILE_SEPARATOR;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.RELATIVE_MODULES_PATH;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.RELATIVE_PATCHES_PATH;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.buildModulePatch;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.randomString;
import org.wildfly.plugins.bootablejar.patching.ResourceItem;

/**
 * @author jdenise
 */
public class PatchExistingModuleTestCase extends AbstractBootableJarMojoTestCase {

    private final boolean isJar;

    public PatchExistingModuleTestCase() {
        super("test15-pom.xml", true, null);
        isJar = true;
    }

     protected PatchExistingModuleTestCase(boolean isJar, String pomFile) {
        super(pomFile, true, null);
        this.isJar = isJar;
    }

    @Test
    public void testUpdateModulePatch()
            throws Exception {
        String patchid = randomString();
        String baseLayerPatchID = randomString();
        Path patchContentDir = createTestDirectory("patch-test-content", patchid);

        final String moduleName = "org.jboss.as.server";
        Path moduleDir = Paths.get(AS_DISTRIBUTION);
        moduleDir = moduleDir.resolve("modules").resolve("system").resolve("layers").
                resolve("base").resolve("org").resolve("jboss").resolve("as").resolve("server").resolve("main");
        assertTrue(Files.exists(moduleDir));
        Path moduleFile = moduleDir.resolve("module.xml");
        Module updatedModule = new Module.Builder(moduleName)
                .miscFile(new ResourceItem("res1", "new resource in the module".getBytes(StandardCharsets.UTF_8)))
                .originalModuleXml(moduleFile)
                .property("foo", "bar")
                .build();
        // create the patch with the updated module
        ContentModification moduleModified = ContentModificationUtils.modifyModule(patchContentDir.toFile(),
                baseLayerPatchID, HashUtils.hashFile(moduleDir.toFile()), updatedModule);
        final Path dir = getTestDir();
        buildModulePatch(patchContentDir, false, dir, patchid, moduleModified, baseLayerPatchID);
        BuildBootableJarMojo mojo = lookupMojo("package");
        assertNotNull(mojo);
        mojo.execute();
        Path home;
        if (isJar) {
            home = checkAndGetWildFlyHome(dir, true, true, null, null, mojo.recordState);
        } else {
            home = dir.resolve("target").resolve(SERVER_DEFAULT_DIR_NAME);
            checkWildFlyHome(home, 1, true, null, null, mojo.recordState);
        }
        try {
            // original module
            final String modulePath = home.toString() + FILE_SEPARATOR + RELATIVE_MODULES_PATH
                    + FILE_SEPARATOR + moduleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
            assertTrue(Files.exists(Paths.get(modulePath)));
            // The patched module
            final String patchedModulePath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                    + FILE_SEPARATOR + baseLayerPatchID + FILE_SEPARATOR + moduleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
            assertTrue(Files.exists(Paths.get(patchedModulePath)));
            if (isJar) {
                checkJar(dir, true, true, null, null, mojo.recordState);
            } else {
                checkServer(dir, SERVER_DEFAULT_DIR_NAME, 1, true, null, null, mojo.recordState);
            }
            checkDeployment(isJar, dir, true);
        } finally {
            BuildBootableJarMojo.deleteDir(home);
        }
    }
}
