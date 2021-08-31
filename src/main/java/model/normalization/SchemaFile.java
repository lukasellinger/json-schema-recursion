package model.normalization;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Stack;
import org.apache.commons.io.FileUtils;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import exception.InvalidIdentifierException;
import exception.StoreException;
import model.Draft;
import util.SchemaUtil;
import util.Store;
import util.URIUtil;
import util.URLLoader;

/**
 * Encapsulates a <code>URI</code> with its content parsed as a <code>JsonObject</code> and a
 * <code>SchemaStore</code>.
 * 
 * @author Lukas Ellinger
 */
public class SchemaFile {
  private static final String TESTSUITE_REMOTES_DIR = "/home/TestSuiteDraft4/remotes/";
  private URI id;
  private JsonObject object;
  private SchemaStore store;
  private Stack<URI> resScope = new Stack<>();
  private Draft draft;

  /**
   * Creates a new <code>SchemaFile</code>. The stored <code>SchemaStore</code> is initialized with
   * this.
   * 
   * @param file of which the <code>SchemaFile</code> should be created.
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   * @param repType type of Repository.
   */
  public SchemaFile(File file, boolean allowDistributedSchemas, RepositoryType repType) {
    this.id = file.toURI();
    store = new SchemaStore(allowDistributedSchemas, repType);
    loadJsonObject();
    draft = SchemaUtil.getDraft(object);
    setIdFromSchema();
    store.addRootSchemaFile(this);
  }

  /**
   * 
   * @param id of where the schema is localized.
   * @param store to use.
   */
  public SchemaFile(URI id, SchemaStore store) {
    this.id = id;
    this.store = store;
    loadJsonObject();
    draft = SchemaUtil.getDraft(object);
    setIdFromSchema();
  }

  /**
   * 
   * @param file of which the <code>SchemaFile</code> should be created.
   * @param id location of where the file is from. Is used as id if no id is declared in the schema.
   * @param allowDistributedSchemas <code>true</code>, if remote references are allowed.
   *        <code>false</code>, if not.
   * @param repType type of Repository.
   */
  public SchemaFile(File file, URI id, boolean allowDistributedSchemas, RepositoryType repType) {
    this.id = file.toURI();
    store = new SchemaStore(allowDistributedSchemas, repType);
    loadJsonObject();
    draft = SchemaUtil.getDraft(object);
    this.id = id;
    setIdFromSchema();
    store.addRootSchemaFile(this);
  }

  public URI getRoot() {
    return store.getRoot();
  }

  public Draft getDraft() {
    return draft;
  }

  private void loadJsonObject() {
    Gson gson = new Gson();

    try {
      try {
        object = Store.getSchema(id);
      } catch (StoreException e) {
        object = gson.fromJson(URLLoader.loadWithRedirect(id.toURL()), JsonObject.class);
        Store.storeSchema(object, id);
      }
    } catch (IOException | JsonSyntaxException e) {
      try {
        if (store.getRepType().equals(RepositoryType.TESTSUITE)) {
          File file = new File(
              id.toString().replace("http://localhost:1234/", TESTSUITE_REMOTES_DIR));
          object = gson.fromJson(FileUtils.readFileToString(file, "UTF-8"), JsonObject.class);
          Store.storeSchema(object, id);
        } else if (store.getRepType().equals(RepositoryType.CORPUS)) {
          try {
            URI idRaw = new URI(id.getScheme(), id.getAuthority(), id.getPath(), "raw=true",
                id.getFragment());
            object = gson.fromJson(URLLoader.loadWithRedirect(idRaw.toURL()), JsonObject.class);
            Store.storeSchema(object, id);
          } catch (URISyntaxException e1) {
            throw new InvalidIdentifierException(id + " is no valid URI with query raw=true");
          }
        } else {
          throw new InvalidIdentifierException("Schema with " + id + " cannot be loaded");
        }
      } catch (IOException e2) {
        throw new InvalidIdentifierException("Schema with " + id + " cannot be loaded");
      } catch (JsonSyntaxException e2) {
        throw new InvalidIdentifierException("At " + id + " is no valid JsonObject");
      }
    }
  }

  /**
   * If there is a id set in schema then this one <code>id</code> is set to this.
   */
  private void setIdFromSchema() {
    try {
      URI schemaId = SchemaUtil.getId(object, draft);

      if (!schemaId.toString().equals("")) {
        URI resolved = id.resolve(schemaId);
        id = URIUtil.removeFragment(resolved);
      }
    } catch (IllegalArgumentException e) {
      throw new InvalidIdentifierException("id declared in schema is no valid URI");
    } catch (URISyntaxException e) {
      throw new InvalidIdentifierException(id + " has an invalid identifier in it");
    }
  }

  /**
   * Gets the current resolution scope.
   * 
   * @return current resolution scope you are in. On the highest level, <code>id</code> is returned.
   */
  public URI getResScope() {
    if (resScope.empty()) {
      return id;
    } else {
      return resScope.peek();
    }
  }

  /**
   * Sets the resolution scope to <code>scope</code>.
   * 
   * @param scope to be set. If <code>null</code> old resolution scope stays.
   */
  public void setResScope(URI scope) {
    if (scope.toString().equals("")) {
      resScope.push(getResScope());
    } else {
      resScope.push(URIUtil.removeTrailingHash(getResScope().resolve(scope)));
    }
  }

  /**
   * Sets the resolution scope to the previous one and returns it.
   * 
   * @return current resolution scope.
   */
  public URI oneScopeUp() {
    return resScope.pop();
  }

  /**
   * Sets the resolution scope to the scope of the top level schema.
   */
  public void setResScopeToTopLevel() {
    resScope.push(resScope.firstElement());
  }

  /**
   * Gets the relative path between the stored <code>root</code> in <code>store</code> and this.
   * 
   * @return relative path between the stored <code>root</code> in <code>store</code> and this.
   */
  public String getRelIdentifier() {
    Optional<String> rootScheme = Optional.ofNullable(store.getRoot().getScheme());
    Optional<String> idScheme = Optional.ofNullable(id.getScheme());
    Optional<String> rootAuthority = Optional.ofNullable(store.getRoot().getAuthority());
    Optional<String> idAuthority = Optional.ofNullable(id.getAuthority());

    if (!rootScheme.equals(idScheme) || !rootAuthority.equals(idAuthority)) {
      return id.toString();
    } else {
      File root = new File(store.getRoot().getPath());
      File idFile = new File(id.getPath());

      Optional<Path> rootPath = Optional.ofNullable(Paths.get(root.getAbsolutePath()).getParent());
      Path idPath = Paths.get(idFile.getAbsolutePath());
      if (rootPath.isEmpty()) {
        return idPath.toString().substring(1);
      } else {
        return rootPath.get().relativize(idPath).toString();
      }
    }
  }


  /**
   * Checks whether this has the same <code>id</code> as <code>root</code> of stored
   * <code>SchemaStore</code>.
   * 
   * @return <code>true</code>, if <code>id</code> equals <code>root</code>. <code>false</code>, if
   *         not.
   */
  public boolean isRootFile() {
    return store.isRoot(this);
  }

  /**
   * Adds <code>element</code> to the visited <code>JsonElements</code>.
   * 
   * @param element to be added.
   */
  public void addVisited(JsonElement element) {
    store.addVisited(element);
  }

  /**
   * Checks whether <code>element</code> as already been visited.
   * 
   * @param element to be checked.
   * @return <code>true</code>, if it has already been visited. <code>false</code>, if not.
   */
  public boolean alreadyVisited(JsonElement element) {
    return store.alreadyVisited(element);
  }

  /**
   * Gets <code>SchemaFile</code> of <code>file</code>. If the corresponding is already stored in
   * the <code>store</code>, then the stored one is returned. Otherwise the new
   * <code>SchemaFile</code> is added to the <code>store</coded> and returned.
   * 
   * &#64;param file of which the <code>SchemaFile</code> should be returned.
   * 
   * @return <code>SchemaFile</code> of <code>file</code>. If the corresponding is already stored in
   *         the <code>store</code>, then the stored one is returned
   */
  public SchemaFile getLoadedFile(URI identifier) {
    return store.getLoadedFile(resScope.peek().resolve(identifier));
  }

  public URI getId() {
    return id;
  }

  public JsonObject getObject() {
    return object;
  }

  /**
   * Only checks whether they have the same <code>identifier</code>.
   */
  @Override
  public boolean equals(Object other) {
    if (other instanceof SchemaFile) {
      SchemaFile otherSchema = (SchemaFile) other;
      return id.equals(otherSchema.id);
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return id.toString();
  }
}
