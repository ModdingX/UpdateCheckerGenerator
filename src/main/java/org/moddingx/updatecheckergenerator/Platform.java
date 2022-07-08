package org.moddingx.updatecheckergenerator;

import joptsimple.util.EnumConverter;
import org.moddingx.updatecheckergenerator.platform.impl.CursePlatform;
import org.moddingx.updatecheckergenerator.platform.ModdingPlatform;
import org.moddingx.updatecheckergenerator.platform.impl.ModrinthPlatform;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum Platform {
    
    CURSE(CursePlatform::new),
    MODRINTH(ModrinthPlatform::new);
    
    public static final EnumConverter<Platform> ARG = new EnumConverter<>(Platform.class) {

        @Override
        public String valuePattern() {
            return Arrays.stream(values()).map(v -> v.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining("|"));
        }
    };
    
    private final Supplier<ModdingPlatform<?>> factory;

    Platform(Supplier<ModdingPlatform<?>> factory) {
        this.factory = factory;
    }
    
    public ModdingPlatform<?> create() {
        return this.factory.get();
    }
}
