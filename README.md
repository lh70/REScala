# Chat

For TUDa members (with TU-ID) you should be able to directly join:
https://element.matrix.tu-darmstadt.de/#/room/#difosys:matrix.tu-darmstadt.de

Everyone else may join using any matrix connection:
https://matrix.to/#/#difosys:matrix.tu-darmstadt.de

# This Repository

This repository contains research software related to Programming Foundations for Distributed Systems at the [Software Technology Group](http://www.stg.tu-darmstadt.de/).

The two most likely modules in this project you are interested in are the library for reactive programming, and the library for replicated data types, both are detailed below.

All code in this repository is written in Scala and organized as a single [sbt](https://www.scala-sbt.org/) project containing multiple modules.

The simplest way to get started is to install [coursier](https://get-coursier.io/docs/cli-installation) – a single binary called `cs` – and then run `cs launch sbt` in the project root directory. This provides you with the sbt shell, where you can type `compile` or `test` to ensure that everything is working correctly. If you get strange errors you may be using a too new/old java version, try `cs launch --jvm=21 sbt` to force the use of Java 21 (will be downloaded).

> [!NOTE]
> Some JS tests require the `jsdom` npm package to be installed. Install it via `npm install jsdom` if you run into errors.

Type `projects` into the sbt shell to get an overview of the available modules. Many modules have a JVM/JS/Native/Aggregate suffix, indicating that this module is cross compiled to different backends. You can select a specific module in the sbt shell by running, for example, `project reactivesJVM`. Then the `compile` or `test` commands will only happen in the selected module. See below for an overview of the modules.

## IDE Setup

There are two IDE choices for Scala (and generally, the use of an IDE is recommended):

* IntelliJ with the Scala plugin: https://www.jetbrains.com/help/idea/get-started-with-scala.html
* Metals (a language server): https://scalameta.org/metals/

Generally, we recommend starting sbt from the command line (see above) first, and then using IntelliJ (with the Scala plugin) to open the project folder (the root folder, not any of the submodules). This should allow you to select `bsp` as the import type (instead of sbt). Using [bsp](https://www.scala-lang.org/blog/2020/10/27/bsp-in-sbt.html) means that IntelliJ is communicating directly with sbt to send commands to compile/test/run your code, providing the best compatibility.
Note that Scala support int IntelliJ is not perfect, it is possible that the IDE believes some correct code to not compile, or does not flag some errors. Fall back to trying the sbt command line directly if you have issues.

Metals also allows using [sbt as a bsp server](https://scalameta.org/metals/docs/build-tools/sbt/#sbt-build-server), but we don’t have much experience if this is necessary over the default import.

## Running the tests

# Reactives / REScala

REScala is a set of libraries for principled reactive programming,
a programming paradigm that integrates the strong guarantees of functional reactive programming into object oriented programs,
and is useful in many contexts such as traditional user interfaces, concurrent and distributed applications, web development, and server software.

* Homepage: <http://www.rescala-lang.com/>
* Usage Manual: <http://www.rescala-lang.com/manual>
* Maven Artifacts: https://index.scala-lang.org/rescala-lang/rescala/artifacts/rescala?pre-releases=false

# Replicated Data Types (ARDTs)

The RDT library is still being actively researched

* primary paper introducing the concept: https://drops.dagstuhl.de/entities/document/10.4230/LIPIcs.ECOOP.2023.14
* Maven Artifacts: https://index.scala-lang.org/rescala-lang/rescala/artifacts/kofre?pre-releases=false


# Repository Module Overview

This repository contains multiple subprojects in the __Modules__ folder:

* __Aead__: This is a simple Scala library that provides authenticated encryption with associated data using the same interfaces on the JVM and for Scala.js.
* __Example EncryptedTodoFx__
* __Example Misc 2015__: various example applications.
  * __examples__: misc examples involving swing, time, datastructures (no reswing, using swing directly)
    * __demo__: application design demonstrating, showing multiple steps of adding features to a reactive application
  * __reswing__: misc reswing examples
    * __millgame__: mill game using reswing
    * __reader__: imperative/reactive versions of a reswing RSS reader (actual network request, no support for failures yet)
    * __reshapes__: reswing drawing application
    * __texteditor__: basic text editor in different implementation styles (imperative, to full reactive)
  * __universe__: console application demoing parallel execution of simulations
* __Example Replication__: This case study demonstrates how the REScala project and replicated data types can be used to design an interactive application that makes use of one or more services within an arbitrary ad-hoc network.
* __Example Todolist__: Rescalatags todo application in the style of common todo applications
* __Graph-Compiler__
* __Javafx__
* __Microbenchmarks__: contains a benchmark suite for reactive programming.
  Execute with `jmh:run`
* __Reactives__: contains the main reactive programming library, for the JVM and JS.
  Also includes a concurrent scheduler for the JVM, making it thread safe.
* __Swing__: contains an integration with scalaswing to write UIs on the JVM.

The __Historical__ folder contains code that is no longer maintained:

* __Android/REAndroidThings__: contains an integration with androidthings to write apps for the android of things platform.
* __dividiParoli__
  * __DividiApp__: money dividing application, showing the use of distributed CRDTs integrated into rescala
  * __ParoliChatApp__: console application demoing distributed CRDTs
