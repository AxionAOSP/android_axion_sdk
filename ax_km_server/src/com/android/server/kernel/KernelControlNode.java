/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.server.kernel;

import android.content.ContentResolver;
import android.os.FileUtils;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.internal.kernel.AxKernelControl;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

final class KernelControlNode {
    final String id;
    final String group;
    final int type;
    final String path;
    final int defaultValue;
    final int[] availableValues;
    final String[] valueLabels;
    final String[] writeValues;

    KernelControlNode(String id, String group, int type, String path, int defaultValue, int[] availableValues) {
        this(id, group, type, path, defaultValue, availableValues, new String[0], new String[0]);
    }

    KernelControlNode(String id, String group, int type, String path, int defaultValue,
            int[] availableValues, String[] valueLabels, String[] writeValues) {
        this.id = Objects.requireNonNull(id, "Control id must not be null");
        this.group = TextUtils.isEmpty(group) ? "Default" : group;
        this.type = type;
        this.path = Objects.requireNonNull(path, "Path must not be null");
        this.defaultValue = defaultValue;
        this.availableValues = availableValues != null ? availableValues : new int[0];
        this.valueLabels = valueLabels != null ? valueLabels : new String[0];
        this.writeValues = writeValues != null ? writeValues : new String[0];
    }

    boolean canUse() {
        return !TextUtils.isEmpty(path) && new File(path).exists();
    }

    int coerce(int value) {
        if (availableValues.length == 0) return value;
        for (int val : availableValues) {
            if (val == value) return value;
        }
        return defaultValue;
    }

    String toFileValue(int value) {
        for (int i = 0; i < availableValues.length && i < writeValues.length; i++) {
            if (availableValues[i] == value) return writeValues[i];
        }
        return Integer.toString(value);
    }

    void writeValue(int value) throws IOException {
        FileUtils.stringToFile(path, toFileValue(value));
    }

    AxKernelControl snapshot(ContentResolver resolver) {
        if (!canUse() || TextUtils.isEmpty(id)) return null;
        int current = getSavedValue(resolver);
        return new AxKernelControl(id, group, type, defaultValue, current, availableValues, valueLabels);
    }

    private int getSavedValue(ContentResolver resolver) {
        if (TextUtils.isEmpty(id)) {
            return defaultValue;
        }
        int saved = Settings.Secure.getIntForUser(resolver, id, Integer.MIN_VALUE, UserHandle.USER_CURRENT);
        if (saved != Integer.MIN_VALUE) return coerce(saved);
        if (id.startsWith("axion_")) {
            String alias = id.replace("axion_", "little_");
            saved = Settings.Secure.getIntForUser(resolver, alias, Integer.MIN_VALUE, UserHandle.USER_CURRENT);
            if (saved != Integer.MIN_VALUE) return coerce(saved);
        } else if (id.startsWith("little_")) {
            String alias = id.replace("little_", "axion_");
            saved = Settings.Secure.getIntForUser(resolver, alias, Integer.MIN_VALUE, UserHandle.USER_CURRENT);
            if (saved != Integer.MIN_VALUE) return coerce(saved);
        }
        return defaultValue;
    }

    int readValue() {
        String val = AxKernelUtils.readSysfsString(path);
        for (int i = 0; i < writeValues.length && i < availableValues.length; i++) {
            if (writeValues[i].equals(val)) return availableValues[i];
        }
        return AxKernelUtils.readSysfsInt(path, defaultValue);
    }
}
