# TODO before going public

* Review release procedure
    * How do we configure the maven release plugin and with which credentials?
* Should we introduce the 4-segment versioning system for pmd-ui before 7.0.0? It could
be confusing to users, and there’s probably not many releases left before 7.0.0 anyway
    * E.g. releasing pmd-ui:6.12.0.1 instead of 6.13.0 could be weird,
    especially so since that version is in fact compatible with pmd-core:6.11.0.
    pmd-ui:6.11.0.1 would be even weirder and would in fact be lower in version
    ranges than 6.12.0

* This repo is to be moved to the pmd org


* Move [open issues](https://github.com/pmd/pmd/labels/in%3Aui)
  * Close the designer project on pmd/pmd

* Delete the pmd-ui directory from the main repo
  * Basically just https://github.com/oowekyala/pmd/commit/cc44bac3c3b8e0e680f8dd6c9da2898c2e39b7d9
  * Document the change:
    * in CONTRIBUTING.md, README.md
    * in the issue template of pmd/pmd
    * on the mailing list?



## Differences from the current pmd-ui in the main repo

* Some IntelliJ config files are checked in VCS to ease installation
  * You're welcome to check in Eclipse config files as well
* The jar artifact is a shaded Jar:
    *  It doesn't include the pmd dependencies
    *  It relocates dependencies that are both depended-on by pmd-core and this
       module (apache)
    *  It's a multi-release jar. That's because ControlsFX has two incompatible
    versions to support JavaFX 8 and 9+. More is explained in comments in the
    POM.
    *  There are profiles for IDE maven import (m2e and IJ) to avoid having the
    language modules as provided. This is similar to what pmd-core does with the
    Jaxen shaded jar.
* The PMD ruleset specific to pmd-ui is in this repo (see config dir)
  * It was a pain to update build-tools when we add a new control with a
  specific naming convention


If you want to test that the multi-release jar works:

```shell
# NB: set variable $YOUR_PMD_SOURCE_REPO

# that branch uses the bread crumb bar, whose java 8 implementation is
# incompatible with JRE 9+
# On master the multi-release jar isn't necessary yet

git co designer-breadcrumbbar
mvn install
cd $YOUR_PMD_SOURCE_REPO
mvn package -Dmaven.javadoc.skip -DskipTests -pl pmd-dist

tmpdir=$(mktemp -d)

cp -f pmd-dist/target/pmd-bin-6.13.0-SNAPSHOT.zip "$tmpdir"
cd "$tmpdir"

unzip -o pmd-bin-6.13.0-SNAPSHOT.zip
pmd-bin-6.13.0-SNAPSHOT/bin/run.sh designer -v &disown

# then switch java versions and check it still works

```

If you want to try plugging the artifact into eg a pmd-bin-6.11.0,
go into the lib dir and delete the following dependencies:

```shell

rm ikonli-* \
   pmd-ui-6.11.0.jar \
   controlsfx-8.40.13.jar \
   undofx-2.1.0.jar \
   richtextfx-0.9.2.jar \
   flowless-0.6.jar \
   wellbehavedfx-0.3.3.jar \
   reactfx-2.0-M5.jar \
   commons-beanutils-core-1.8.3.jar
```

Then you can just copy your `pmd-ui-6.13.0-SNAPSHOT.jar`
and run the designer as usual with run.sh.

---------------
---------------

# PMD Rule Designer


The Rule Designer is a graphical tool that helps PMD users develop their custom
rules.

TODO Gifs



## Installation

The designer is part of PMD's binary distributions.

TODO release a fat jar containing PMD too using classifiers?

TODO describe minimum Java config

## Usage

TODO put usage doc on the main website


## Contributing

TODO describe packaging procedure, branching model, versioning system

### IDE Setup

#### IntelliJ IDEA

1. Clone the repository
1. Open in IntelliJ
1. [Open IntelliJ's terminal](https://stackoverflow.com/a/28044371/6245827) and
paste the following:
```shell
git update-index --skip-worktree -- .idea/misc.xml pmd-ui.iml # Ignore some config files
mvn process-resources # Generate CSS resources
```

4. [Synchronize the directory contents](https://stackoverflow.com/a/4599243/6245827) to pick-up on the new CSS files
1. Invoke the [Reimport All Maven Projects Action](https://stackoverflow.com/a/29765077/6245827)
1. You can now run the designer with the existing Run Configurations

1. Install the [File Watchers](https://plugins.jetbrains.com/plugin/7177-file-watchers) 
plugin to compile the Less files to CSS when you edit them. Configuration is already
in your repo because it was cloned in step 1. The CSS files are generated into an 
ignored resource directory so that the integrated SceneBuilder picks up on them.

TODO make Gifs?


#### Eclipse

TODO
