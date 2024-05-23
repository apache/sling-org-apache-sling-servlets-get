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
package org.apache.sling.servlets.get.impl.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.servlethelpers.MockRequestDispatcherFactory;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import junitx.util.PrivateAccessor;

public class StreamRendererTest {

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    RequestDispatcher requestDispatcher;


    @Before
    public void setup() {
        context.create().resource("/abc.txt","prop","value");
        context.build().file("file.txt", this.getClass().getResourceAsStream("/samplefile.json"));
        requestDispatcher = Mockito.mock(RequestDispatcher.class);
        context.request().setRequestDispatcherFactory(new MockRequestDispatcherFactory() {
            @Override
            public RequestDispatcher getRequestDispatcher(String path, RequestDispatcherOptions options) {
                return requestDispatcher;
            }

            @Override
            public RequestDispatcher getRequestDispatcher(Resource resource, RequestDispatcherOptions options) {
                return requestDispatcher;
            }
        });
    }

    @Test
    public void testCopyRange() throws IOException {
        runTests(1234);
        runTests(4321);
    }

    @Test
    public void testResultingLength() throws IOException {
        final ByteArrayInputStream in = new ByteArrayInputStream("12345678".getBytes());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamRenderer.staticCopyRange(in, out, 2, 4);
        final String result = out.toString();
        assertEquals(2, result.length());
        assertEquals("34", result);
    }

    private void runTests(int randomSeed) throws IOException {
        final Random random = new Random(randomSeed);
        assertCopyRange(random, StreamRenderer.IO_BUFFER_SIZE * 2 + 42);
        assertCopyRange(random, StreamRenderer.IO_BUFFER_SIZE * 3);
        assertCopyRange(random, StreamRenderer.IO_BUFFER_SIZE);
        assertCopyRange(random, StreamRenderer.IO_BUFFER_SIZE - 1);
        assertCopyRange(random, random.nextInt(StreamRenderer.IO_BUFFER_SIZE));
        assertCopyRange(random, 42);
        assertCopyRange(random, 1);
    }

    private void assertCopyRange(Random random, int bufferSize) throws IOException {

        // generate some random test data
        final byte[] expected = new byte[bufferSize];
        random.nextBytes(expected);

        // Check some simple cases ...
        assertCopyRange(expected, 0, 0);
        assertCopyRange(expected, 0, 1);
        assertCopyRange(expected, 0, expected.length);

        // ... and a few randomly selected ones
        final int n = random.nextInt(100);
        for (int i = 0; i < n; i++) {
            final int a = random.nextInt(expected.length);
            final int b = random.nextInt(expected.length);
            assertCopyRange(expected, Math.min(a, b), Math.max(a, b));
        }
    }

    private void assertCopyRange(byte[] expected, int a, int b) throws IOException {
        assertCopyRange(expected, new ByteArrayInputStream(expected), a, b);
        // with BufferedInputStream
        assertCopyRange(expected, new BufferedInputStream(new ByteArrayInputStream(expected)), a, b);
        // without available()
        assertCopyRange(expected, new ByteArrayInputStream(expected) {
            @Override
            public synchronized int available() {
                return 0;
            }
        }, a, b);
        // with BufferedInputStream and without available()
        assertCopyRange(expected, new BufferedInputStream(new ByteArrayInputStream(expected) {
            @Override
            public synchronized int available() {
                return 0;
            }
        }), a, b);
        // with an input stream that does not return everything in read()
        assertCopyRange(expected, new ByteArrayInputStream(expected) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                // allow maximum of 10
                if (len > 10) {
                    len = 10;
                }
                return super.read(b, off, len);
            }
        }, a, b);
    }

    private void assertCopyRange(byte[] expected, InputStream input, int a, int b) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        StreamRenderer.staticCopyRange(input, output, a, b);

        byte[] actual = output.toByteArray();
        assertEquals(b - a, actual.length);
        for (int i = a; i < b; i++) {
            assertEquals(expected[i], actual[i - a]);
        }
    }

    @Test
    public void test_setHeaders() throws Throwable {

        final Resource resource = Mockito.mock(Resource.class);
        final SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);
        final ResourceMetadata meta = Mockito.mock(ResourceMetadata.class);
        final ServletContext sc = Mockito.mock(ServletContext.class);

        StreamRenderer streamRendererServlet = new StreamRenderer(true, new String[] { "/" }, sc);

        Mockito.when(resource.getResourceMetadata()).thenReturn(meta);
        PrivateAccessor.invoke(streamRendererServlet, "setHeaders",
                new Class[] { Resource.class, SlingHttpServletResponse.class }, new Object[] { resource, response });
        Mockito.verify(response, Mockito.times(1)).setContentType("application/octet-stream");
    }


    @Test
    public void test_render_file() throws IOException {

        StreamRenderer renderer = new StreamRenderer(true,null,null);
        context.request().setResource(context.resourceResolver().getResource("/file.txt"));
        renderer.render(context.request(), context.response());
        assertTrue(context.response().getOutputAsString().equals("not json"));
        assertEquals(HttpServletResponse.SC_OK, context.response().getStatus());

    }

    @Test
    public void test_render_file_directoryListing() throws IOException, ServletException {
        ServletContext sc = Mockito.mock(ServletContext.class);
        StreamRenderer renderer = new StreamRenderer(true,new String[] {"/"},sc);
        Resource root = context.resourceResolver().getResource("/");
        context.request().setResource(root);
        renderer.render(context.request(), context.response());
        assertEquals(HttpServletResponse.SC_OK, context.response().getStatus());
        Mockito.verify(requestDispatcher).include(Mockito.any(), Mockito.any());

    }

    @Test
    public void test_render_file_with_length_greater_than_range() throws IOException {
        StreamRenderer renderer = new StreamRenderer(true,null,null);
        context.request().setResource(context.resourceResolver().getResource("/file.txt"));
        context.request().setHeader("Range","bytes=0-8388607");
        renderer.render(context.request(), context.response());
        assertTrue(context.response().getOutputAsString().equals("not json"));
        assertEquals(HttpServletResponse.SC_OK, context.response().getStatus());

    }
    @Test
    public void test_render_file_with_length_less_than_range() throws IOException {
        StreamRenderer renderer = new StreamRenderer(true,null,null);
        context.request().setResource(context.resourceResolver().getResource("/file.txt"));
        context.request().setHeader("Range","bytes=0-2");
        renderer.render(context.request(), context.response());
        assertTrue(context.response().getOutputAsString().equals("not json".substring(0,3)));
        assertEquals(HttpServletResponse.SC_PARTIAL_CONTENT, context.response().getStatus());
    }

    @Test
    public void test_render_file_with_length_equal_to_range() throws IOException {
        StreamRenderer renderer = new StreamRenderer(true,null,null);
        context.request().setResource(context.resourceResolver().getResource("/file.txt"));
        context.request().setHeader("Range","bytes=0-7");
        renderer.render(context.request(), context.response());
        assertTrue(context.response().getOutputAsString().equals("not json"));
        assertEquals(HttpServletResponse.SC_PARTIAL_CONTENT, context.response().getStatus());
    }

}
