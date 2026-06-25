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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Phase 4.1 verification (Type-A, no Docker): proves the {@link BaseBlockRunner} skip guard turns a
 * recorded {@code bootError} into a {@link SkipException} (so the block's classes are SKIPPED, not FAILED)
 * and is a no-op when boot succeeded. Lives in the same package so it can invoke the package-private guard
 * directly, and uses the real {@link ITestContext} TestNG injects into each test (only its IAttributes
 * methods are touched — never the Guice-typed methods, which a hand-rolled proxy would force-resolve).
 */
public class BaseBlockRunnerVerificationTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseBlockRunnerVerificationTest.class);

    private static final class Probe extends BaseBlockRunner {
    }

    @Test
    public void guardSkipsWithBootErrorAsCauseWhenBootFailed(ITestContext context) {
        BaseBlockRunner runner = new Probe();
        RuntimeException bootError = new RuntimeException("simulated APIM boot/readiness failure");
        context.setAttribute(BaseBlockRunner.BOOT_ERROR_ATTRIBUTE, bootError);
        try {
            SkipException skip = Assert.expectThrows(SkipException.class,
                    () -> runner.abortIfBlockBootFailed(context));
            Assert.assertSame(skip.getCause(), bootError,
                    "skip cause must be the recorded boot error (single root cause, no NPE cascade)");
            logger.info("Guard converted a recorded bootError into a SkipException with the boot error "
                    + "as its cause");
        } finally {
            context.removeAttribute(BaseBlockRunner.BOOT_ERROR_ATTRIBUTE);
        }
    }

    @Test
    public void guardIsNoOpWhenBootSucceeded(ITestContext context) {
        context.removeAttribute(BaseBlockRunner.BOOT_ERROR_ATTRIBUTE);
        BaseBlockRunner runner = new Probe();
        runner.abortIfBlockBootFailed(context);   // must not throw
        logger.info("Guard was a no-op when no bootError was recorded (block proceeds normally)");
    }
}
