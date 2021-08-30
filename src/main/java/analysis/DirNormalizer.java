package analysis;

import java.io.File;
import java.io.IOException;
import exception.DistributedSchemaException;
import exception.InvalidReferenceException;
import model.normalization.RepositoryType;
import util.Log;
import util.SchemaUtil;

/**
 * Used to normalize schemas in a directory.
 * 
 * @author Lukas Ellinger
 */
public class DirNormalizer {

  /**
   * Normalizes all valid schemas in <code>dir</code> and stores them.
   * 
   * @param dir directory of schemas to be normalized.
   * @param allowDistributedSchemas <code>true</code>, if remote refs to other schemas are allowed.
   *        Otherwise should be <code>false</code>.
   * @param repType type of Repository.
   * @throws IOException
   */
  public void normalize(File dir, boolean allowDistributedSchemas, RepositoryType repType)
      throws IOException {
    if (!dir.isDirectory()) {
      throw new IllegalArgumentException(dir.getName() + " needs to be a directory");
    }
    
    DirCleaner cleaner = new DirCleaner();
    cleaner.removeNoValidSchemas(dir);

    File normalizedDir = new File("Normalized_" + dir.getName());
    normalizedDir.mkdir();

    int invalidReference = 0;
    for (File schema : dir.listFiles()) {
      try {
        SchemaUtil.normalize(schema, null, normalizedDir, allowDistributedSchemas, repType);
      } catch (InvalidReferenceException e) {
        Log.warn(schema, e);
        invalidReference++;
      } catch (DistributedSchemaException e) {
        Log.warn(schema, e);
      }
    }

    Log.info("Normalization process:");
    Log.info("Invalid reference: " + invalidReference);
    Log.info("----------------------------------");
  }
}
