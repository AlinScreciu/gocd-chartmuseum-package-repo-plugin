/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/
package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

public class JsonUtil {

    // GoCD's package plugin API wants yyyy-MM-dd'T'HH:mm:ss.SSS'Z'. GoCD 26.1+ reads the 'Z' as UTC,
    // so timestamps are written in UTC rather than in the server's local time zone.
    private static final DateTimeFormatter GOCD_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new TypeAdapter<Date>() {
                @Override
                public void write(JsonWriter out, Date value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(GOCD_TIMESTAMP.format(value.toInstant()));
                    }
                }

                @Override
                public Date read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    String text = in.nextString();
                    try {
                        return Date.from(Instant.from(GOCD_TIMESTAMP.parse(text)));
                    } catch (DateTimeParseException e) {
                        // Only the previous revision's timestamp is read, and the plugin never uses it.
                        return null;
                    }
                }
            })
            .create();

    public static String toJsonString(Object object) {
        return GSON.toJson(object);
    }

    public static <T> T fromJsonString(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }
}
