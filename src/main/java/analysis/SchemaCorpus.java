package analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import com.google.common.collect.Lists;
import exception.InvalidReferenceException;
import model.normalization.Normalizer;
import model.normalization.RepositoryType;
import model.recursion.RecursionChecker;
import model.recursion.RecursionType;
import util.CSVUtil;
import util.Log;
import util.SchemaUtil;
import util.URIUtil;

/**
 * Is used to analyse the Schema Corpus (https://github.com/sdbs-uni-p/json-schema-corpus)
 * 
 * @author Lukas Ellinger
 */
public class SchemaCorpus {
  /**
   * Normalizes all schemas of <code>schema_corpus</code>. The base is Draft04.
   * 
   * @param schema_corpus directory of <code>schema_corpus</code>.
   * @param fullPath file of <code>repos_fullPath.csv</code>.
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   * @throws IOException if <code>repos_fullPath</code> cannot be loaded.
   */
  public static void analyse(File schema_corpus, File fullPath, boolean allowDistributedSchemas)
      throws IOException {
    if (!schema_corpus.isDirectory() || !fullPath.exists()) {
      throw new IllegalArgumentException(schema_corpus.getName() + " needs to be a directory and "
          + fullPath.getName() + " needs to exist");
    }

    List<CSVRecord> records = CSVUtil.loadCSV(fullPath, ' ', false);
    File normalizedDir = new File("Normalized_" + schema_corpus.getName());
    normalizedDir.mkdir();

    ListIterator<CSVRecord> iterator = records.listIterator();

    int total = 0;
    int recursive = 0;
    int unguardedRecursive = 0;
    int illegalDraft = 0;
    int invalidReference = 0;

    File analysisFile = new File("analysis_" + schema_corpus.getName() + ".csv");
    createAnalysisCSV(analysisFile);
    while (iterator.hasNext()) {
      CSVRecord record = iterator.next();

      if (!record.get(1).equals("deleted")) {
        String file = record.get(0);
        file = file.replaceFirst("js", "pp");
        String[] fileRow = {file, "", "", "", ""};
        try {
          URI recordURI = URIUtil.urlToUri(new URL(record.get(1)));
          File unnormalized = new File(schema_corpus, file);

          if (unnormalized.exists()) {
            if (SchemaUtil.isValidToDraft(unnormalized)) {
              try {
                RecursionChecker checker = new RecursionChecker(SchemaUtil.normalize(unnormalized,
                    recordURI, normalizedDir, allowDistributedSchemas, RepositoryType.CORPUS));
                try {
                  RecursionType type = checker.checkForRecursion();
                  if (type == RecursionType.GUARDED || type == RecursionType.RECURSION) {
                    fileRow[1] = "TRUE";
                    recursive++;

                    if (type != RecursionType.GUARDED) {
                      fileRow[2] = "TRUE";
                      unguardedRecursive++;
                    }
                  }
                } catch (Exception e) {
                  Log.severe(
                      unnormalized.getName() + ": Error occured during recursion analysis - " + e);
                }
              } catch (IOException e) {
                Log.severe(unnormalized.getName(), e);
              } catch (InvalidReferenceException e) {
                fileRow[3] = "TRUE";
                invalidReference++;
              } catch (Exception e) {
                Log.severe(unnormalized.getName() + "RuntimeError", e);
              }
            } else {
              fileRow[4] = "TRUE";
              illegalDraft++;
            }
          }
        } catch (URISyntaxException e) {
          Log.warn(record.get(0), e);
        }
        total++;
        CSVUtil.writeToCSV(analysisFile, fileRow);
      }
    }

    Log.info("----------------------------------");
    Log.info("Total: " + total);
    Log.info("Recursive: " + recursive);
    Log.info("Thereof unguarded recursive: " + unguardedRecursive);
    Log.info("Illegal draft: " + illegalDraft);
    Log.info("Invalid reference: " + invalidReference);
  }

  /**
   * Can test specified schemas in <code>csvFile</code> for recursion.
   * 
   * @param schema_corpus directory of <code>schema_corpus</code>.
   * @param fullPath file of <code>repos_fullPath.csv</code>.
   * @param csvFile of to be tested schemas. In each row, there only needs to be the number of one
   *        schema. No header.
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   * @throws IOException
   * @throws URISyntaxException
   */
  public static void testSchemas(File schema_corpus, File fullPath, File csvFile,
      boolean allowDistributedSchemas) throws IOException, URISyntaxException {
    if (!schema_corpus.isDirectory() || !fullPath.exists() || !csvFile.exists()) {
      throw new IllegalArgumentException(schema_corpus.getName() + " needs to be a directory and "
          + fullPath.getName() + " " + csvFile.getName() + " needs to exist");
    }

    List<CSVRecord> records = CSVUtil.loadCSV(fullPath, ' ', false);
    List<CSVRecord> toBeTested = CSVUtil.loadCSV(csvFile, ' ', false);

    for (CSVRecord record : toBeTested) {
      int reposNumber = Integer.parseInt(record.get(0));
      URI reposURI = new URI(records.get(reposNumber).get(1));
      String file = "pp_" + reposNumber + ".json";
      File unnormalized = new File(schema_corpus, file);

      if (unnormalized.exists()) {
        if (SchemaUtil.isValidToDraft(unnormalized)) {
          try {
            Normalizer normalizer = new Normalizer(unnormalized, reposURI, allowDistributedSchemas,
                RepositoryType.CORPUS);
            RecursionChecker checker = new RecursionChecker(normalizer.normalize());
            Log.info(unnormalized.getName() + ": " + checker.checkForRecursion().name());
          } catch (InvalidReferenceException e) {
            Log.warn(unnormalized.getName(), e);
          }
        }
      }
    }
  }

  private static void createAnalysisCSV(File csv) throws IOException {
    assert csv.exists();

    String[] head =
        {"name", "recursiv", "unguarded_recursiv", "invalid_reference", "illegal_draft"};
    CSVUtil.writeToCSV(csv, head);
  }

  /**
   * Corrects all URI of repos_fullPath.csv. Therefore it inserts %20 for spaces.
   * 
   * @param fullPath file of <code>repos_fullPath.csv</code>.
   * @throws IOException
   */
  private static void correctURIsFullpath(File fullPath) throws IOException {
    assert fullPath.exists();

    List<CSVRecord> records = CSVUtil.loadCSV(fullPath, ' ', false);
    List<String> lines = new ArrayList<String>();
    int count = 0;

    for (CSVRecord csvRecord : records) {
      String uri = csvRecord.get(1);
      String correctedUri = uri.replace(" ", "%20");

      if (!correctedUri.equals(uri)) {
        count++;
      }

      lines.add(csvRecord.get(0) + " " + correctedUri);
    }

    FileUtils.writeLines(fullPath, lines);
    Log.info(count + " URIS corrected.");
  }

  /**
   * Replaces URI (second column) in <code>repos_fullPath</code> with "deleted", if file does not
   * exist in <code>schema_corpus</code>.
   * 
   * @param schema_corpus directory of <code>schema_corpus</code>.
   * @param fullPath file of <code>repos_fullPath.csv</code>.
   * @throws IOException
   */
  private static void markNotExistingFilesFullPath(File schema_corpus, File fullPath)
      throws IOException {
    assert schema_corpus.isDirectory() && fullPath.exists();

    List<CSVRecord> records = CSVUtil.loadCSV(fullPath, ' ', false);
    ListIterator<CSVRecord> iterator = records.listIterator();
    List<String> lines = new ArrayList<String>();
    int count = 0;

    while (iterator.hasNext()) {
      CSVRecord record = iterator.next();
      String file = record.get(0);
      file = file.replaceFirst("js", "pp");
      File schema = new File(schema_corpus, file);

      if (!schema.exists()) {
        lines.add(record.get(0) + " " + "deleted");
        count++;
      } else {
        lines.add(record.get(0) + " " + record.get(1));
      }
    }

    FileUtils.writeLines(fullPath, lines);
    Log.info(count + " not existing files in corpus marked.");
  }

  /**
   * Deletes the files of all no valid schemas in <code>schema_corpus</code> and marks them with
   * "deleted" in <code>repos_fullPath.csv</code>.
   * 
   * @param schema_corpus directory of <code>schema_corpus</code>.
   * @param fullPath file of <code>repos_fullPath.csv</code>.
   * @throws IOException
   */
  private static void deleteNoValidSchemas(File schema_corpus, File fullPath) throws IOException {
    assert schema_corpus.isDirectory() && fullPath.exists();

    List<CSVRecord> records = CSVUtil.loadCSV(fullPath, ' ', false);
    ListIterator<CSVRecord> iterator = records.listIterator();
    List<String> lines = new ArrayList<String>();
    int count = 0;

    while (iterator.hasNext()) {
      CSVRecord record = iterator.next();

      if (!record.get(1).equals("deleted")) {
        String file = record.get(0);
        file = file.replaceFirst("js", "pp");
        File schema = new File(schema_corpus, file);

        if (!SchemaUtil.isValidToDraft(schema)) {
          lines.add(record.get(0) + " " + "deleted");
          schema.delete();
          count++;
        } else {
          lines.add(record.get(0) + " " + record.get(1));
        }
      } else {
        lines.add(record.get(0) + " " + record.get(1));
      }
    }

    FileUtils.writeLines(fullPath, lines);
    Log.info(count + " not valid schemas in corpus deleted.");
  }

  /**
   * Removes all duplicate files. The content is checked for equivality.
   * 
   * @param dir to remove duplicates.
   * @throws IOException
   */
  private static void removeDuplicateSchemas(File dir) throws IOException {
    assert dir.isDirectory();

    List<File> dirList = Arrays.asList(dir.listFiles());
    ListIterator<File> iterator = dirList.listIterator();
    int count = 0;

    while (iterator.hasNext()) {
      File file1 = iterator.next();
      ListIterator<File> innerIterator = dirList.listIterator(iterator.nextIndex());
      if (file1.exists()) {
        while (innerIterator.hasNext()) {
          File file2 = innerIterator.next();
          if (FileUtils.contentEquals(file1, file2)) {
            file2.delete();
            count++;
          }
        }
      }
    }

    Log.info("Duplicates count: " + count);
  }

  /**
   * Removes all files which are included in other files.
   * 
   * @param dir to remove all included schemas.
   * @throws IOException
   */
  private static void removeIncludedSchemas(File dir) throws IOException {
    assert dir.isDirectory();

    List<File> dirList = Arrays.asList(dir.listFiles());
    int count = 0;

    for (File file1 : dirList) {
      if (file1.exists()) {
        long file1Length = file1.length();
        String file1Lines = FileUtils.readFileToString(file1, "UTF-8");
        file1Lines = file1Lines.replace("\n", "");
        file1Lines = file1Lines.replace(" ", "");
        List<Character> file1List = Lists.charactersOf(file1Lines);


        for (File file2 : dir.listFiles()) {
          if (file2.length() < file1Length) {
            String file2Lines = FileUtils.readFileToString(file2, "UTF-8");
            file2Lines = file2Lines.replace("\n", "");
            file2Lines = file2Lines.replace(" ", "");
            List<Character> file2List = Lists.charactersOf(file2Lines);

            if (Collections.indexOfSubList(file1List, file2List) != -1) {
              file2.delete();
              count++;
            }
          }
        }

      }
    }

    Log.info("Included count: " + count);
  }

  /**
   * Removes all lines of <code>fullPath</code> which have "deleted" in second column.
   * 
   * @param fullPath file of <code>repos_fullPath.csv</code>.
   * @throws IOException
   */
  private static void removeDeletedLinesFromCSV(File fullPath) throws IOException {
    assert fullPath.exists();

    List<CSVRecord> records = CSVUtil.loadCSV(fullPath, ' ', false);
    List<CSVRecord> newRecords = new ArrayList<>();

    for (CSVRecord record : records) {
      if (!record.get(1).equals("deleted")) {
        newRecords.add(record);
      }
    }

    try (
        BufferedWriter writer = Files.newBufferedWriter(fullPath.toPath(),
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);) {
      csvPrinter.printRecords(newRecords);
      csvPrinter.flush();
    }
  }

  /**
   * Replaces URI in <code>repos_fullPath</code> with "deleted" if file does not exist in
   * <code>schema_corpus</code>.
   * 
   * @param schema_corpus directory of <code>schema_corpus</code>.
   * @param fullPath file of <code>repos_fullPath.csv</code>.
   * @throws IOException
   */
  private static void markNotExistingFilesFullPathNormalized(File schema_corpus, File fullPath)
      throws IOException {
    assert schema_corpus.isDirectory() && fullPath.exists();

    List<CSVRecord> records = CSVUtil.loadCSV(fullPath, ' ', false);
    ListIterator<CSVRecord> iterator = records.listIterator();
    List<String> lines = new ArrayList<String>();
    int count = 0;

    while (iterator.hasNext()) {
      CSVRecord record = iterator.next();
      String file = record.get(0);
      file = file.replaceFirst("js", "pp");
      File schema = new File(schema_corpus, SchemaUtil.getNormalizedFileName(file));

      if (!schema.exists()) {
        lines.add(record.get(0) + " " + "deleted");
        count++;
      } else {
        lines.add(record.get(0) + " " + record.get(1));
      }
    }

    FileUtils.writeLines(fullPath, lines);
    Log.info(count + " not existing files in corpus marked.");
  }

  public static void main(String[] args) throws IOException {
    removeDuplicateSchemas(new File("Normalized_SchemaCorpus"));
    removeIncludedSchemas(new File("Normalized_SchemaCorpus"));
    markNotExistingFilesFullPathNormalized(new File("Normalized_SchemaCorpus"),
        new File("repos_fullpath"));
    removeDeletedLinesFromCSV(new File("repos_fullpath"));
  }
}
