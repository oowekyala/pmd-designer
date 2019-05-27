/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.util.controls;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.reactfx.Subscription;
import org.reactfx.value.Var;

import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.KeyCode;
import javafx.util.Pair;

/**
 * A copycat of {@link TextFieldListCell}, because it deletes the graphic and
 * is anyway quite simple.
 */
public abstract class SmartTextFieldListCell<T> extends ListCell<T> {

    private TextField textField;

    private Subscription subscriber;

    @Override
    public final void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (isEmpty() || item == null) {
            setGraphic(null);
            textField = null;
            if (subscriber != null) {
                subscriber.unsubscribe();
                subscriber = Subscription.EMPTY;
            }
        } else {
            if (isEditing()) {
                if (textField != null) {
                    textField.setText(extractEditable(item).getValue());
                } else {
                    textField = getEditingGraphic(item);
                }
                setGraphic(textField);
            } else {
                Pair<Node, Subscription> nodeAndSub = getNonEditingGraphic(item);

                setGraphic(nodeAndSub.getKey());
                if (subscriber != null) {
                    subscriber.unsubscribe();
                }
                subscriber = nodeAndSub.getValue();
            }
        }
    }

    protected abstract Pair<Node, Subscription> getNonEditingGraphic(T testCase);


    protected abstract Var<String> extractEditable(T t);


    @Nullable
    protected String getPrompt() {
        return null;
    }

    private final TextField getEditingGraphic(T t) {
        Var<String> stringVar = extractEditable(t);
        final TextField textField = new TextField(stringVar.getValue());

        textField.setPromptText(getPrompt());

        // Use onAction here rather than onKeyReleased (with check for Enter),
        // as otherwise we encounter RT-34685
        textField.setOnAction(event -> {
            stringVar.setValue(textField.getText());
            commitEdit(t);
            event.consume();
        });
        textField.setOnKeyReleased(ke -> {
            if (ke.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
                ke.consume();
            }
        });
        return textField;
    }


    @Override
    public final void startEdit() {
        super.startEdit();
        if (textField == null) {
            textField = getEditingGraphic(getItem());
        }
        textField.setText(extractEditable(getItem()).getValue());

        setGraphic(textField);

        textField.selectAll();

        // requesting focus so that key input can immediately go into the
        // TextField (see RT-28132)
        textField.requestFocus();
    }

    @Override
    public final void cancelEdit() {
        super.cancelEdit();
        Pair<Node, Subscription> nonEditingGraphic = getNonEditingGraphic(getItem());
        if (subscriber != null) {
            subscriber.unsubscribe();
        }
        subscriber = nonEditingGraphic.getValue();
        setGraphic(nonEditingGraphic.getKey());
    }
}
