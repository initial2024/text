package com.dsh.webgateway;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PendingAttachmentStore {
    public static final int MAX_ITEM_BYTES = 15 * 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 24 * 1024 * 1024;
    public static final int MAX_ITEMS = 6;

    public static final class Item {
        public final String name;
        public final String mime;
        public final String base64;
        public final int bytes;

        Item(String name, String mime, String base64, int bytes) {
            this.name = name;
            this.mime = mime;
            this.base64 = base64;
            this.bytes = bytes;
        }

        public boolean isImage() {
            return mime != null && mime.startsWith("image/");
        }
    }

    private static final List<Item> ITEMS = new ArrayList<>();

    private PendingAttachmentStore() {}

    public static synchronized void addUri(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("附件为空");
        }

        if (ITEMS.size() >= MAX_ITEMS) {
            throw new IllegalStateException("最多同时添加 " + MAX_ITEMS + " 个附件");
        }

        ContentResolver resolver = context.getContentResolver();
        String name = queryName(resolver, uri);
        String mime = resolver.getType(uri);
        if (mime == null || mime.trim().isEmpty()) {
            mime = "application/octet-stream";
        }

        byte[] bytes;
        try (InputStream in = resolver.openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("无法读取附件");
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                out.write(buffer, 0, n);
                if (out.size() > MAX_ITEM_BYTES) {
                    throw new IllegalStateException(
                            "单个附件不能超过 15 MiB：" + name);
                }
            }
            bytes = out.toByteArray();
        }

        int total = bytes.length;
        for (Item item : ITEMS) total += item.bytes;
        if (total > MAX_TOTAL_BYTES) {
            throw new IllegalStateException("附件总大小不能超过 24 MiB");
        }

        ITEMS.add(new Item(
                sanitizeName(name),
                mime,
                Base64.encodeToString(bytes, Base64.NO_WRAP),
                bytes.length));
    }

    public static synchronized List<Item> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(ITEMS));
    }

    public static synchronized List<Item> consume() {
        List<Item> copy = new ArrayList<>(ITEMS);
        ITEMS.clear();
        return copy;
    }

    public static synchronized void clear() {
        ITEMS.clear();
    }

    public static synchronized int count() {
        return ITEMS.size();
    }

    public static synchronized String summary() {
        if (ITEMS.isEmpty()) return "未添加附件";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ITEMS.size(); i++) {
            if (i > 0) out.append(" · ");
            Item item = ITEMS.get(i);
            out.append(item.isImage() ? "图片 " : "文件 ")
                    .append(item.name);
        }
        return out.toString();
    }

    private static String queryName(ContentResolver resolver, Uri uri) {
        String name = "attachment-" + System.currentTimeMillis();
        Cursor cursor = null;
        try {
            cursor = resolver.query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) {
                        name = value.trim();
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return name;
    }

    private static String sanitizeName(String value) {
        if (value == null || value.trim().isEmpty()) return "attachment.bin";
        return value.trim()
                .replace('\n', '_')
                .replace('\r', '_')
                .replace('/', '_')
                .replace('\\', '_');
    }
}
