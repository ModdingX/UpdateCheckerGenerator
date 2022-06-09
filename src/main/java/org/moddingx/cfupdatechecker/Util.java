package org.moddingx.cfupdatechecker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Util {

    public static final Gson GSON;
    public static final Gson INTERNAL;

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        builder.setPrettyPrinting();
        GSON = builder.create();
    }

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        INTERNAL = builder.create();
    }
}
