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
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.value.BinaryValue;
import org.apache.jackrabbit.value.ValueHelper;
import org.apache.sling.api.SlingException;
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

    boolean exportBinaryData;

    /**
     * Create a new json object creator
     * @param resource The source
     * @param ecmaSupport ECMA date format for Calendar
     */
    public JsonObjectCreator(Resource resource, boolean ecmaSupport, boolean exportBinaryData) {
        this.resource = resource;
        this.valueMap = resource.getValueMap();
        this.ecmaSupport = ecmaSupport;
        this.exportBinaryData = exportBinaryData;
    }

    /**
     * Create the object builder
     * @return The object builder
     */
    public JsonObjectBuilder create() {
        final JsonObjectBuilder obj = Json.createObjectBuilder();

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
            createProperty(obj, prop.getKey(), prop.getValue());
        }

        return obj;
    }

    /**
     * Helper method to format a calendar
     * @param date The calendar
     * @return The formated output
     */
    public static String formatEcma(final Calendar date) {
        DateFormat formatter = new SimpleDateFormat(ECMA_DATE_FORMAT, DATE_FORMAT_LOCALE);
        formatter.setTimeZone(date.getTimeZone());
        return formatter.format(date.getTime());
    }

    /** Dump only a value in the correct format */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private JsonValue getValue(Object value) {
        if ( value instanceof Supplier ) {
            return getValue(((Supplier)value).get());
        }

        if (value == null) {
            return Json.createValue("");
        }
        if (value instanceof InputStream) {
            // input stream is already handled
            return Json.createValue(0);

        } else if (value instanceof Calendar) {
            if (ecmaSupport) {
                return Json.createValue(JsonObjectCreator.formatEcma((Calendar) value));
            } else {
                return Json.createValue(ISO8601.format(((Calendar) value)));
            }

        } else if (value instanceof Boolean) {
            final Boolean bool = (Boolean)value;
            return bool ? JsonValue.TRUE : JsonValue.FALSE;

        } else if (value instanceof Long) {
            return Json.createValue((Long) value);

        } else if (value instanceof Double) {

            return Json.createValue((Double) value);

        } else if (value instanceof String) {
            return Json.createValue((String) value);

        } else if (value instanceof Integer) {
            return Json.createValue((Integer) value);

        } else if (value instanceof Short) {
            return Json.createValue((Short) value);

        } else if (value instanceof Byte) {
            return Json.createValue((Byte) value);

        } else if (value instanceof Float) {
            return Json.createValue((Float) value);

        } else if (value instanceof Map) {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            for (final Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
                final JsonValue v = getValue(entry.getValue());
                builder.add(entry.getKey().toString(), v);
            }
            return builder.build();

        } else if (value instanceof Collection) {
            final JsonArrayBuilder builder = Json.createArrayBuilder();
            for (final Object obj : (Collection) value) {
                builder.add(getValue(obj));
            }
            return builder.build();

        } else if (value.getClass().isArray()) {
            final JsonArrayBuilder builder = Json.createArrayBuilder();
            for (int i = 0; i < Array.getLength(value); i++) {
                builder.add(getValue(Array.get(value, i)));
            }
            return builder.build();
        }
        return Json.createValue(value.toString());
    }

    /**
     * Write a single property
     */
    @SuppressWarnings("rawtypes")
    private void createProperty(final JsonObjectBuilder obj, final String key, final Object value) {
        if ( value == null ) {
            return;
        }
        if ( value instanceof Supplier ) {
            createProperty(obj, key, ((Supplier)value).get());
            return;
        }

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
                while ( values[i] instanceof Supplier ) {
                    values[i] = ((Supplier)values[i]).get();
                }
            }
        }

        // special handling for binaries: we dump the length and not the data!
        if (value instanceof InputStream || (values != null && values[0] instanceof InputStream)) {
            // TODO for now we mark binary properties with an initial colon in
            // their name
            // (colon is not allowed as a JCR property name)
            // in the name, and the value should be the size of the binary data
            if (values == null) {
                if(exportBinaryData){
                    try {
                        // Use the same code that Jackrabbit uses to export binary data in JCR XML
                        String attribute = ValueHelper.serialize(new BinaryValue((InputStream) value), false);
                        obj.add(":" + key, attribute);
                    } catch (RepositoryException e) {
                        throw new SlingException("Failed to encode " + key + " to base64", e);
                    }
                 } else {
                    obj.add(":" + key, getLength(-1, key, (InputStream) value));
                }
            } else {
                final JsonArrayBuilder result = Json.createArrayBuilder();
                for (int i = 0; i < values.length; i++) {
                    if(exportBinaryData){
                        try {
                            // Use the same code that Jackrabbit uses to export binary data in JCR XML
                            String attribute = ValueHelper.serialize(new BinaryValue((InputStream) values[i]), false);
                            obj.add(":" + key, attribute);
                        } catch (RepositoryException e) {
                            throw new SlingException("Failed to encode " + key + " to base64", e);
                        }
                    } else {
                        result.add(getLength(i, key, (InputStream) values[i]));
                    }
                }
                obj.add(":" + key, result);
            }
            return;
        }

        if (values != null ) {
            obj.add(key, getValue(values));
        } else {
            obj.add(key, getValue(value));
        }
    }

    private long getLength(final int index, final String key, final InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignore) {
        }
        if (index == -1) {
            return valueMap.get(key, index);
        }
        final Long[] lengths = valueMap.get(key, Long[].class);
        if (lengths != null && lengths.length > index) {
            return lengths[index];
        }
        return -1;
    }
}
