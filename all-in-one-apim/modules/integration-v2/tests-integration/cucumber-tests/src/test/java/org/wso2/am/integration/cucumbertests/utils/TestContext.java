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

package org.wso2.am.integration.cucumbertests.utils;

import java.util.HashMap;
import org.testng.ITestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestContext {

    // ThreadLocal to maintain a separate contextMap per thread
    private static final ThreadLocal<Map<String, Object>> threadLocalContext =
            ThreadLocal.withInitial(HashMap::new);
    private static final String DEFAULT_SHARED_SCOPE = "global-shared";
    private static final String DEFAULT_LOCAL_SCOPE = "global-local";

    /**
     * Derives the shared-scope id for a TestNG {@code <test>} block, namespaced by suite name so two
     * blocks of the same {@code <test name>} living in different suites cannot merge shared state.
     * This is the single source of truth for the shared-scope key — listeners that set the scope per
     * invocation and any onStart lifecycle wiring must both derive the key through this method.
     */
    public static String sharedScopeId(ITestContext ctx) {
        return ctx.getSuite().getName() + "::" + ctx.getName();
    }

    private static final Map<String, Map<String, Object>> sharedContexts = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> localContexts = new ConcurrentHashMap<>();
    private static final InheritableThreadLocal<ContextScope> currentScope = new InheritableThreadLocal<>();

    private static final class ContextScope {
        private final String sharedScopeId;
        private final String localScopeId;

        private ContextScope(String sharedScopeId, String localScopeId) {
            this.sharedScopeId = sharedScopeId;
            this.localScopeId = localScopeId;
        }
    }

    public static void setScope(String sharedScopeId, String localScopeId) {
        currentScope.set(new ContextScope(defaultIfBlank(sharedScopeId, DEFAULT_SHARED_SCOPE),
                defaultIfBlank(localScopeId, DEFAULT_LOCAL_SCOPE)));
    }

    public static void clearScope() {
        currentScope.remove();
    }

    /**
     * Number of distinct shared-scope maps currently retained. Test-observability only — lets the
     * scope-leak verification assert that finished blocks' entries are reclaimed (no per-block buildup).
     */
    public static int sharedScopeCount() {
        return sharedContexts.size();
    }

    /**
     * Number of distinct local-scope maps currently retained. Test-observability only — lets the
     * scope-leak verification assert that finished blocks' entries are reclaimed (no per-block buildup).
     */
    public static int localScopeCount() {
        return localContexts.size();
    }

    public static void set(String key, Object value) {
        threadLocalContext.get().put(key, value);
    }

    public static Object get(String key) {
        return threadLocalContext.get().get(key);
    }

    public static boolean contains(String key) {
        return threadLocalContext.get().containsKey(key);
    }

    public  static void remove(String key) {
        threadLocalContext.get().remove(key);
    }

    public static void clear() {
        threadLocalContext.get().clear();
    }
}
