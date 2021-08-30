package main;

import java.io.File;
import java.io.IOException;
import analysis.Analyser;
import analysis.DirNormalizer;
import analysis.SchemaCorpus;
import analysis.TestSuite;
import model.normalization.RepositoryType;

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
   * @param args As first parameter choose between -normalize | -recursion | -stats. If -normalize
   *        is chosen, second parameter will be the repositorytype. Choose between -corpus |
   *        -testsuite | -normal. Third parameter is the path to the directory to analyse in
   *        quotation marks. If -corpus is chosen, an additional fourth parameter with the path to
   *        repos_fullpath.csv in quotation marks will be needed. If -recursion is chosen, second
   *        parameter will be the path to the directory in which the normalized schemas are. These
   *        will be checked for recursion. If -stats is chosen, second parameter will be the path to
   *        the directory with unnormalized schemas and third parameter the path to the directory
   *        with normalized schemas.
   * 
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    switch (args[0]) {
      case "-normalize":
        boolean allowDistributedSchemas = Boolean.parseBoolean(args[2].substring(1));

        switch (args[1]) {
          case "-corpus":
            SchemaCorpus corpus = new SchemaCorpus();
            corpus.normalize(new File(args[3]), new File(args[4]), allowDistributedSchemas);
            break;
          case "-testsuite":
            TestSuite suite = new TestSuite();
            suite.normalize(new File(args[3]), allowDistributedSchemas, RepositoryType.TESTSUITE);
            break;
          case "-normal":
            DirNormalizer normalizer = new DirNormalizer();
            normalizer.normalize(new File(args[3]), allowDistributedSchemas, RepositoryType.NORMAL);
            break;
          default:
            throw new IllegalArgumentException("Unexpected value: " + args[1]);
        }
        break;
      case "-recursion":
        Analyser analyser1 = new Analyser();
        analyser1.analyseRecursion(new File(args[1]));
        break;
      case "-stats":
        Analyser analyser2 = new Analyser();
        analyser2.createDetailedStats(new File(args[1]), new File(args[2]));
        break;
      default:
        throw new IllegalArgumentException("Unexpected value: " + args[0]);
    }
  }
}
