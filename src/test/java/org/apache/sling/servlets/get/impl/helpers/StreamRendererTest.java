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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import junitx.util.PrivateAccessor;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.redirect.RedirectResolver;
import org.apache.sling.api.redirect.RedirectResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class StreamRendererTest {

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
    public void test_streamRedirect() throws Exception {
        final Resource resource = Mockito.mock(Resource.class);
        final SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
        final SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);
        final ResourceMetadata meta = Mockito.mock(ResourceMetadata.class);
        final ServletContext sc = Mockito.mock(ServletContext.class);
        final RequestPathInfo requestPathInfo = Mockito.mock(RequestPathInfo.class);

        StreamRenderer streamRendererServlet = new StreamRenderer(true, new String[] { "/" }, sc);

        Mockito.when(request.getRequestPathInfo()).thenReturn(requestPathInfo);
        Mockito.when(request.getResource()).thenReturn(resource);

        Mockito.when(resource.getResourceMetadata()).thenReturn(meta);
        List<String[]> headers = new ArrayList<>();
        headers.add(new String[] { "x-test", "header"});

        Mockito.when(resource.adaptTo(Mockito.eq(RedirectResolver.class))).thenReturn(
                new RedirectResolverForTesting("https://xyz.blobs.com//container/id",
                headers, 301));
        streamRendererServlet.render(request, response);

        Mockito.verify(response, Mockito.times(1)).setHeader("x-test", "header");
        Mockito.verify(response, Mockito.times(1)).setStatus(301);
        Mockito.verify(response, Mockito.times(1)).sendRedirect("https://xyz.blobs.com//container/id");

    }

    @Test
    public void test_streamNoRedirect() throws Exception {
        final Resource resource = Mockito.mock(Resource.class);
        final SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
        final SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);
        final ResourceMetadata meta = Mockito.mock(ResourceMetadata.class);
        final ServletContext sc = Mockito.mock(ServletContext.class);
        final RequestPathInfo requestPathInfo = Mockito.mock(RequestPathInfo.class);

        StreamRenderer streamRendererServlet = new StreamRenderer(true, new String[] { "/" }, sc);

        Mockito.when(request.getRequestPathInfo()).thenReturn(requestPathInfo);
        Mockito.when(request.getResource()).thenReturn(resource);

        Mockito.when(resource.getResourceMetadata()).thenReturn(meta);
        List<String[]> headers = new ArrayList<>();
        headers.add(new String[] { "x-test", "header"});

        Mockito.when(resource.adaptTo(Mockito.eq(RedirectResolver.class))).thenReturn(
                new RedirectResolverForTesting("https://xyz.blobs.com//container/id",
                        headers, RedirectResponse.NO_REDIRECT));

        try {
            streamRendererServlet.render(request, response);
            Assert.fail();
        } catch (NullPointerException e) {
            // after testing for stream render there will be a NPE as this test didnt mock everything else up
            // other tests verify non redirect behaviour already and the test preceeding this one
            // verifies redirect works.
            // Still need to verify that no redirect was set.
        }

        Mockito.verify(response, Mockito.times(0)).setHeader("x-test", "header");
        Mockito.verify(response, Mockito.times(0)).setStatus(301);
        Mockito.verify(response, Mockito.times(0)).sendRedirect("https://xyz.blobs.com//container/id");

    }


    // since this is a provider class, I want it to fail to compile if the RedirectResolver changes.
    protected class RedirectResolverForTesting implements RedirectResolver {
        private final String redirect;
        private final List<String[]> headers;
        private final int status;

        RedirectResolverForTesting(String redirect, List<String[]> headers, int status) {
            this.redirect = redirect;
            this.headers = headers;
            this.status = status;
        }

        @Override
        public void resolve(HttpServletRequest request, RedirectResponse redirectResponse) {
            for (String[] header: headers) {
                redirectResponse.setHeader(header[0], header[1]);
            }
            redirectResponse.setStatus(status);
            redirectResponse.setRedirect(redirect);
        }
    }

}
