package com.dsh.webgateway;

import android.app.Application;

public final class GatewayApp extends Application {
    private static volatile WebRelay relay;

    public static WebRelay relay() {
        return relay;
    }

    public static void setRelay(WebRelay value) {
        relay = value;
    }
}
