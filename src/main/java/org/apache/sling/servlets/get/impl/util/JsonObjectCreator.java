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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

public class JsonObjectCreator {

    /** Used to format date values */
    private static final String ECMA_DATE_FORMAT = "EEE MMM dd yyyy HH:mm:ss 'GMT'Z";

    /** The Locale used to format date values */
    static final Locale DATE_FORMAT_LOCALE = Locale.US;

    private Resource resource;

    private ValueMap valueMap;

    private boolean ecmaSupport;

    public JsonObjectCreator(Resource resource, boolean ecmaSupport) {
        this.resource = resource;
        this.valueMap = resource.getValueMap();
        this.ecmaSupport = ecmaSupport;
    }

    public JsonObjectBuilder create() {
        final JsonObjectBuilder obj = Json.createObjectBuilder();

        ValueMap valueMap = resource.getValueMap();
        if (valueMap.isEmpty()) {
            final String value = resource.adaptTo(String.class);
            if (value != null) {
                obj.add(resource.getName(), value.toString());
            } else {
                final String[] values = resource.adaptTo(String[].class);
                if (values != null) {
                    JsonArrayBuilder builder = Json.createArrayBuilder();
                    for (String v : values) {
                        builder.add(v);
                    }
                    obj.add(resource.getName(), builder);
                }
            }
            return obj;
        }

        final Iterator<Map.Entry<String, Object>> props = valueMap.entrySet().iterator();

        while (props.hasNext()) {
            final Map.Entry<String, Object> prop = props.next();
            if (prop.getValue() != null) {
                createProperty(obj, prop.getKey(), prop.getValue());
            }
        }

        return obj;
    }

    public static String formatEcma(final Calendar date) {
        DateFormat formatter = new SimpleDateFormat(ECMA_DATE_FORMAT, DATE_FORMAT_LOCALE);
        formatter.setTimeZone(date.getTimeZone());
        return formatter.format(date.getTime());
    }

    /** Dump only a value in the correct format */
    private JsonValue getValue(final Object value) {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        if (value instanceof InputStream) {
            // input stream is already handled
            builder.add("entry", 0);
        } else if (value instanceof Calendar) {
            if (ecmaSupport) {
                builder.add("entry", JsonObjectCreator.formatEcma((Calendar) value));
            } else {
                builder.add("entry", ISO8601.format(((Calendar) value)));
            }
        } else if (value instanceof Boolean) {
            builder.add("entry", (Boolean) value);
        } else if (value instanceof Long) {
            builder.add("entry", (Long) value);
        } else if (value instanceof Double) {
            builder.add("entry", (Double) value);
        } else if (value != null) {
            builder.add("entry", value.toString());
        } else {
            builder.add("entry", "");
        }
        return builder.build().get("entry");
    }

    /**
     * Write a single property
     */
    private void createProperty(final JsonObjectBuilder obj, final String key, final Object value) {
        Object[] values = null;
        if (value.getClass().isArray()) {
            final int length = Array.getLength(value);
            // write out empty array
            if (length == 0) {
                obj.add(key, Json.createArrayBuilder());
                return;
            }
            values = new Object[Array.getLength(value)];
            for (int i = 0; i < length; i++) {
                values[i] = Array.get(value, i);
            }
        }

        // special handling for binaries: we dump the length and not the data!
        if (value instanceof InputStream || (values != null && values[0] instanceof InputStream)) {
            // TODO for now we mark binary properties with an initial colon in
            // their name
            // (colon is not allowed as a JCR property name)
            // in the name, and the value should be the size of the binary data
            if (values == null) {
                obj.add(":" + key, getLength(-1, key, (InputStream) value));
            } else {
                final JsonArrayBuilder result = Json.createArrayBuilder();
                for (int i = 0; i < values.length; i++) {
                    result.add(getLength(i, key, (InputStream) values[i]));
                }
                obj.add(":" + key, result);
            }
            return;
        }

        if (!value.getClass().isArray()) {
            obj.add(key, getValue(value));
        } else {
            final JsonArrayBuilder result = Json.createArrayBuilder();
            for (Object v : values) {
                result.add(getValue(v));
            }
            obj.add(key, result);
        }
    }

    private long getLength(final int index, final String key, final InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignore) {
        }
        if (valueMap != null) {
            if (index == -1) {
                return valueMap.get(key, index);
            }
            Long[] lengths = valueMap.get(key, Long[].class);
            if (lengths != null && lengths.length > index) {
                return lengths[index];
            }
        }
        return -1;
    }
}
