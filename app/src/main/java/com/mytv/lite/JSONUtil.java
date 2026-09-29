package com.mytv.lite;

import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 容错 JSON 解析：TVBox 配置常见前导混淆/注释。 */
public final class JSONUtil {
    public static JSONObject parse(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        // 去掉 JSON 前的非 { [ 前缀（hex 头、注释等）
        int brace = raw.indexOf('{');
        int bracket = raw.indexOf('[');
        int start = brace < 0 ? bracket : (bracket < 0 ? brace : Math.min(brace, bracket));
        if (start < 0) return null;
        raw = raw.substring(start);
        try { return new JSONObject(raw); } catch (Exception ignored) {}
        try { return new JSONObject(raw.replaceAll("//[^\n]*", "")); } catch (Exception ignored) {}
        return null;
    }
}
