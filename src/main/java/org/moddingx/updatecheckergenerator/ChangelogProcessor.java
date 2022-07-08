package org.moddingx.updatecheckergenerator;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;

public class ChangelogProcessor {

    public static String process(String html) {
        return normalizeText(Jsoup.parse(html).wholeText());
    }

    private static String normalizeText(String str) {
        str = str.startsWith("\"") && str.endsWith("\"") ? str.substring(1, str.length() - 1) : str;
        str = StringEscapeUtils.unescapeJava(str).replace("\r", "")
                .replaceAll("\\n+", "\n")
                .strip();
        
        StringBuilder builder = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isWhitespace(c) && c != '\n') c = ' ';
            builder.append(c);
        }
        
        return builder.toString().replaceAll(" +", " ")
                .replace("\n \n", "\n")
                .replaceAll("\\n+", "\n")
                .strip();
    }
}
