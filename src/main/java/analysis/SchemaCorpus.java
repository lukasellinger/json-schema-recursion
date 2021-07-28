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
import java.util.List;
import java.util.ListIterator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import exception.InvalidReferenceException;
import model.normalization.Normalizer;
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
   * @param schema_corpus path of <code>schema_corpus</code>.
   * @param path path of <code>repos_fullPath</code>.
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   * @throws IOException if <code>repos_fullPath</code> cannot be loaded.
   */
  public static void analyse(String schema_corpus, String path, boolean allowDistributedSchemas)
      throws IOException {
    File dir = new File(schema_corpus);
    List<CSVRecord> records = CSVUtil.loadCSV(path, ' ', false);
    File normalizedDir = new File("Normalized_" + dir.getName());
    normalizedDir.mkdir();

    ListIterator<CSVRecord> iterator = records.listIterator();

    int total = 0;
    int recursive = 0;
    int unguardedRecursive = 0;
    int illegalDraft = 0;
    int invalidReference = 0;
    
    File analysisFile = new File("analysis_" + dir.getName() + ".csv");
    createAnalysisCSV(analysisFile);
    while (iterator.hasNext()) {
      CSVRecord record = iterator.next();

      if (!record.get(1).equals("deleted")) {
        String file = record.get(0);
        file = file.replaceFirst("js", "pp");
        String[] fileRow = {file, "", "", "", ""};
        try {
          URI recordURI = URIUtil.urlToUri(new URL(record.get(1)));
          File unnormalized = new File(dir, file);

          if (unnormalized.exists()) {
            if (SchemaUtil.isValidToDraft(unnormalized)) {
              try {
                RecursionChecker checker = new RecursionChecker(SchemaUtil.normalize(unnormalized,
                    recordURI, normalizedDir, allowDistributedSchemas));
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
   * @param schema_corpus path of <code>schema_corpus</code>.
   * @param path path of <code>repos_fullPath</code>.
   * @param csvFile of to be tested schemas. In each row, there only needs to be the number of one
   *        schema. No header.
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   * @throws IOException
   * @throws URISyntaxException
   */
  public static void testSchemas(String schema_corpus, String path, String csvFile,
      boolean allowDistributedSchemas) throws IOException, URISyntaxException {
    List<CSVRecord> records = CSVUtil.loadCSV(path, ' ', false);
    List<CSVRecord> toBeTested = CSVUtil.loadCSV(csvFile, ' ', false);

    for (CSVRecord record : toBeTested) {
      int reposNumber = Integer.parseInt(record.get(0));
      URI reposURI = new URI(records.get(reposNumber).get(1));
      String file = "pp_" + reposNumber + ".json";
      File unnormalized = new File(schema_corpus, file);

      if (unnormalized.exists()) {
        if (SchemaUtil.isValidToDraft(unnormalized)) {
          try {
            Normalizer normalizer = new Normalizer(unnormalized, reposURI, allowDistributedSchemas);
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
    String[] head =
        {"name", "recursiv", "unguarded_recursiv", "invalid_reference", "illegal_draft"};
    CSVUtil.writeToCSV(csv, head);
  }

  /**
   * Corrects all URI of repos_fullPath.csv. Therefore it inserts %20 for spaces
   * 
   * @param path path of <code>repos_fullPath</code>.
   * @throws IOException
   */
  private static void correctURIsFullpath(String path) throws IOException {
    List<CSVRecord> records = CSVUtil.loadCSV(path, ' ', false);
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

    FileUtils.writeLines(new File(path), lines);
    Log.info(count + " URIS corrected.");
  }

  /**
   * Replaces URI in <code>repos_fullPath</code> with "deleted" if file does not exist in
   * <code>schema_corpus</code>.
   * 
   * @param schema_corpus path of <code>schema_corpus</code>.
   * @param path path of <code>repos_fullPath</code>.
   * @throws IOException
   */
  private static void markNotExistingFilesFullPath(String schema_corpus, String path)
      throws IOException {
    List<CSVRecord> records = CSVUtil.loadCSV(path, ' ', false);
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

    FileUtils.writeLines(new File(path), lines);
    Log.info(count + " not existing files in corpus marked.");
  }

  /**
   * Deletes all files <code>schema_corpus</code>and marks them with "deleted" in
   * <code>repos_fullPath</code> if they are not valid schemas.
   * 
   * @param schema_corpus path of <code>schema_corpus</code>.
   * @param path path of <code>repos_fullPath</code>.
   * @throws IOException
   */
  private static void deleteNoValidSchemas(String schema_corpus, String path) throws IOException {
    List<CSVRecord> records = CSVUtil.loadCSV(path, ' ', false);
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

    FileUtils.writeLines(new File(path), lines);
    Log.info(count + " not valid schemas in corpus deleted.");
  }

  /**
   * Creates a new csv-file without duplicate files from <code>csv</code> and deletes them.
   * 
   * @param csv of files with their <code>URI</code>.
   * @param store where files are stored.
   * @throws IOException
   */
  private static void cleanUriOfFiles(String csv, String store) throws IOException {
    List<CSVRecord> records = CSVUtil.loadCSV(csv, ',', false);
    List<CSVRecord> newRecords = new ArrayList<>();
    File dir = new File(store);

    ListIterator<CSVRecord> iterator = records.listIterator(1);

    while (iterator.hasNext()) {
      CSVRecord record = iterator.next();

      if (new File(dir, record.get(0)).exists()) {
        newRecords.add(record);
        ListIterator<CSVRecord> innerIterator = records.listIterator(iterator.nextIndex());
        String url = record.get(1);

        innerIterator.forEachRemaining((other) -> {
          File file = new File(dir, other.get(0));

          if (url.equals(other.get(1))) {
            file.delete();
          }

        });
      }
    }

    File csvNew = new File("UriOfFilesNew.csv");

    try (
        BufferedWriter writer = Files.newBufferedWriter(csvNew.toPath(), StandardOpenOption.APPEND,
            StandardOpenOption.CREATE);
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);) {
      csvPrinter.printRecord("name", "uri");
      csvPrinter.printRecords(newRecords);
      csvPrinter.flush();
    }
  }
}
