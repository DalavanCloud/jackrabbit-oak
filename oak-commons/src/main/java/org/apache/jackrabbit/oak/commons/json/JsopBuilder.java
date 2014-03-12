/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.commons.json;

/**
 * A builder for Json and Jsop strings. It encodes string values, and knows when
 * a comma is needed. A comma is appended before '{', '[', a value, or a key;
 * but only if the last appended token was '}', ']', or a value. There is no
 * limit to the number of nesting levels.
 */
public class JsopBuilder implements JsopWriter {

    private static final boolean JSON_NEWLINES = false;

    private StringBuilder buff = new StringBuilder();
    private boolean needComma;
    private int lineLength, previous;

    /**
     * Resets this instance.
     */
    @Override
    public void resetWriter() {
        needComma = false;
        buff.setLength(0);
    }

    @Override
    public void setLineLength(int length) {
        lineLength = length;
    }

    /**
     * Append all entries of the given buffer.
     *
     * @param buffer the buffer
     * @return this
     */
    @Override
    public JsopBuilder append(JsopWriter buffer) {
        appendTag(buffer.toString());
        return this;
    }

    /**
     * Append a Jsop tag character.
     *
     * @param tag the string to append
     * @return this
     */
    @Override
    public JsopBuilder tag(char tag) {
        buff.append(tag);
        needComma = false;
        return this;
    }

    /**
     * Append an (already formatted) Jsop tag. This will allow to append
     * non-Json data. This method resets the comma flag, so no comma is added
     * before the next key or value.
     *
     * @param string the string to append
     * @return this
     */
    private JsopBuilder appendTag(String string) {
        buff.append(string);
        needComma = false;
        return this;
    }

    /**
     * Append a newline character.
     *
     * @return this
     */
    @Override
    public JsopBuilder newline() {
        buff.append('\n');
        return this;
    }

    /**
     * Append '{'. A comma is appended first if needed.
     *
     * @return this
     */
    @Override
    public JsopBuilder object() {
        optionalCommaAndNewline(1);
        buff.append('{');
        needComma = false;
        return this;
    }

    /**
     * Append '}'.
     *
     * @return this
     */
    @Override
    public JsopBuilder endObject() {
        if (JSON_NEWLINES) {
            buff.append("\n}");
        } else {
            buff.append('}');
        }
        needComma = true;
        return this;
    }

    /**
     * Append '['. A comma is appended first if needed.
     *
     * @return this
     */
    @Override
    public JsopBuilder array() {
        optionalCommaAndNewline(1);
        buff.append('[');
        needComma = false;
        return this;
    }

    /**
     * Append ']'.
     *
     * @return this
     */
    @Override
    public JsopBuilder endArray() {
        buff.append(']');
        needComma = true;
        return this;
    }

    /**
     * Append the key (in quotes) plus a colon. A comma is appended first if
     * needed.
     *
     * @param name the key
     * @return this
     */
    @Override
    public JsopBuilder key(String name) {
        optionalCommaAndNewline(name.length());
        if (JSON_NEWLINES) {
            buff.append('\n');
        }
        buff.append(encode(name)).append(':');
        needComma = false;
        return this;
    }

    /**
     * Append a number. A comma is appended first if needed.
     *
     * @param value the value
     * @return this
     */
    @Override
    public JsopBuilder value(long value) {
        return encodedValue(Long.toString(value));
    }

    /**
     * Append the boolean value 'true' or 'false'. A comma is appended first if
     * needed.
     *
     * @param value the value
     * @return this
     */
    @Override
    public JsopBuilder value(boolean value) {
        return encodedValue(Boolean.toString(value));
    }

    /**
     * Append a string or null. A comma is appended first if needed.
     *
     * @param value the value
     * @return this
     */
    @Override
    public JsopBuilder value(String value) {
        return encodedValue(encode(value));
    }

    /**
     * Append an already encoded value. A comma is appended first if needed.
     *
     * @param value the value
     * @return this
     */
    @Override
    public JsopBuilder encodedValue(String value) {
        optionalCommaAndNewline(value.length());
        buff.append(value);
        needComma = true;
        return this;
    }

    private void optionalCommaAndNewline(int add) {
        if (needComma) {
            buff.append(',');
        }
        if (lineLength > 0) {
            int len = buff.length();
            if (len > lineLength / 4 && len + add - previous > lineLength) {
                buff.append('\n');
                previous = len;
            }
        }
    }

    /**
     * Get the generated string.
     */
    @Override
    public String toString() {
        return buff.toString();
    }

    /**
     * Convert a string to a quoted Json literal using the correct escape
     * sequences. The literal is enclosed in double quotes. Characters outside
     * the range 32..127 are encoded (backslash u xxxx). The forward slash
     * (solidus) is not escaped. Null is encoded as "null" (without quotes).
     *
     * @param s the text to convert
     * @return the Json representation (including double quotes)
     */
    public static String encode(String s) {
        if (s == null) {
            return "null";
        }
        int length = s.length();
        if (length == 0) {
            return "\"\"";
        }
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (c == '\"' || c == '\\' || c < ' ' || c >= 127) {
                StringBuilder buff = new StringBuilder(length + 2 + length / 8);
                buff.append('\"');
                escape(s, length, buff);
                return buff.append('\"').toString();
            }
        }
        StringBuilder buff = new StringBuilder(length + 2);
        return buff.append('\"').append(s).append('\"').toString();
    }

    /**
     * Escape a string into the target buffer.
     *
     * @param s      the string to escape
     * @param length the number of characters.
     * @param buff   the target buffer
     */
    public static void escape(String s, int length, StringBuilder buff) {
        // TODO only backslashes, double quotes, and characters < 32 need to be
        // escaped - but currently all special characters are escaped, which
        // needs more time, memory, and storage space
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            switch (c) {
            case '"':
                // quotation mark
                buff.append("\\\"");
                break;
            case '\\':
                // backslash
                buff.append("\\\\");
                break;
            case '\b':
                // backspace
                buff.append("\\b");
                break;
            case '\f':
                // formfeed
                buff.append("\\f");
                break;
            case '\n':
                // newline
                buff.append("\\n");
                break;
            case '\r':
                // carriage return
                buff.append("\\r");
                break;
            case '\t':
                // horizontal tab
                buff.append("\\t");
                break;
            default:
                if (c < ' ') {
                    // guaranteed to be 1 or 2 hex digits only
                    buff.append("\\u00");
                    String hex = Integer.toHexString(c);
                    if (hex.length() == 1) {
                        buff.append('0');
                    }
                    buff.append(hex);
                } else if (c >= 127) {
                    // ascii only mode
                    buff.append("\\u");
                    String hex = Integer.toHexString(c);
                    for (int len = hex.length(); len < 4; len++) {
                        buff.append('0');
                    }
                    buff.append(hex);
                } else {
                    buff.append(c);
                }
            }
        }
    }

    /**
     * Get the buffer length.
     *
     * @return the length
     */
    public int length() {
        return buff.length();
    }

    /**
     * Beautify (format) the json / jsop string.
     *
     * @param jsop the jsop string
     * @return the formatted string
     */
    public static String prettyPrint(String jsop) {
        StringBuilder buff = new StringBuilder();
        JsopTokenizer t = new JsopTokenizer(jsop);
        while (true) {
            prettyPrint(buff, t, "  ");
            if (t.getTokenType() == JsopReader.END) {
                return buff.toString();
            }
        }
    }

    static String prettyPrint(StringBuilder buff, JsopTokenizer t, String ident) {
        String space = "";
        boolean inArray = false;
        while (true) {
            int token = t.read();
            switch (token) {
                case JsopReader.END:
                    return buff.toString();
                case JsopReader.STRING:
                    buff.append('\"').append(t.getEscapedToken()).append('\"');
                    break;
                case JsopReader.NUMBER:
                case JsopReader.TRUE:
                case JsopReader.FALSE:
                case JsopReader.NULL:
                case JsopReader.IDENTIFIER:
                case JsopReader.ERROR:
                    buff.append(t.getEscapedToken());
                    break;
                case '{':
                    if (t.matches('}')) {
                        buff.append("{}");
                    } else {
                        buff.append("{\n").append(space += ident);
                    }
                    break;
                case '}':
                    space = space.substring(0, space.length() - ident.length());
                    buff.append('\n').append(space).append("}");
                    break;
                case '[':
                    inArray = true;
                    buff.append("[");
                    break;
                case ']':
                    inArray = false;
                    buff.append("]");
                    break;
                case ',':
                    if (!inArray) {
                        buff.append(",\n").append(space);
                    } else {
                        buff.append(", ");
                    }
                    break;
                default:
                    buff.append((char) token).append(' ');
                    break;
            }
        }
    }

}
