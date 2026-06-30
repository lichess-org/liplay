/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.routes.compiler

import java.io.File

import snapshot4s.generated.snapshotConfig
import snapshot4s.munit.SnapshotAssertions

import play.routes.compiler.RoutesCompiler.RoutesCompilerTask

class RoutesSnapshotTest extends munit.FunSuite with SnapshotAssertions:

  private val fixtureName = "lila.routes"

  private def fixture: (String, File) =
    val file = new File(getClass.getClassLoader.getResource(fixtureName).toURI)
    val content = new String(java.nio.file.Files.readAllBytes(file.toPath))
    (content, file)

  private def rules: List[Rule] =
    val (content, file) = fixture
    RoutesFileParser
      .parseContent(content, file)
      .fold(errs => fail(s"failed to parse $fixtureName:\n${errs.mkString("\n")}"), identity)

  // The generated source embeds a non-deterministic `// @SOURCE:<path>` header line
  // (relativized against the cwd); drop it so the snapshot is path-independent.
  private def normalize(s: String): String =
    s.linesIterator.filterNot(_.trim.startsWith("// @SOURCE:")).mkString("\n")

  test("parsed route AST snapshot for lila routes"):
    val rendered = rules
      .collect { case r: Route => r }
      .map(r => s"${r.verb.value} ${r.path} -> ${r.call}")
      .mkString("\n")
    assertFileSnapshot(rendered, "routes/lila-ast.txt")

  test("generated router source snapshot for lila routes"):
    val (_, file) = fixture
    val task = RoutesCompilerTask(
      file,
      Seq.empty,
      forwardsRouter = true,
      reverseRouter = true,
      namespaceReverseRouter = false
    )
    val rendered = InjectedRoutesGenerator
      .generate(task, Some("lila"), rules)
      .map { case (name, src) => s"// ===== $name =====\n${normalize(src)}" }
      .mkString("\n\n")
    assertFileSnapshot(rendered, "routes/lila-generated.txt")
