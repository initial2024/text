package com.omnigate.integrated;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Transparent one-shot SAF picker. Keeps underlying GeckoView activity alive. */
public final class OverlayDocumentPickerActivity extends Activity {
    private static final int PICK = 940;
    private static final int LIMIT = 8 * 1024 * 1024;
    private boolean started;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        started = state != null && state.getBoolean("started", false);
        if (started) return;
        started = true;
        try {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(pick, PICK);
        } catch (RuntimeException e) {
            OverlayAttachmentDraft.report("无法打开系统文件选择器：" + e.getClass().getSimpleName());
            finish();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("started", started);
        super.onSaveInstanceState(out);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            OverlayAttachmentDraft.report("已取消选择；不会发送文件");
            finish();
            return;
        }
        Uri uri = data.getData();
        final String mime = getContentResolver().getType(uri);
        String name = "attachment";
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,null,null)) {
            if (c != null && c.moveToFirst()) {
                int n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (n >= 0 && !c.isNull(n)) name = c.getString(n);
                int sz = c.getColumnIndex(OpenableColumns.SIZE);
                if (sz >= 0 && !c.isNull(sz) && c.getLong(sz)>LIMIT) {
                    OverlayAttachmentDraft.report("文件超过 8 MiB，未读取或发送");
                    finish(); return;
                }
            }
        } catch (Exception ignored) {}
        final String fileName = name;
        OverlayAttachmentDraft.report("正在读取选中的文件…");
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("文件不可读");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int count;
                while ((count = input.read(buf)) >= 0) {
                    if (bytes.size() + count > LIMIT) throw new java.io.IOException("文件超过 8 MiB");
                    bytes.write(buf,0,count);
                }
                OverlayAttachmentDraft.select(bytes.toByteArray(),fileName,mime);
            } catch (Exception e) {
                OverlayAttachmentDraft.report("读取失败：" + e.getClass().getSimpleName());
            }
            runOnUiThread(this::finish);
        },"omnigate-file-read").start();
    }
}
