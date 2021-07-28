package util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map.Entry;
import org.apache.commons.io.FileUtils;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exception.DraftValidationException;
import model.Draft;
import model.normalization.Normalizer;

/**
 * Offers utils for JSON Schemas.
 * 
 * @author Lukas Ellinger
 */
public class SchemaUtil {

  /**
   * Checks whether a object is valid to a schema. <code>org.json.</code> is used.
   * 
   * @param schema to validate testData. Has to be valid to Draft04, 06 or 07.
   * @param testData to be validated.
   * @return <code>true</code>, if it is valid. <code>false</code>, if not.
   */
  public static boolean isValid(Schema schema, Object testData) {
    try {
      schema.validate(testData);
      return true;
    } catch (ValidationException e) {
      return false;
    }
  }

  /**
   * Checks whether a schema is valid to its specified draft. If no draft was specified or does not
   * equal draft04, draft06 or draft07, draft04 will be used for verification.
   * 
   * @param file to be checked.
   * @return <code>true</code>, if it is valid to the draft. <code>false</code> if not.
   */
  public static boolean isValidToDraft(File file) {
    try {
      JSONObject fileSchema =
          (JSONObject) new JSONTokener(FileUtils.readFileToString(file, "UTF-8")).nextValue();
      int validationDraftNumber = getValidationDraftNumber(fileSchema);

      try (InputStream draftStream = SchemaUtil.class.getClassLoader()
          .getResourceAsStream("drafts/draft" + validationDraftNumber + ".json")) {
        JSONObject draft = (JSONObject) new JSONTokener(draftStream).nextValue();
        SchemaLoader.load(draft).validate(fileSchema);
        return true;
      } catch (ValidationException e) {
        Log.severe(file,
            new DraftValidationException("Specified draft in Document: "
                + getDraftString(fileSchema) + " Used for validation: Draft0"
                + validationDraftNumber + " - " + e.getMessage()));
      }
    } catch (Exception e) {
      Log.severe(file, e);
    }
    return false;
  }

  /**
   * Checks whether <code>file</code> contains a valid JSON schema. Uses Draft04 as default. Draft06
   * and Draft07 is also supported if they are used in "$schema".
   * 
   * @param file to be checked.
   * @return <code>true</code>, if it contains a valid JSON schema. <code>false</code>, if not.
   * @throws IOException if file cannot be loaded or does not conatain valid JSON.
   */
  public static boolean isValidSchema(File file) throws IOException {
    JSONObject fileObject = new JSONObject(FileUtils.readFileToString(file, "UTF-8"));

    try {
      SchemaLoader.load(fileObject);
      return true;
    } catch (ValidationException e) {
      return false;
    }
  }

  /**
   * Gets the id of <code>object</code>. If there is no id then an <code>URI</code> with an empty
   * string is returned.<code>com.google.gson.JsonObject</code> is used.
   * 
   * @param object to get the id from.
   * @return id of <code>object</code>. If there is no id then <code>URI</code> with an empty
   *         string.
   * @throws URISyntaxException if id is no valid <code>URI</code>.
   */
  public static URI getId(JsonObject object, Draft draft) throws URISyntaxException {
    if (draft.equals(Draft.Draft4)) {
      if (object.has("id") && object.get("id").isJsonPrimitive()) {
        return URIUtil.toURI(object.get("id").getAsString());
      }
    } else if (draft.equals(Draft.DraftHigher)) {
      if (object.has("$id") && object.get("$id").isJsonPrimitive()) {
        return URIUtil.toURI(object.get("$id").getAsString());
      }
    }

    return new URI("");
  }

  /**
   * Gets its draft to be used.
   * 
   * @param object to get draft from.
   * @return <code>Draft4</code>, if "id" should be used. <code>DraftHigher</code>, if "$id" should
   *         be used.
   */
  public static Draft getDraft(JsonObject object) {
    if (getValidationDraftNumber(object) == 4) {
      return Draft.Draft4;
    } else {
      return Draft.DraftHigher;
    }
  }

  /**
   * Gets the specified draft of <code>object</code>. <code>org.json.JSONObject</code> is used.
   * 
   * @param object of which the draft should be returned.
   * @return specified draft of <code>object</code>. <code>null</code>, if there is no schema
   *         specified.
   */
  public static String getDraftString(JSONObject object) {
    if (object.has("$schema")) {
      return object.get("$schema").toString();
    } else {
      return null;
    }
  }

  /**
   * Gets draft version number of <code>string</code>. If a not supported one is used, 4 is returned
   * for draft03 and 6 for everything else. <code>com.google.gson.JsonObject</code> is used.
   * 
   * @param object to get the draft version number from.
   * @return 7, if draft07, 4, if draft03 or 04 and 6 for everything else .
   */
  public static int getValidationDraftNumber(JsonObject object) {
    if (object.has("$schema")) {
      String draft = object.get("$schema").getAsString();

      if (draft.contains("draft-07")) {
        return 7;
      } else if (draft.contains("draft-06")) {
        return 6;
      } else if (draft.contains("draft-04") || draft.contains("draft-03")) {
        return 4;
      }
    }

    return getDraftOfIdKeyword(object);
  }

  private static int getDraftOfIdKeyword(JsonElement element) {
    if (element.isJsonObject()) {
      return getRecursiveDraftOfIdKeyword(element.getAsJsonObject());
    } else if (element.isJsonArray()) {
      return getRecursiveDraftOfIdKeyword(element.getAsJsonArray());
    } else {
      return 4;
    }
  }

  private static int getRecursiveDraftOfIdKeyword(JsonObject object) {
    if (object.has("$id")) {
      return 6;
    } else {
      for (Entry<String, JsonElement> entry : object.entrySet()) {
        if (!entry.getKey().equals("enum")) {
          int draft = getDraftOfIdKeyword(entry.getValue());
          if (draft == 6) {
            return draft;
          }
        }
      }

      return 4;
    }
  }

  private static int getRecursiveDraftOfIdKeyword(JsonArray array) {
    for (JsonElement element : array) {
      int draft = getDraftOfIdKeyword(element);
      if (draft == 6) {
        return draft;
      }
    }
    return 4;
  }

  /**
   * Gets draft version number of <code>string</code>. If a not supported one is used, 4 is returned
   * for draft03 and 6 for everything else. <code>org.json.JSONObject</code> is used.
   * 
   * @param object to get the draft version number from.
   * @return 7, if draft07, 4, if draft03 or 04 and 6 for everything else .
   */
  public static int getValidationDraftNumber(JSONObject object) {
    return getValidationDraftNumber(Converter.toJson(object));
  }

  /**
   * Deletes all schemas which are not valid for draft0<code>i</code>. Draft04, 06 and 07 are
   * supported.
   * 
   * @param dir directory in which the schemas are.
   * @param i draftNumber. 4, 6 and 7 are supported.
   * @throws JSONException if draftschema is corrupt.
   * @throws IOException if draftfile cannot be loaded.
   */
  public static void deleteInvalidSchemasForDraft(File dir, int i)
      throws JSONException, IOException {
    JSONObject draft = (JSONObject) new JSONTokener(
        SchemaUtil.class.getClassLoader().getResourceAsStream("drafts/draft" + i + ".json"))
            .nextValue();
    for (File file : dir.listFiles()) {
      JSONObject obj;
      try {
        obj = (JSONObject) new JSONTokener(FileUtils.readFileToString(file, "UTF-8")).nextValue();

        if (!isValid(SchemaLoader.load(draft), obj)) {
          file.delete();
        }
      } catch (Exception e) {
        file.delete();
      }
    }
  }

  /**
   * Removes the id in <code>element</code>. <code>com.google.gson.JsonObject</code> is used.
   * 
   * @param element in which id should be removed.
   */
  public static void removeIdInElement(JsonElement element) {
    if (element.isJsonObject()) {
      JsonObject object = element.getAsJsonObject();
      if (object.has("$id") && object.get("$id").isJsonPrimitive()) {
        try {
          object.get("$id").getAsString();
          object.remove("$id");
        } catch (ClassCastException e) {
        }
      } else if (object.has("id") && object.get("id").isJsonPrimitive()) {
        try {
          object.get("id").getAsString();
          object.remove("id");
        } catch (ClassCastException e) {
        }
      }
    }
  }

  /**
   * Removes all ids in <code>object</code> including ids in subschemas.
   * <code>com.google.gson.JsonObject</code> is used.
   * 
   * @param object in which the ids should be removed.
   */
  public static void removeIds(JsonObject object) {
    for (Entry<String, JsonElement> entry : object.entrySet()) {
      if (!entry.getKey().equals("enum")) {
        removeRecursiveIds(entry.getValue());
      }
    }
  }

  private static void removeRecursiveIds(JsonElement element) {
    if (element.isJsonObject()) {
      removeRecursiveIds(element.getAsJsonObject());
    } else if (element.isJsonArray()) {
      removeRecursiveIds(element.getAsJsonArray());
    }
  }

  private static void removeRecursiveIds(JsonObject object) {
    removeIdInElement(object);
    for (Entry<String, JsonElement> entry : object.entrySet()) {
      if (!entry.getKey().equals("enum")) {
        removeRecursiveIds(entry.getValue());
      }
    }
  }

  private static void removeRecursiveIds(JsonArray array) {
    for (JsonElement element : array) {
      removeRecursiveIds(element);
    }
  }

  /**
   * Deletes all schemas which are not using <code>draft</code>. This simply checks whether
   * <code>"$schema"</code> contains <code>draft</code>.
   * 
   * @param dir directory in which the schemas are.
   * @param draft of which schemas should be kept.
   */
  public static void deleteSchemasNotUsingDraft(File dir, String draft) {
    for (File file : dir.listFiles()) {
      JSONObject obj;
      try {
        obj = (JSONObject) new JSONTokener(FileUtils.readFileToString(file, "UTF-8")).nextValue();

        if (getDraftString(obj).contains(draft)) {
          file.delete();
        }
      } catch (Exception e) {
        file.delete();
      }
    }
  }

  /**
   * Stores <code>element</code> in <code>file</code>.
   * 
   * @param element to be stored.
   * @param file to store <code>element</code> in.
   * @throws IOException
   */
  public static void writeJsonToFile(JsonElement element, File file) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    FileUtils.writeStringToFile(file, gson.toJson(element), "UTF-8");
  }

  /**
   * Normalizes the schema in <code>unnormalized</code> and stores it under the directory
   * <code>store</code>.
   * 
   * @param unnormalized file of schema to be normalized.
   * @param uri base uri of schema. If <code>null</code>, <code>URI</code> of
   *        <code>unnormalized</code> is used.
   * @param store to store normalized schema.
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   * @return normalized schema.
   * @throws IOException
   */
  public static JsonObject normalize(File unnormalized, URI uri, File store,
      boolean allowDistributedSchemas) throws IOException {
    Normalizer normalizer;
    if (uri != null) {
      normalizer = new Normalizer(unnormalized, uri, allowDistributedSchemas);
    } else {
      normalizer = new Normalizer(unnormalized, allowDistributedSchemas);
    }
        
    File normalizedFile =
        new File(store, getNormalizedFileName(unnormalized.getName()));

    JsonObject normalizedSchema = normalizer.normalize();
    writeJsonToFile(normalizedSchema, normalizedFile);
    return normalizedSchema;
  }
  
  /**
   * Gets the filename of the normalized schema.
   * 
   * @param unnormalizedFileName to get normalized filename of.
   * @return normalized filename.
   */
  public static String getNormalizedFileName(String unnormalizedFileName) {
    int lastOccurence = unnormalizedFileName.lastIndexOf(".json");
    
    if (lastOccurence != -1) {
      unnormalizedFileName = unnormalizedFileName.substring(0, lastOccurence);
      return unnormalizedFileName + "_Normalized.json";
    } else {
      throw new IllegalArgumentException(unnormalizedFileName + " does not equal normal filename");
    }
  }
    
  /**
   * Gets the definitions object of <code>schema</code>. If there is no definitions object, then an
   * empty <code>JsonObject</code> gets returned.
   * 
   * @param schema of which definitions should be fetched.
   * @return definitions object of <code>schema</code>.
   */
  public static JsonObject getDefinitions(JsonObject schema) {
    JsonObject defs;

    if (schema.get("definitions") != null) {
      defs = schema.get("definitions").getAsJsonObject();
    } else {
      defs = new JsonObject();
    }

    return defs;
  }
}
