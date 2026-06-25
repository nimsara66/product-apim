/*
 *  Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wso2.am.integration.cucumbertests.runners.block;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;

/**
 * Base class for runners in the parallel-on-shared-container lane. Each {@code <test>} block boots a
 * single APIM container in {@code BlockLifecycleListener.onStart} (Phase 4.2); every test class in the
 * block extends this runner to inherit the boot-failure skip guard below, so authors write no lifecycle
 * code of their own.
 *
 * <p>When {@code onStart} fails to boot/ready the block's container it records the cause as the
 * {@code bootError} attribute on the {@link ITestContext} instead of throwing. This guard then converts
 * that into a {@link SkipException} per class, so the block's classes are reported SKIPPED (with the boot
 * error as the single root cause) rather than FAILED with an NPE cascade from the absent container.
 */
public abstract class BaseBlockRunner extends AbstractTestNGCucumberTests {

    static final String BOOT_ERROR_ATTRIBUTE = "bootError";

    @BeforeClass(alwaysRun = true)
    void abortIfBlockBootFailed(ITestContext context) {
        Object bootError = context.getAttribute(BOOT_ERROR_ATTRIBUTE);
        if (bootError != null) {
            throw new SkipException("APIM block boot failed", (Throwable) bootError);
        }
    }
}
