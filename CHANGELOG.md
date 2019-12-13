# Changelog



## 6.21.0

* **Bump required pmd-core version to 6.20.0.** The previous minimal pmd-core version was 6.12.0.
* Add Modelica syntax highlighter, thanks to @atrosinenko
* Add scrollbars to code area (fixes #22)
* Internal improvements (refs #33)

See https://github.com/pmd/pmd-designer/milestone/9


## 6.19.0

* Add scala syntax highlighter
* When no language modules are on the classpath, the app will still run normally, opening files with a "plain text" language and displaying a warning.
* Fix [#27](https://github.com/pmd/pmd-designer/issues/27): Rule export escapes special characters in rules unnecessarily
* Fix some internal issues
  * Version stamping not working

See https://github.com/pmd/pmd-designer/milestone/7

## 6.17.0

* Added test case edition features. Usage is [documented on the wiki](https://github.com/pmd/pmd-designer/wiki/Testing_rules). Preview:

![testing-designer](https://user-images.githubusercontent.com/24524930/61461094-504a7900-a970-11e9-822e-30cc121b568c.gif)


## 6.16.0

* Added the possibility to search the treeview. Press CTRL+F when it's focused, or click on the `Search` :mag_right: button and enter a search query. You can cycle through results with `CTRL+TAB` or `CTRL+F3`, and cycle back with `CTRL+SHIFT+TAB` or `CTRL+SHIFT+F3`.

![TreeView search demo](https://user-images.githubusercontent.com/24524930/58752348-a3926600-84ad-11e9-9ef2-11920590b5e5.gif)

* Fix some bugs with XPath autocompletion

## 6.14.0

* Added the possibility to select a node directly from the code area.
To use this, hold the CTRL key pressed for a second. The code area should
change colors. In that state, hovering above some text should select the
node to which it corresponds:


![Node selection demo](https://user-images.githubusercontent.com/24524930/53699223-3c014780-3de6-11e9-9c6b-b9382a3c1117.gif)

* Added a crumb bar that displays the ancestors of the currently selected node.

* When you have selected a node and start editing the code again, your selection
should be preserved in most cases (on a best-effort basis) instead of reset.

* Improved the XPath export wizard

* Allow multiple independent xpath editors
