package analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.security.InvalidParameterException;
import java.util.List;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import exception.DistributedSchemaException;
import exception.InvalidReferenceException;
import model.normalization.Normalizer;
import model.recursion.RecursionChecker;
import model.recursion.RecursionType;
import util.CSVUtil;
import util.Log;
import util.SchemaUtil;

/**
 * Used for analyzing directories with schemas in it.
 * 
 * @author Lukas Ellinger
 */
public class Analyser {

  /**
   * Analyse all files in <code>dir</code>. All normalized schemas get stored in "Normalized_name"
   * with name being the name of <code>dir</code>. A csv file with name "analysis_name" is created.
   * In it it can be checked for each file what was determined.
   * 
   * @param dir has to be a directory. all files in this get normalized.
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   * @throws IOException
   */
  public static void analyse(File dir, boolean allowDistributedSchemas) throws IOException {
    File normalizedDir = new File("Normalized_" + dir.getName());
    normalizedDir.mkdir();

    File[] files = dir.listFiles();
    int recursive = 0;
    int unguardedRecursive = 0;
    int illegalDraft = 0;
    int invalidReference = 0;

    File analysisFile = new File("analysis_" + dir.getName() + ".csv");
    createAnalysisCSV(analysisFile);
    for (File file : files) {
      String[] fileRow = {file.getName(), "", "", "", ""};
      if (SchemaUtil.isValidToDraft(file)) {
        try {
          RecursionChecker checker = new RecursionChecker(
              SchemaUtil.normalize(file, null, normalizedDir, allowDistributedSchemas));
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
            Log.severe(file.getName() + ": Error occured during recursion analysis - " + e);
          }
        } catch (InvalidReferenceException e) {
          fileRow[3] = "TRUE";
          Log.warn(file, e);
          invalidReference++;
        }
      } else {
        fileRow[4] = "TRUE";
        illegalDraft++;
      }
      CSVUtil.writeToCSV(analysisFile, fileRow);
    }

    Log.info("----------------------------------");
    Log.info("Total: " + files.length);
    Log.info("Recursive: " + recursive);
    Log.info("Thereof unguarded recursive: " + unguardedRecursive);
    Log.info("Illegal draft: " + illegalDraft);
    Log.info("Invalid reference: " + invalidReference);
  }

  /**
   * Prints stats to log-file and creates csv-file with schema types (single-file schemas,
   * distributed schemas). Uses cleaned schemas (all schemas that could be normalized.
   * 
   * @param csvRecursionPath path to csv file of recursion analysis.
   * @param unnormalizedDir path to directory of unnormalized schemas.
   * @param normalizedDir path to directory of normalized schemas.
   * @throws IOException
   */
  public static void executeDetailedStats(String csvRecursionPath, File unnormalizedDir,
      File normalizedDir) throws IOException {

    separateSchemasByType(unnormalizedDir, normalizedDir);
    detailedStats("schemaTypes.csv", csvRecursionPath, unnormalizedDir, normalizedDir);
  }

  private static void createAnalysisCSV(File csv) throws IOException {
    String[] head =
        {"name", "recursiv", "unguarded_recursiv", "invalid_reference", "illegal_draft"};
    CSVUtil.writeToCSV(csv, head);
  }

  private static int countRows(String s) throws IOException {
    BufferedReader in = new BufferedReader(new StringReader(s));
    int count = 0;

    while (in.readLine() != null) {
      count++;
    }

    return count;
  }

  /**
   * Gets rowcount of <code>file</code>. Uses pretty printing of <code>Gson</code>.
   * 
   * @param file needs to be valid JSON.
   * @return line count of <code>file</code>.
   * @throws IOException
   */
  public static int countRowsJSON(File file) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    JsonElement element =
        gson.fromJson(FileUtils.readFileToString(file, "UTF-8"), JsonElement.class);
    return countRows(gson.toJson(element));
  }

  /**
   * Separates all schemas that could be normalized by their type (single-file schema, distributed
   * schema). CSV-File "schemaTypes.csv" is created.
   * 
   * @param unnormalizedDir directory of which schemas should be separated by their type.
   * @param normalizedDir directory of normalized schemas of schemas in
   *        <code>unnormalizedDir</code>.
   * @throws IOException
   */
  public static void separateSchemasByType(File unnormalizedDir, File normalizedDir)
      throws IOException {
    if (unnormalizedDir.isDirectory() && normalizedDir.isDirectory()) {
      File csv = new File("schemaTypes.csv");
      String[] head = {"name", "distributed"};
      CSVUtil.writeToCSV(csv, head);

      for (File file : normalizedDir.listFiles()) {
        File unnormalized = new File(unnormalizedDir, file.getName().replace("_Normalized", ""));
        Normalizer normalizer = new Normalizer(unnormalized, false);
        try {
          normalizer.normalize();
          String[] row = {unnormalized.getName(), ""};
          CSVUtil.writeToCSV(csv, row);
        } catch (DistributedSchemaException e) {
          String[] row = {unnormalized.getName(), "TRUE"};
          CSVUtil.writeToCSV(csv, row);
        }
      }
    } else {
      throw new InvalidParameterException(unnormalizedDir.getName() + " and "
          + normalizedDir.getName() + " need to be a directory");
    }
  }

  /**
   * Prints stats to log-file and creates csv-file with schema types (single-file schemas,
   * distributed schemas). Uses cleaned schemas (all schemas that could be normalized.
   * 
   * @param csvTypePath path to csv file of type analysis.
   * @param csvRecursionPath path to csv file of recursion analysis.
   * @param unnormalizedDir path to directory of unnormalized schemas.
   * @param normalizedDir path to directory of normalized schemas.
   * @throws IOException
   */
  public static void detailedStats(String csvTypePath, String csvRecursionPath,
      File unnormalizedDir, File normalizedDir) throws IOException {
    List<CSVRecord> recordsType = CSVUtil.loadCSV(csvTypePath, ',', true);
    List<CSVRecord> recordsRecursion = CSVUtil.loadCSV(csvRecursionPath, ',', true);
    int singleFilesCount = 0;
    int distributedFilesCount = 0;
    int totalLocSingleFile = 0;
    int totalLoCSingleFileNormalized = 0;
    int totalLocDistributedFile = 0;
    int totalLoCDistributedFileNormalized = 0;
    int recursiveCountSingleFiles = 0;
    int recursiveCountDistributedFiles = 0;


    for (CSVRecord recordType : recordsType) {
      String fileName = recordType.get(0);
      String normalizedFileName = SchemaUtil.getNormalizedFileName(fileName);
      boolean isRecursive = false;

      for (CSVRecord recordRecursion : recordsRecursion) {
        if (recordRecursion.get(0).equals(fileName) && recordRecursion.get(1).equals("TRUE")) {
          isRecursive = true;
          break;
        }
      }

      if (recordType.get(1).equals("TRUE")) {
        if (isRecursive) {
          recursiveCountDistributedFiles++;
        }
        totalLocDistributedFile += countRowsJSON(new File(unnormalizedDir, fileName));
        totalLoCDistributedFileNormalized +=
            countRowsJSON(new File(normalizedDir, normalizedFileName));
        distributedFilesCount++;
      } else {
        if (isRecursive) {
          recursiveCountSingleFiles++;
        }
        totalLocSingleFile += countRowsJSON(new File(unnormalizedDir, fileName));
        totalLoCSingleFileNormalized += countRowsJSON(new File(normalizedDir, normalizedFileName));
        singleFilesCount++;
      }
    }

    int avgLocSingleFile = totalLocSingleFile / singleFilesCount;
    int avgLocSingleFileNormalized = totalLoCSingleFileNormalized / singleFilesCount;

    int avgLoCDistributedFile = totalLocDistributedFile / distributedFilesCount;
    int avgLocDistributedFileNormalized = totalLoCDistributedFileNormalized / distributedFilesCount;

    int avgLoCOverall =
        (totalLocDistributedFile + totalLocSingleFile) / (singleFilesCount + distributedFilesCount);
    int avgLoCOverallNormalized = (totalLoCDistributedFileNormalized + totalLoCSingleFileNormalized)
        / (singleFilesCount + distributedFilesCount);

    double blowUpSingleFile = blowUp(avgLocSingleFile, avgLocSingleFileNormalized);
    double blowUpDistributedFile = blowUp(avgLoCDistributedFile, avgLocDistributedFileNormalized);
    double blowUpOverall = blowUp(avgLoCOverall, avgLoCOverallNormalized);

    Log.info("Total single-file-schemas: " + singleFilesCount);
    Log.info("Single-file-schemas Rekursion: " + recursiveCountSingleFiles);
    Log.info("Avg LoC single-file-schemas: " + avgLocSingleFile);
    Log.info("Avg LoC single-file-schemas normalized: " + avgLocSingleFileNormalized);
    Log.info("BlowUp single-file-schemas: " + blowUpSingleFile);
    Log.info("----------------------------------");
    Log.info("Total distributed-schemas: " + distributedFilesCount);
    Log.info("Distributed-schemas Rekursion: " + recursiveCountDistributedFiles);
    Log.info("Avg LoC distributed-schemas: " + avgLoCDistributedFile);
    Log.info("Avg LoC distributed-schemas normalized: " + avgLocDistributedFileNormalized);
    Log.info("BlowUp distributed-schemas: " + blowUpDistributedFile);
    Log.info("----------------------------------");
    Log.info("Avg LoC overall: " + avgLoCOverall);
    Log.info("Avg LoC overall normalized: " + avgLoCOverallNormalized);
    Log.info("BlowUp overall: " + blowUpOverall);
  }

  private static double blowUp(int base, int value) {
    return ((double) value) / base - 1;
  }
}
