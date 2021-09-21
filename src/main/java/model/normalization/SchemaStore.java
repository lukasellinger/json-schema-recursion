package model.normalization;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonElement;
import exception.DistributedSchemaException;

/**
 * Used for encapsulation of a <code>URI</code> and the lists of all visited
 * <code>JsonElements</code> and all already loaded <code>SchemaFiles</code>.
 * 
 * @author Lukas Ellinger
 */
public class SchemaStore {
  private boolean allowDistributedSchemas;
  private RepositoryType repType;
  private URI root;
  private List<JsonElement> visited = new ArrayList<>();
  private List<SchemaFile> loadedFiles = new ArrayList<>();

  /**
   * Stores <code>schema</code> as root and adds it to the <code>loadedFiles</code>.
   * 
   * @param rootSchemaFile top-level schema.
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   * @param repType type of Repository.
   */
  public SchemaStore(SchemaFile rootSchemaFile, boolean allowDistributedSchemas,
      RepositoryType repType) {
    this.allowDistributedSchemas = allowDistributedSchemas;
    this.repType = repType;
    this.root = rootSchemaFile.getId();
    loadedFiles.add(rootSchemaFile);
  }
  
  public SchemaStore(boolean allowDistributedSchemas, RepositoryType repType) {
    this.allowDistributedSchemas = allowDistributedSchemas;
    this.repType = repType;
  }
  
  public void addRootSchemaFile(SchemaFile rootSchemaFile) {
    this.root = rootSchemaFile.getId();
    loadedFiles.add(rootSchemaFile);
  }

  public URI getRoot() {
    return root;
  }

  public RepositoryType getRepType() {
    return repType;
  }
  
  public List<SchemaFile> getLoadedFiles() {
    return loadedFiles;
  }

  /**
   * Gets <code>SchemaFile</code> of <code>identifier</code>. If the corresponding is already stored
   * in <code>loadedFiles</code>, then the stored one is returned. Otherwise the new
   * <code>SchemaFile</code> is added to <code>loadedFiles</coded> and returned.
   * 
   * @param identifier of which the <code>SchemaFile</code> should be returned.
   * @return <code>SchemaFile</code> of <code>identifier</code>. If the corresponding is already
   *         stored in <code>loadedFiles</code>, then the stored one is returned
   */
  public SchemaFile getLoadedFile(URI identifier) {
    for (SchemaFile loadedSchema : loadedFiles) {
      if (loadedSchema.getId().equals(identifier)) {
        return loadedSchema;
      }
    }

    if (allowDistributedSchemas) {
      SchemaFile schema = new SchemaFile(identifier, this);
      loadedFiles.add(schema);
      return schema;
    } else {
      throw new DistributedSchemaException(
          "Schema has a ref which is pointing outside of the schema: " + identifier);
    }
  }

  /**
   * Adds <code>element</code> to the visited <code>JsonElements</code>.
   * 
   * @param element to be added.
   */
  public void addVisited(JsonElement element) {
    visited.add(element);
  }

  /**
   * Checks whether <code>element</code> as already been visited.
   * 
   * @param element to be checked.
   * @return <code>true</code>, if it has already been visited. <code>false</code>, if not.
   */
  public boolean alreadyVisited(JsonElement element) {
    return visited.contains(element);
  }

  /**
   * Checks whether <code>schema</code> has the same <code>id</code> as <code>root</code> of this.
   * 
   * @param schema to be checked.
   * @return <code>true</code>, if <code>id</code> of <code>schema</code> equals <code>root</code>.
   *         <code>false</code>, if not.
   */
  public boolean isRoot(SchemaFile schema) {
    return root.equals(schema.getId());
  }
}
