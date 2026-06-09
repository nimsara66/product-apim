/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.am.integration.cucumbertests.utils.listeners;

import org.slf4j.MDC;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.wso2.am.integration.cucumbertests.utils.TestContext;

public class TestNameMdcListener implements ITestListener, IInvokedMethodListener {

    @Override
    public void onStart(ITestContext context) {
        // Add name of the <test> set to MDC
        MDC.put("testName", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        MDC.remove("testName");
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        String testName = testResult.getTestContext().getName();
        String localScopeId = buildLocalScopeId(testName, testResult);

        MDC.put("testName", testName);
        TestContext.setScope(testName, localScopeId);
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        TestContext.clearScope();
        MDC.remove("testName");
    }

    private String buildLocalScopeId(String testName, ITestResult testResult) {
        Object instance = testResult.getInstance();
        String className = testResult.getTestClass() != null
                ? testResult.getTestClass().getName()
                : "unknown-class";
        int instanceId = instance != null ? System.identityHashCode(instance) : 0;
        return testName + "::" + className + "#" + instanceId;
    }
}
