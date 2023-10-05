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
package org.apache.sling.servlets.get.impl.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Test;
import org.mockito.Mockito;

public class JsonObjectCreatorTest {

    private JsonValue createJsonValue(final Object value) {
        final Resource rsrc = Mockito.mock(Resource.class);
        final Map<String, Object> values = new HashMap<>();
        values.put("v", value);
        Mockito.when(rsrc.getValueMap()).thenReturn(new ValueMapDecorator(values));

        final JsonObjectCreator joc = new JsonObjectCreator(rsrc, false);

        return joc.create().build().get("v");
    }

    @Test public void testBoolean() {
        final JsonValue t = createJsonValue(Boolean.TRUE);
        assertEquals(JsonValue.TRUE, t);

        final JsonValue f = createJsonValue(Boolean.FALSE);
        assertEquals(JsonValue.FALSE, f);
    }

    @Test public void testBooleanArray() {
        final JsonValue t = createJsonValue(new boolean[] {true, false, true});
        assertEquals(ValueType.ARRAY, t.getValueType());
        assertEquals(3, t.asJsonArray().size());
        assertEquals(JsonValue.TRUE, t.asJsonArray().get(0));
        assertEquals(JsonValue.FALSE, t.asJsonArray().get(1));
        assertEquals(JsonValue.TRUE, t.asJsonArray().get(2));
    }

    @Test public void testNull() {
        final JsonValue t = createJsonValue(null);
        assertNull(t);
    }

    @Test public void testNullInArray() {
        final JsonValue t = createJsonValue(new String[] {null});
        assertEquals(ValueType.ARRAY, t.getValueType());
        assertEquals(1, t.asJsonArray().size());
        assertEquals("", ((JsonString)t.asJsonArray().get(0)).getString());
    }

}