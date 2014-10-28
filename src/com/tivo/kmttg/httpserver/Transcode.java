package com.tivo.kmttg.httpserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class Transcode {
   SocketProcessInputStream ss = null;
   String returnFile = null;
   backgroundProcess process = null;
   Process p1 = null;
   Process p2 = null;
   String inputFile = null;
   String base = config.httpserver_cache;
   String prefix = "";
   int count = 0;
   String format = "";
   
   public Transcode(String inputFile) {
      this.inputFile = inputFile;
      setCachePrefix(); // sets prefix variable
   }
   public static class RunnableInputDrainer implements Runnable {
      InputStream is;
      public RunnableInputDrainer(InputStream is) {
         this.is = is;
      }
      public void run() {
         try {
            byte[] b = new byte[2048];
            while(is.read(b) != -1) {}
         } catch (IOException e) {log.error("Drainer - " + e.getMessage());}         
      }
   }
   
   public SocketProcessInputStream webm() {
      boolean isTivoFile = false;
      if (inputFile.toLowerCase().endsWith(".tivo"))
         isTivoFile = true;
      try {
         ss = new SocketProcessInputStream();
      } catch (Exception e) {
         log.error("webm - " + e.getMessage());
         return null;
      }
      String sockStr = "tcp://127.0.0.1:" + ss.getPort();
      String args = TranscodeTemplates.webm() + " " + sockStr;
      String[] ffArgs = args.split(" ");
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      if (isTivoFile)
         command.add("-");
      else
         command.add(inputFile);
      for (String c : ffArgs)
         command.add(c);

      if (isTivoFile) {
         // Need 2 piped processes
         log.print(">> Transcoding TiVo file to webm " + inputFile + " ...");
         java.lang.Runtime rt = java.lang.Runtime.getRuntime();
         String[] tivodecode = {
            config.tivodecode,
            "--mak",
            config.MAK,
            "--no-verify",
            inputFile
         };
         String[] ffmpeg = new String[command.size()];
         int i=0;
         for (String s : command)
            ffmpeg[i++] = s;
         try {
            p1 = rt.exec(tivodecode);
            p2 = rt.exec(ffmpeg);
            log.print(
               TranscodeTemplates.printArray(tivodecode) + " | " +
               TranscodeTemplates.printArray(ffmpeg)
            );
            RunnableInputDrainer des = new RunnableInputDrainer(p2.getErrorStream());
            new Thread(des).start();
         } catch (IOException e) {
            log.error("webm - " + e.getMessage());
            return null;
         }
         Piper pipe = new Piper(
            new BufferedInputStream(p1.getInputStream()),
            new BufferedOutputStream(p2.getOutputStream())
         );
         new Thread(pipe).start();
         ss.attachProcess(p1);
      } else {
         process = new backgroundProcess();
         log.print(">> Transcoding to webm " + inputFile + " ...");
         if ( process.run(command) ) {
            log.print(process.toString());
            ss.attachProcess(process.getProcess());
         } else {
            log.error("Failed to start command: " + process.toString());
            process.printStderr();
            process = null;
            return null;
         }
      }
      return ss;
   }
   
   public String hls() {
      boolean isTivoFile = false;
      if (inputFile.toLowerCase().endsWith(".tivo"))
         isTivoFile = true;
      format = "hls";
      String urlBase = config.httpserver_cache_relative;
      String args = TranscodeTemplates.hls(urlBase);
      if (! file.isDir(base))
         new File(base).mkdirs();
      String segmentFile = base + File.separator + prefix + ".m3u8";
      String segments = base + File.separator + prefix + "-%05d.ts";
      String[] ffArgs = args.split(" ");
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      if (isTivoFile)
         command.add("-");
      else
         command.add(inputFile);
      for (String c : ffArgs)
         command.add(c);
      command.add(segmentFile);
      command.add(segments);

      if (isTivoFile) {
         // Need 2 piped processes
         log.print(">> Transcoding TiVo file to HLS " + inputFile + " ...");
         java.lang.Runtime rt = java.lang.Runtime.getRuntime();
         String[] tivodecode = {
            config.tivodecode,
            "--mak",
            config.MAK,
            "--no-verify",
            inputFile
         };
         String[] ffmpeg = new String[command.size()];
         int i=0;
         for (String s : command)
            ffmpeg[i++] = s;
         try {
            p1 = rt.exec(tivodecode);
            p2 = rt.exec(ffmpeg);
            log.print(
               TranscodeTemplates.printArray(tivodecode) + " | " +
               TranscodeTemplates.printArray(ffmpeg)
            );
            RunnableInputDrainer des = new RunnableInputDrainer(p2.getErrorStream());
            new Thread(des).start();
         } catch (IOException e) {
            log.error("hls - " + e.getMessage());
            return null;
         }
         Piper pipe = new Piper(
            new BufferedInputStream(p1.getInputStream()),
            new BufferedOutputStream(p2.getOutputStream())
         );
         new Thread(pipe).start();
      } else {
         process = new backgroundProcess();
         log.print(">> Transcoding to hls " + inputFile + " ...");
         if ( process.run(command) ) {
            log.print(process.toString());
         } else {
            log.error("Failed to start command: " + process.toString());
            process.printStderr();
            process = null;
            return null;
         }
      }
      
      returnFile = urlBase + prefix + ".m3u8";
      try {
         // Wait for segmentFile to get created
         int counter = 0;
         while( file.size(segmentFile) == 0 && counter < 10 ) {
            Thread.sleep(1000);
            counter++;
         }
      } catch (InterruptedException e) {
         log.error("Transcode sleep - " + e.getMessage());
      }
      return returnFile;
   }
   
   public int exitStatus(Process proc) {
      try {
         int v = proc.exitValue();
         return v;
      }
      catch (IllegalThreadStateException i) {
         return -1;
      }
   }
   
   // Determine unused video cache file prefix to use
   public void setCachePrefix() {
      String prefix_string = "t";
      Boolean go = true;
      int index = config.httpserver.transcode_counter;
      File[] files = new File(base).listFiles();
      String filePrefix;
      while (go) {
         filePrefix = prefix_string + index;
         boolean useThis = true;
         for (File f : files) {
            String basename = string.basename(f.getAbsolutePath());
            if (basename.startsWith(filePrefix)) {
               useThis = false;
            }
         }
         if (useThis) {
            prefix = prefix_string + index;
            return;
         }
         index++;
         if (index > 100) // prevent large number of cached files + inf loop
            go = false;
      }
      prefix = prefix_string + index;
   }
   
   public boolean isRunning() {
      // hls is special case since once files are created don't re-create
      if (count > 0 && format.equals("hls"))
         return true;
      count++;
      boolean running = false;
      if (process != null && process.exitStatus() == -1)
         running = true;
      if (p1 != null && exitStatus(p1) == -1)
         running = true;
      if (p2 != null && exitStatus(p2) == -1)
         running = true;
      return running;
   }
   
   public void kill() {
      if (process != null) {
         log.warn("Killing transcode: " + process.toString());
         process.kill();
      }
      if (p1 != null)
         p1.destroy();
      if (p2 != null)
         p2.destroy();
   }
   
   public void cleanup() {
      if (prefix.length() > 0 && base.length() > 0) {
         log.warn("Removing '" + prefix + "' transcode files in: " + base);
         File[] files = new File(base).listFiles();
         for (File f : files) {
            String basename = string.basename(f.getAbsolutePath());
            if (basename.startsWith(prefix)) {
               f.delete();
            }
         }
      }
   }
}