package main;

import java.io.File;
import java.io.IOException;
import analysis.Analyser;
import analysis.SchemaCorpus;

/**
 * 
 * @author Lukas Ellinger
 */
public class Main {
  /**
   * If jar has already been executed once there possibly is a directory "Store" and a file
   * "UriOfFiles.csv" next to the jar. These two are used to store all remote refs. If you are
   * analysing the schema dataset again these are used and no remote refs are downloaded from the
   * internet. If you want to analyse a different dataset you have to delete these two files.
   * 
   * @param args choose between -analyse | -corpus, then -true if remote refs are allowed, -false if
   *        not. Third is the path in quotation marks to directory. If -corpus is chosen path should
   *        be directory of schema_corpus and fourth parameter should be path to repos_fullPath.csv.
   *        || if -stats is chosen as first parameter. Next parameters have to be path to
   *        analysis.csv, path to unnormalizedDir and at last path to normalizedDir.
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 3) {
      File dir = new File(args[2]);

      if (args[0].equals("-analyse")) {
        Analyser.analyse(dir, Boolean.parseBoolean(args[1].substring(1)));
      } else {
        throw new IllegalArgumentException("Unexpected value: " + args[0]);
      }
    } else if (args.length == 4) {
      if (args[0].equals("-corpus")) {
        SchemaCorpus.analyse(args[2], args[3], Boolean.parseBoolean(args[1].substring(1)));
      } else if (args[0].equals("-stats")) {
        Analyser.executeDetailedStats(args[1], new File(args[2]), new File(args[3]));
      } else {
        throw new IllegalArgumentException("Unexpected value: " + args[0]);
      }
    } else {
      throw new IllegalArgumentException("Invalid argument count");
    }
  }
}
