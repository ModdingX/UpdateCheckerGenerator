package org.moddingx.updatecheckergenerator;

import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public enum ModLoader {
    
    FORGE("forge"),
    NEOFORGE("neoforge");
    
    public static final ValueConverter<ModLoader> ARG = new ModLoaderValueConverter();
    
    public final String id;

    ModLoader(String id) {
        this.id = id;
    }
    
    private static class ModLoaderValueConverter implements ValueConverter<ModLoader> {
        
        private ModLoaderValueConverter() {
            //
        }

        @Override
        public String valuePattern() {
            return Arrays.stream(ModLoader.values()).map(loader -> loader.id).sorted().collect(Collectors.joining(" | ", "[", "]"));
        }

        @Override
        public Class<? extends ModLoader> valueType() {
            return ModLoader.class;
        }

        @Override
        public ModLoader convert(String value) {
            for (ModLoader loader : ModLoader.values()) {
                if (Objects.equals(loader.id, value)) {
                    return loader;
                }
            }
            throw new ValueConversionException("ModLoader '" + value + "' is unknown or does not support update checker files.");
        }
    }
}
