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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonReader;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.testing.sling.MockResource;
import org.apache.sling.commons.testing.sling.MockResourceResolver;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

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
        Mockito.when(config.aliases()).thenReturn(new String[] { "xml:pdf" });
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

    @Test
    public void verifyPrintWriterFlushesOutputStream() throws Exception {
        // GIVEN
        String expectedJsonString = "{\"resourceType\":\"page\"}";
        MockResourceResolver mockResourceResolver = new MockResourceResolver();
        Resource mockResource = new MockResource(mockResourceResolver, "/content/page", "page");
        mockResourceResolver.addResource(mockResource);

        final DefaultGetServlet.Config config = Mockito.mock(DefaultGetServlet.Config.class);
        Mockito.when(config.enable_json()).thenReturn(true);
        final ServletConfig servletConfig = Mockito.mock(ServletConfig.class);

        final DefaultGetServlet defaultGetServlet = new DefaultGetServlet() {
            @Override
            public ServletConfig getServletConfig() {
                return servletConfig;
            }
        };
        defaultGetServlet.activate(config);
        defaultGetServlet.init();

        RequestPathInfo requestPathInfo = Mockito.mock(RequestPathInfo.class);
        Mockito.when(requestPathInfo.getExtension()).thenReturn(DefaultGetServlet.EXT_JSON);
        Mockito.when(requestPathInfo.getSelectors()).thenReturn(new String[]{"-1"});

        RequestProgressTracker requestProgressTracker = Mockito.mock(RequestProgressTracker.class);

        SlingHttpServletRequest slingHttpServletRequest = Mockito.mock(SlingHttpServletRequest.class);
        Mockito.when(slingHttpServletRequest.getProtocol()).thenReturn("HTTP/1.1");
        Mockito.when(slingHttpServletRequest.getMethod()).thenReturn("GET");
        Mockito.when(slingHttpServletRequest.getResourceResolver()).thenReturn(mockResourceResolver);
        Mockito.when(slingHttpServletRequest.getResource()).thenReturn(mockResource);
        Mockito.when(slingHttpServletRequest.getRequestPathInfo()).thenReturn(requestPathInfo);
        Mockito.when(slingHttpServletRequest.getRequestProgressTracker()).thenReturn(requestProgressTracker);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(byteArrayOutputStream);

        SlingHttpServletResponse slingHttpServletResponse = Mockito.mock(SlingHttpServletResponse.class);
        Mockito.when(slingHttpServletResponse.getWriter()).thenReturn(printWriter);

        // WHEN
        defaultGetServlet.service(slingHttpServletRequest, slingHttpServletResponse);

        // THEN
        String actualJsonString = byteArrayOutputStream.toString();
        Assert.assertThat(actualJsonString, CoreMatchers.not(CoreMatchers.is("")));

        JsonReader expected = Json.createReader(new StringReader(expectedJsonString));
        JsonReader actual = Json.createReader(new StringReader(actualJsonString));
        Assert.assertThat(actual.read(), CoreMatchers.is(expected.read()));
    }
}
