package analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import model.normalization.Normalizer;
import util.Converter;
import util.Log;
import util.SchemaUtil;

/**
 * Used to verify normalization
 * 
 * @author Lukas Ellinger
 */
public class SchemaStoreDataVerifier {
  private final static String TESTDATA_DIR = "/Users/lukasellinger/Library/testData";
  private final static String SCHEMA_DIR = "/Users/lukasellinger/Library/currentSchema";

  public static void main(String[] args) {
    checkForCorrectNormalization(true);
  }

  /**
   * Checks whether the normalization was correct for the schemaStore. Therefore it is checked
   * whether the test data for a schema is still valid for the normalized schema. Or if not, if it
   * is not valid for the normalized schema too. If it is not equal, then there is a entry written
   * in the log-file.
   * 
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   */
  public static void checkForCorrectNormalization(boolean allowDistributedSchemas) {
    List<Pair<File, File[]>> schemas = getTestDataFiles();
    for (Pair<File, File[]> schema : schemas) {
      File schemaFile = schema.getLeft();
      try {
        JSONObject normalizedSchema =
            Converter.toJSON(new Normalizer(schemaFile, allowDistributedSchemas).normalize());
        JSONObject unnormalizedSchema =
            new JSONObject(FileUtils.readFileToString(schemaFile, "UTF-8"));

        updateSchema(normalizedSchema, unnormalizedSchema);

        for (File file : schema.getRight()) {
          Object testData = new JSONTokener(FileUtils.readFileToString(file, "UTF-8")).nextValue();

          if (SchemaUtil.isValid(SchemaLoader.load(unnormalizedSchema), testData) != SchemaUtil
              .isValid(SchemaLoader.load(normalizedSchema), testData)) {

            Log.warn(schemaFile.getName() + " and its normalization do not match on accepted data");
          }
        }
      } catch (Exception e) {
        Log.severe(schemaFile, e);
      }
    }
  }

  private static void updateSchema(JSONObject one, JSONObject another) {
    if (one.has("$schema") && (SchemaUtil.getValidationDraftNumber(one) == 6)) {
      one.put("$schema", "http://json-schema.org/draft-06/schema#");
      another.put("$schema", "http://json-schema.org/draft-06/schema#");
    }

    if (one.has("$schema") && (SchemaUtil.getValidationDraftNumber(one) == 4)) {
      another.put("$schema", "http://json-schema.org/draft-04/schema#");
      another.put("$schema", "http://json-schema.org/draft-04/schema#");
    }
  }

  private static List<Pair<File, File[]>> getTestDataFiles() {
    File[] testDataDirs = new File(TESTDATA_DIR).listFiles();
    File[] schemas = new File(SCHEMA_DIR).listFiles();
    List<Pair<File, File[]>> test = new ArrayList<>();
    for (File file : testDataDirs) {
      for (File schema : schemas) {
        if (schema.getName().equals(file.getName() + ".json")) {
          File[] testData = file.listFiles();
          test.add(new ImmutablePair<>(schema, testData));
          break;
        }
      }
    }

    return test;
  }
}
