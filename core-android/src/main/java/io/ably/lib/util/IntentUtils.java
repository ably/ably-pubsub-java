package io.ably.lib.util;

import android.content.Intent;
import io.ably.lib.types.ErrorInfo;

public class IntentUtils {
    public static void addErrorInfo(Intent intent, ErrorInfo error) {
        intent.putExtra("hasError", error != null);
        if (error != null) {
            intent.putExtra("error.message", error.message);
            intent.putExtra("error.statusCode", error.statusCode);
            intent.putExtra("error.code", error.code);
        }
    }

    public static ErrorInfo getErrorInfo(Intent intent) {
        if (!intent.getBooleanExtra("hasError", false)) {
            return null;
        }
        return new ErrorInfo(
            intent.getStringExtra("error.message"),
            intent.getIntExtra("error.statusCode", 0),
            intent.getIntExtra("error.code", 0)
        );
    }
}
