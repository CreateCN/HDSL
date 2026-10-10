/// HDSL's own layer: what the launcher is for, as opposed to the interface it is drawn with.
///
/// The packages below are arranged by subject, and the arrangement is a rule rather than a habit:
/// nothing here may use JavaFX or the transplanted interface packages, which is what lets the command line and
/// the tests run without a toolkit and keeps the transplanted interface layer on one side of the
/// line.  [PackageRulesTest] holds that line, and the allowed dependencies between these packages.
///
/// Each package's own file says what it is about; the pages that present them live in the
/// interface tree, under its `dsh` package.
package com.mcxcc303.hdsl;