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

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Base64;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsonRendererBinaryDataTest {

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private MockSlingHttpServletRequest request;
    private MockSlingHttpServletResponse response;

    @Before
    public void setup() {
        request = context.request();
        response = context.response();
    }

    /**
     * Default behavior: dump the length instead of the data
     */
    @Test
    public void testExportBinaryDataDisabled() throws IOException {
        byte[] bytes = "hello".getBytes();
        Resource resource = context.create().resource("/binary", "data", new ByteArrayInputStream(bytes));

        context.currentResource(resource);

        boolean exportBinaryData = false;
        JsonRenderer renderer = new JsonRenderer(1000, true, exportBinaryData);
        renderer.render(request, response);

        try (JsonReader jsonReader = Json.createReader(new StringReader(response.getOutputAsString()))) {
            JsonObject json = jsonReader.readObject();
            assertTrue("should have :data property", json.containsKey(":data"));

            JsonValue.ValueType valueType = json.get(":data").getValueType();
            assertEquals(":data should be a numeric property", JsonValue.ValueType.NUMBER, valueType);

            JsonNumber data = (JsonNumber) json.get(":data");
            assertEquals("the value of :data should be the length of the input data", bytes.length, data.intValue());
        }
    }

    /**
     * exportBinaryData is enabled. Dump the base64  data
     */
    @Test
    public void testExportBinaryDataEnabled() throws IOException {
        byte[] bytes = "hello".getBytes();
        Resource resource = context.create().resource("/binary", "data", new ByteArrayInputStream(bytes));

        context.currentResource(resource);

        boolean exportBinaryData = true;
        JsonRenderer renderer = new JsonRenderer(1000, true, exportBinaryData);
        renderer.render(request, response);

        try (JsonReader jsonReader = Json.createReader(new StringReader(response.getOutputAsString()))) {
            JsonObject json = jsonReader.readObject();
            assertTrue("should have :data property", json.containsKey(":data"));

            JsonValue.ValueType valueType = json.get(":data").getValueType();
            assertEquals(":data should be a base64 string", JsonValue.ValueType.STRING, valueType);

            JsonString data = (JsonString) json.get(":data");
            // test round-trip
            byte[] jsonBytes = Base64.getDecoder().decode(data.getString());
            assertArrayEquals("decoded bytes match the input", bytes, jsonBytes);
        }
    }
}