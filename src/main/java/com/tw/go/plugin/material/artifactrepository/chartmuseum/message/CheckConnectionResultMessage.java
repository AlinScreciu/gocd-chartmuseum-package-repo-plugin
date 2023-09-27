/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/
package com.tw.go.plugin.material.artifactrepository.chartmuseum.message;

import com.google.gson.annotations.Expose;

import java.util.List;

public class CheckConnectionResultMessage {

    @Expose
    private STATUS status;
    @Expose
    private List<String> messages;

    public CheckConnectionResultMessage(STATUS status, List<String> messages) {
        this.status = status;
        this.messages = messages;
    }

    public boolean success() {
        return STATUS.SUCCESS.equals(status);
    }

    public List<String> getMessages() {
        return messages;
    }

    public enum STATUS {SUCCESS, FAILURE}
}
