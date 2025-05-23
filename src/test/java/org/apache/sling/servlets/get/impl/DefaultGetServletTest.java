/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.servlets.get.impl;

import java.lang.reflect.Field;
import java.util.Map;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DefaultGetServletTest {

    @Test
    public void testDisabledAlias() throws Exception {
        @SuppressWarnings("serial")
        final DefaultGetServlet servlet = new DefaultGetServlet() {
            public ServletContext getServletContext() {
                return null;
            }
        };
        final DefaultGetServlet.Config config = Mockito.mock(DefaultGetServlet.Config.class);
        Mockito.when(config.enable_html()).thenReturn(true);
        Mockito.when(config.enable_json()).thenReturn(true);
        Mockito.when(config.enable_xml()).thenReturn(false);
        Mockito.when(config.enable_txt()).thenReturn(false);
        Mockito.when(config.aliases()).thenReturn(new String[] {"xml:pdf"});
        servlet.activate(config);

        servlet.init();

        final Field field = DefaultGetServlet.class.getDeclaredField("rendererMap");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        final Map<String, Servlet> map = (Map<String, Servlet>) field.get(servlet);
        assertEquals(5, map.size());
        assertNotNull(map.get(DefaultGetServlet.EXT_RES));
        assertNotNull(map.get(DefaultGetServlet.EXT_HTML));
        assertNotNull(map.get(DefaultGetServlet.EXT_JSON));
        assertNotNull(map.get("pdf"));
        assertNotNull(map.get(null));
    }
}
