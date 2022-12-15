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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.Calendar;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonPatch;
import javax.json.JsonReader;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JsonRendererTest {

    @Rule
    public final SlingContext context = new SlingContext();

    private MockSlingHttpServletRequest request;
    private MockSlingHttpServletResponse response;

    private JsonRenderer jrs;

    @Before
    public void setup() {
        context.load().json("/data.json", "/content");
        request = context.request();
        response = context.response();

        context.currentResource("/content");

        jrs = new JsonRenderer(42, false);
    }

    @Test
    public void testRecursionLevelA() {
        context.requestPathInfo().setSelectorString("12");
        assertEquals(12, jrs.getMaxRecursionLevel(request));
        assertFalse(jrs.isTidy(request));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecursionLevelB() {
        context.requestPathInfo().setSelectorString("42.more");
        jrs.getMaxRecursionLevel(request);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecursionLevelC() {
        context.requestPathInfo().setSelectorString("more");
        jrs.getMaxRecursionLevel(request);
    }

    @Test
    public void testRecursionLevelD() {
        context.requestPathInfo().setSelectorString("tidy");
        assertEquals(0, jrs.getMaxRecursionLevel(request));
    }

    @Test
    public void testRecursionLevelE() {
        // Level must be the last selector but
        // if the last selector is "tidy" there's
        // no error. "for historical reasons"
        context.requestPathInfo().setSelectorString("46.tidy");
        assertEquals(0, jrs.getMaxRecursionLevel(request));
    }

    @Test
    public void testRecursionLevelF() {
        context.requestPathInfo().setSelectorString("tidy.45");
        assertEquals(45, jrs.getMaxRecursionLevel(request));
        assertTrue(jrs.isTidy(request));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecursionLevelNumeric() {
        context.requestPathInfo().setSelectorString("᭙");
        jrs.getMaxRecursionLevel(request);
    }

    @Test
    public void testRecursionLevelOverflow() {
        context.requestPathInfo().setSelectorString(Long.toString(((long) Integer.MAX_VALUE)  + 1L));
        assertEquals(-1, jrs.getMaxRecursionLevel(request));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecursionLevelUnderflow() {
        context.requestPathInfo().setSelectorString(Long.toString(((long) Integer.MIN_VALUE)  - 1L));
        jrs.getMaxRecursionLevel(request);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecursionLevelNegativ() {
        context.requestPathInfo().setSelectorString(Long.toString( - 2L));
        jrs.getMaxRecursionLevel(request);
    }

    @Test
    public void testRecursionLevelInfinity() {
        context.requestPathInfo().setSelectorString("infinity");
        assertEquals(-1, jrs.getMaxRecursionLevel(request));
    }

    @Test
    public void testRecursionLevelInfinityNumeric() {
        context.requestPathInfo().setSelectorString("-1");
        assertEquals(-1, jrs.getMaxRecursionLevel(request));
    }

    @Test
    public void testBadRequest() throws IOException {
        context.requestPathInfo().setSelectorString("bad.selectors");
        jrs.render(request, response);
        assertTrue(response.getStatus() == 400);
    }

    @Test
    public void testISO8601() throws IOException {
        context.requestPathInfo().setSelectorString("1");
        String created = getJsonFromReader().getString("created");
        Calendar cal = ISO8601.parse(created);
        // at the time of the test the offset it not preserved
        // if we did direct string comparison the time would be different
        // based on the testing environments time zone
        assertTrue(cal != null && ISO8601.getYear(cal) == 2016);
    }

    @Test
    public void testECMA() throws IOException {
        context.requestPathInfo().setSelectorString("1");
        JsonRenderer ecmajrs = new JsonRenderer(42, true);
        ecmajrs.render(request, response);
        String out = response.getOutputAsString();
        JsonObject job = Json.createReader(new StringReader(out)).readObject();
        String created = job.getString("created");
        assertTrue(created.startsWith("Mon Jan"));
    }

    @Test
    public void testBoolean() throws IOException {
        context.requestPathInfo().setSelectorString("1");
        assertTrue(getJsonFromReader().getBoolean("active"));
    }

    @Test
    // JSON impl only support Integers, JCR only supports Long values.
    public void testNumber() throws IOException {
        context.requestPathInfo().setSelectorString("1");
        assertTrue(getJsonFromReader().getInt("number") == 2);
    }

    @Test
    // TODO investigating SLING-7890 - needs cleanup once we find out exactly what's going on
    public void testBooleansNoTidy() throws IOException {
        context.currentResource("/content/booleans");
        final String expected = "{\"b2\":false,\"jcr:primaryType\":\"nt:unstructured\",\"s1\":\"true\",\"b1\":true,\"s2\":\"false\"}";
        JsonPatch diff;
        try(JsonReader jsonReader = Json.createReader(new StringReader(expected));
            JsonReader jsonReader1 = Json.createReader(new StringReader(getJsonFromRequestResponse()));
        ){
            JsonObject expectedObject = jsonReader.readObject();
            JsonObject targetObject = jsonReader1.readObject();
            diff = Json.createDiff(expectedObject, targetObject);
        }
        assertEquals("[]", diff.toString());
    }

    @Test
    // TODO investigating SLING-7890 - needs cleanup once we find out exactly what's going on
    public void testBooleansWithTidy() throws IOException {
        context.currentResource("/content/booleans");
        context.requestPathInfo().setSelectorString(".tidy");
        final String expected = "{\n" +
            "  \"b2\": false,\n" +
            "  \"jcr:primaryType\": \"nt:unstructured\",\n" +
            "  \"s1\": \"true\",\n" +
            "  \"b1\": true,\n" +
            "  \"s2\": \"false\"\n" +
            "  }";
        JsonPatch diff;
        try(JsonReader jsonReader = Json.createReader(new StringReader(expected));
            JsonReader jsonReader1 = Json.createReader(new StringReader(getJsonFromRequestResponse()));
        ){
            JsonObject expectedObject = jsonReader.readObject();
            JsonObject targetObject = jsonReader1.readObject();
            diff = Json.createDiff(expectedObject, targetObject);
        }
        assertEquals("[]", diff.toString());
    }

    private JsonObject getJsonFromReader() throws IOException {
        jrs.render(request, response);
        String out = response.getOutputAsString();
        JsonObject job = Json.createReader(new StringReader(out)).readObject();
        return job;
    }

    private String getJsonFromRequestResponse() throws IOException {
        final JsonRenderer renderer = new JsonRenderer(1000, true);
        renderer.render(request, response);
        return response.getOutputAsString();
    }

}