package com.tivo.kmttg.main;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.string;

// Build tivo file name based on certain keyword/specs
public class tivoFileName {
   
   public static String buildTivoFileName(Hashtable<String,String> entry) {      
      if ( config.tivoFileNameFormat == null ) {
         config.tivoFileNameFormat = "[title]_[wday]_[month]_[mday]";
      }
      String file = config.tivoFileNameFormat + ".TiVo";
      
      // Breakdown gmt to local time components
      Hashtable<String,String> keys = new Hashtable<String,String>();
      SimpleDateFormat sdf = new SimpleDateFormat("mm HH dd MM E MMM yyyy");
      long gmt = Long.parseLong(entry.get("gmt"));
      String format = sdf.format(gmt);
      String[] t = format.split("\\s+");
      keys.put("min",      t[0]);
      keys.put("hour",     t[1]);
      keys.put("mday",     t[2]);
      keys.put("monthNum", t[3]);
      keys.put("wday",     t[4]);
      keys.put("month",    t[5]);
      keys.put("year",     t[6]);
      if ( ! entry.containsKey("EpisodeNumber") ) entry.put("EpisodeNumber", "");
      
      // Enter values for these names into keys hash
      String[] names = {
         "title", "titleOnly", "episodeTitle", "channelNum", "channel",
         "EpisodeNumber", "description"
      };
      for (int i=0; i<names.length; ++i) {
         if (entry.containsKey(names[i])) {
            keys.put(names[i], entry.get(names[i]));
         } else {
            keys.put(names[i], "");
         }
      }
      
      // Special keyword "[/]" means use sub-folders
      file = file.replaceAll("\\[/\\]", "__separator__");
      
      // Keyword handling
      // Syntax can be: [keyword] or ["text" keyword "text" ...]
      String n = "";
      char[] chars = file.toCharArray();
      for (int i=0; i<chars.length; ++i) {
         if (chars[i] == '[') {
            Hashtable<String,Object> h = parseKeyword(chars, i, keys);
            int delta = (Integer)h.get("delta");
            String text = (String)h.get("text");
            n += text;
            i += delta;
         } else {
            n += chars[i];
         }
      }
      file = n;
      
      // Remove/replace certain special characters
      //file = file.replaceAll("\\s+","_");
      file = file.replaceAll("/", "_");
      file = file.replaceAll("\\*", "");
      file = file.replaceAll("\"", "");
      file = file.replaceAll("'", "");
      file = file.replaceAll(":", "");
      file = file.replaceAll(";", "");
      //file = file.replaceAll("-", "_");
      file = file.replaceAll("!", "");
      file = file.replaceAll("\\?", "");
      file = file.replaceAll("&", "and");
      file = file.replaceAll("\\\\", "");
      file = file.replaceAll("\\$", "");
      
      // Deal with separators
      String s = File.separator;
      s = s.replaceFirst("\\\\", "\\\\\\\\");
      String[] l = file.split("__separator__");
      if (l.length > 0) {
         file = file.replaceAll("__separator__", s);
      }
      
      // Add sub-folder if requested
      if ( config.CreateSubFolder == 1 ) {
         String folder = string.basename(file).replaceFirst("\\.TiVo$", "");
         file = folder + File.separator + file;
      }
      
      debug.print("buildTivoFileName::file=" + file);
      return file;
   }
   
   // Keyword handling
   // Syntax can be: [keyword] or ["text" keyword "text" ...]
   // Quoted text => conditional literal text (only display if keyword evaluates to non-nil)
   // Unquoted text => keyword to evaluate
   private static Hashtable<String,Object> parseKeyword(char[] chars, int offset, Hashtable<String,String> keys) {
      char[] text_orig = chars;
      int length = text_orig.length;
      String keyword = "";
      int delta = 0;
      for (int j=offset; j<length; ++j) {
         keyword += text_orig[j];
         if (text_orig[j] == ']') break;
         delta++;
      }
      keyword = keyword.replaceFirst("\\[", "");
      keyword = keyword.replaceFirst("\\]", "");

      String[] fields = keyword.split("\\s+");
      Stack<String>newFields = new Stack<String>();
      Boolean exists = true;
      String text;
      for (int i=0; i<fields.length; i++) {
         text = fields[i];
         if (text.contains("\"")) {
            text = text.replaceAll("\"", "");
         } else {
            text = text.replaceFirst("^title$",         keys.get("title"));
            text = text.replaceFirst("^mainTitle$",     keys.get("titleOnly"));
            text = text.replaceFirst("^episodeTitle$",  keys.get("episodeTitle"));
            text = text.replaceFirst("^channelNum$",    keys.get("channelNum"));
            text = text.replaceFirst("^channel$",       keys.get("channel"));
            text = text.replaceFirst("^min$",           keys.get("min"));
            text = text.replaceFirst("^hour$",          keys.get("hour"));
            text = text.replaceFirst("^wday$",          keys.get("wday"));
            text = text.replaceFirst("^mday$",          keys.get("mday"));
            text = text.replaceFirst("^month$",         keys.get("month"));
            text = text.replaceFirst("^monthNum$",      keys.get("monthNum"));
            text = text.replaceFirst("^year$",          keys.get("year"));
            text = text.replaceFirst("^EpisodeNumber$", keys.get("EpisodeNumber"));
            text = text.replaceFirst("^description$",   keys.get("description"));
            if (text.length() == 0) exists = false;
         }
         newFields.add(text);
      }
      text = "";
      if (exists) {
         for (int i=0; i<newFields.size(); ++i) {
            text += newFields.get(i);
         }
      }
      debug.print("parseKeyword::text=" + text);
      Hashtable<String,Object> l = new Hashtable<String,Object>();
      l.put("delta", delta);
      l.put("text", text);
      return l;
   }

}
