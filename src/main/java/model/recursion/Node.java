package model.recursion;

import com.google.gson.JsonElement;

/**
 * Encapsulates a <code>JsonElement</code> and a attribute whether it is guarded.
 * 
 * @author Lukas Ellinger
 */
public class Node {

  private JsonElement element;
  private boolean guarded;

  public Node(JsonElement element, boolean guarded) {
    this.element = element;
    this.guarded = guarded;
  }

  public boolean isGuarded() {
    return guarded;
  }

  public void setGuarded(boolean guarded) {
    this.guarded = guarded;
  }

  public JsonElement getElement() {
    return element;
  }

  public void setElement(JsonElement element) {
    this.element = element;
  }

  /**
   * Only checks whether stored <code>element</code> equals. If it is guarded is ignored.
   */
  @Override
  public boolean equals(Object other) {
    if (other instanceof Node) {
      return this.element.equals(((Node) other).element);
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return element + " guarded:" + guarded;
  }
}
