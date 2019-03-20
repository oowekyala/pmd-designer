/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.util;

import static net.sourceforge.pmd.util.fxdesigner.util.DesignerIteratorUtil.parentIterator;
import static net.sourceforge.pmd.util.fxdesigner.util.DesignerIteratorUtil.reverse;
import static net.sourceforge.pmd.util.fxdesigner.util.DesignerIteratorUtil.toIterable;
import static net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil.or;
import static net.sourceforge.pmd.util.fxdesigner.util.codearea.PmdCoordinatesSystem.endPosition;
import static net.sourceforge.pmd.util.fxdesigner.util.codearea.PmdCoordinatesSystem.findNodeAt;
import static net.sourceforge.pmd.util.fxdesigner.util.codearea.PmdCoordinatesSystem.findNodeCovering;
import static net.sourceforge.pmd.util.fxdesigner.util.codearea.PmdCoordinatesSystem.rangeOf;

import java.util.Iterator;
import java.util.Optional;

import net.sourceforge.pmd.lang.ast.Node;

/**
 * @author Clément Fournier
 */
public final class AstTraversalUtil {

    private AstTraversalUtil() {

    }


    public static Node getRoot(Node n) {
        return n == null ? null
                         : n.jjtGetParent() == null
                           ? n : getRoot(n.jjtGetParent());
    }

    /**
     * Tries hard to find the node in [myRoot] that corresponds most closely
     * to the given [node], which may be from another tree.
     *
     * @param myRoot (Nullable) root of the tree in which to search
     * @param node   (Nullable) node to look for
     */
    public static Optional<Node> mapToMyTree(final Node myRoot, final Node node) {
        if (myRoot == null || node == null) {
            return Optional.empty();
        }

        if (AstTraversalUtil.getRoot(node) == myRoot) {
            return Optional.of(node); // same tree
        } else {
            return
                or(
                    or(
                        // first try with path
                        findOldNodeInNewAst(node, myRoot),
                        // then try with exact range
                        () -> findNodeCovering(myRoot, rangeOf(node), true)
                    ),
                    // fallback on leaf if nothing works
                    () -> findNodeAt(myRoot, endPosition(node)
                    )
                );
        }
    }


    /**
     * @param oldSelection Not null
     * @param newRoot      Not null
     */
    public static Optional<Node> findOldNodeInNewAst(final Node oldSelection, final Node newRoot) {
        if (oldSelection.jjtGetParent() == null) {
            return Optional.of(newRoot);
        }

        Iterator<Node> pathFromOldRoot = reverse(parentIterator(oldSelection, true));

        pathFromOldRoot.next(); // skip root

        Node currentNewNode = newRoot;

        for (Node step : toIterable(pathFromOldRoot)) {

            int n = step.jjtGetChildIndex();

            if (n >= 0 && n < currentNewNode.jjtGetNumChildren()) {
                currentNewNode = currentNewNode.jjtGetChild(n);
            } else {
                return Optional.empty();
            }
        }

        return currentNewNode.getXPathNodeName().equals(oldSelection.getXPathNodeName())
               ? Optional.of(currentNewNode) : Optional.empty();
    }
}
