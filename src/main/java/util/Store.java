package util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import exception.StoreException;

/**
 * Is used to store schemas in a directory. A uri is safed in a csv-File with the associate file
 * name.
 * 
 * @author Lukas Ellinger
 */
public class Store {
  private static int counter = 0;
  private static File dir = new File("Store");
  private static File csv = new File("UriOfFiles.csv");

  /**
   * Stores <code>object</code> in <code>dir</code> and makes an entry in <code>csv</code> with its
   * filename and <code>uri</code>.
   * 
   * @param object to be stored.
   * @param uri to associate <code>object</code> with.
   * @throws IOException if it cannot be stored or if entry to <code>csv</code> cannot be made.
   */
  public static void storeSchema(JsonObject object, URI uri) throws IOException {
    if (!uri.getScheme().equals("file")) {
      if (!dir.exists()) {
        dir.mkdir();
      }
      File file = new File(dir, "js_" + counter + ".json");
      SchemaUtil.writeJsonToFile(object, file);
      String[] line = {file.getName(), uri.toString()};
      CSVUtil.writeToCSV(csv, line);
      counter++;
    }
  }
  
  public static JsonObject getSchema(URI uri) throws StoreException, IOException {
    if (csv.exists()) {
      Reader in = new FileReader(csv);
      List<CSVRecord> records = CSVFormat.DEFAULT.withDelimiter(',').parse(in).getRecords();
      in.close();
      
      for (CSVRecord record : records) {
        if (record.get(1).equals(uri.toString())) {
          File file = new File(dir, record.get(0));
          return new Gson().fromJson(FileUtils.readFileToString(file, "UTF-8"), JsonObject.class);
        }
      }
      
      throw new StoreException("No file associate with " + uri + " found in store");
    } else {
      throw new StoreException(csv.getName() + " does not exist");
    }
  }
}
