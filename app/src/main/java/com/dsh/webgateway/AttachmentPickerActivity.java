package com.dsh.webgateway;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public final class AttachmentPickerActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_IMAGE = "image";
    public static final String MODE_FILE = "file";
    private static final int REQUEST_PICK = 5101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) launchPicker();
    }

    private void launchPicker() {
        String mode = getIntent() == null
                ? MODE_FILE
                : getIntent().getStringExtra(EXTRA_MODE);

        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType(MODE_IMAGE.equals(mode) ? "image/*" : "*/*");
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(pick, REQUEST_PICK);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null) {
            finish();
            return;
        }

        int added = 0;
        String error = null;

        try {
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri == null) continue;
                    tryPersist(uri, data.getFlags());
                    PendingAttachmentStore.addUri(this, uri);
                    added++;
                }
            } else {
                Uri uri = data.getData();
                if (uri != null) {
                    tryPersist(uri, data.getFlags());
                    PendingAttachmentStore.addUri(this, uri);
                    added++;
                }
            }
        } catch (Throwable e) {
            error = e.getMessage() == null
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
        }

        if (error != null) {
            Toast.makeText(
                    this,
                    "附件添加失败：" + error,
                    Toast.LENGTH_LONG).show();
        } else if (added > 0) {
            Toast.makeText(
                    this,
                    "已添加 " + added + " 个附件",
                    Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    private void tryPersist(Uri uri, int flags) {
        try {
            int takeFlags = flags
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(
                    uri,
                    takeFlags);
        } catch (Throwable ignored) {}
    }
}
