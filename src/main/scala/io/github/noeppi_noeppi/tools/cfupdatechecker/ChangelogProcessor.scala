package io.github.noeppi_noeppi.tools.cfupdatechecker

import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup

object ChangelogProcessor {

  def process(html: String): String = normalizeText(Jsoup.parse(html).wholeText())
  
  private def normalizeText(str: String): String = {
    val theStr = if (str.startsWith("\"") && str.endsWith("\"")) str.substring(1, str.length - 1) else str
    StringEscapeUtils.unescapeJava(theStr).replace("\r", "").replaceAll("\\n+", "\n").strip()
      .map(chr => if (chr.isWhitespace && chr != '\n') ' ' else chr).replaceAll(" +", " ")
      .replace("\n \n", "\n").replaceAll("\\n+", "\n").strip()
  }
}
