package leo.modules

import leo.datastructures.impl.Signature
import leo.datastructures.{Clause, Role}
import leo.datastructures.tptp.Commons
import leo.modules.parsers.{TPTP, InputProcessing}
import leo.modules.output.{SZS_SyntaxError, SZS_InputError}

import scala.io.Source
import java.nio.file.{Path, Paths, Files}

/**
 * This facade object publishes some convenience methods
 * for parsing related utility including parsing of strings and problem files,
 *
 * @author Alexander Steen <a.steen@fu-berlin.de>
 * @since 29.04.2015
 */
object Parsing {

  // Functions that go from file/string to unprocessed internal TPTP-syntax
  // "String -> TPTP"
  /**
   * Reads the file located at `file` and parses it recursively using the `TPTP` parser.
   * Note that the return value is a sequence of [[Commons.AnnotatedFormula]] since
   * all includes are automatically parsed exhaustively.
   * If `file` is a relative path, it is assumed to be equivalent to the path
   * [[Utility.wd]]/file where `Utility.wd` is the working directory `user.dir`.
   *
   * @param file  The absolute or relative path to the problem file.
   * @param assumeRead Implicitly assume that the problem files in this parameter
   *                   have already been read. Hence, recursive parsing will skip this
   *                   includes.
   * @return The sequence of annotated TPTP formulae.
   */
  def readProblem(file: String, assumeRead: Set[Path] = Set()): Seq[Commons.AnnotatedFormula] = {
    val canonicalFile = canonicalPath(file)
    if (!assumeRead.contains(canonicalFile)) {
      val p = shallowReadProblem(file)
      val includes = p.getIncludes

      val pIncludes = includes.map{case (inc, _) => readProblem(canonicalFile.getParent.resolve(inc).toString,assumeRead + canonicalFile)}
      pIncludes.flatten ++ p.getFormulae
    } else {
      Seq()
    }
  }

  /**
   * Parses the single TPTP syntax formula given by `formula` into internal
   * tptp syntax representation.
   *
   * @param formula The formula to be parsed
   * @return The input formula in internal TPTP syntax representation
   */
  def readFormula(formula: String): Commons.AnnotatedFormula = {
    val p = TPTP.parseFormula(formula)
    if (p.isLeft) {
      throw new SZSException(SZS_SyntaxError, s"Parse error in formula $formula", p.left.get)
    } else {
      p.right.get
    }
  }

  /**
   * Reads the file located at `file`  and shallowly parses it using the `TPTP` parser.
   * Note that include statements are NOT recursively parsed but returned in internal TPTP
   * syntax instead. For recursive parsing of include statements, use [[readProblem()]].
   * If `file` is a relative path, it is assumed to be equivalent to the path
   * [[Utility.wd]]/file where `Utility.wd` is the working directory `user.dir`.
   *
   * @param file The absolute or relative path to the problem file.
   * @return The TPTP problem file in internal [[Commons.TPTPInput]] representation.
   */
  def shallowReadProblem(file: String): Commons.TPTPInput = {
    val p = TPTP.parseFile(read0(canonicalPath(file)))
    if (p.isLeft) {
      throw new SZSException(SZS_SyntaxError, s"Parse error in file $file", p.left.get)
    } else {
      p.right.get
    }
  }

  // Functions that go from internal TPTP syntax to processed internal representation (Term)
  // "TPTP -> Term"
  type FormulaId = String

  /**
   * Convert the problem given by the formulae in `problem` to internal term representation.
   * SIDE-EFFECTS: Type declarations and definitions within `problem` are added to the signature.
   *
   * Note that the parameter type is `Commons.AnnotatedFormula`, hence
   * all include statements need to be read externally before calling this function.
   *
   * @param problem The input problem in internal TPTP representation.
   * @return A triple `(Id, Clause, Role)` for each `AnnotatedFormula` within
   *         `problem`, such that `Id` is the identifier of the formula, `Clause` is the respective
   *         singleton clause in internal representation, and `Role` is the role of the formula as defined
   *         by the TPTP input.
   *         Note that formulae with role `definition` or `type` are returned as triples
   *         `(Id, Clause($true), Role)` with their respective identifier and role.
   */
  def processProblem(problem: Seq[Commons.AnnotatedFormula]): Seq[(FormulaId, Clause, Role)] = {
    InputProcessing.processAll(Signature.get)(problem)
  }
  /**
   * Convert the `formula` to internal term representation.
   * SIDE-EFFECTS: If `formula` is either a type declaration or a definition,
   * the respective declarations are added to the signature.
   *
   * @param formula The input formula in internal TPTP representation.
   * @return A triple `(Id, Term, Role)`,
   *         such that `Id` is the identifier of the formula, `Clause` is the respective
   *         singleton clause in internal representation, and `Role` is the role of the formula as defined
   *         by the TPTP input.
   *         Note that a formula with role `definition` or `type` is returned as a triple
   *         `(Id, Clause($true), Role)` with its respective identifier and role.
   */
  def processFormula(formula: Commons.AnnotatedFormula): (FormulaId, Clause, Role) = {
    InputProcessing.process(Signature.get)(formula)
  }


  // Functions that go from file/string to processed internal representation (Term)
  // "String -> Term"

  /**
   * Reads and recursively parses the file located at `file` and converts its
   * formulae to internal term presentation.
   * Note that the return value is a sequence of `(FormulaId, Term, Role)` since
   * all includes are automatically parsed and converted exhaustively.
   * If `file` is a relative path, it is assumed to be equivalent to the path
   * [[Utility.wd]]/file where `Utility.wd` is the working directory `user.dir`.
   *
   * SIDE-EFFECTS: Type declarations and definitions within `problem` are added to the signature.
   *
   * @param file  The absolute or relative path to the problem file.
   * @param assumeProcessed Implicitly assume that the problem files in this parameter
   *                   have already been read and processed.
   *                   Hence, recursive parsing will skip this includes.
   * @return A triple `(Id, Term, Role)` for each formula within
   *         the problem, such that `Id` is the identifier of the formula, `Clause` is the respective
   *         singleton clause in internal representation, and `Role` is the role of the formula as defined
   *         by the TPTP input.
   *         Note that formulae with role `definition` or `type` are returned as triples
   *         `(Id, Clause($true), Role)` with their respective identifier and role.
   */
  def parseProblem(file: String, assumeProcessed: Set[Path] = Set()): Seq[(FormulaId, Clause, Role)] = {
    processProblem(readProblem(file,assumeProcessed))
  }

  /**
   * Reads and parses `formula` and converts it to internal term representation.
   *
   * SIDE-EFFECTS: Type declarations and definitions within `problem` are added to the signature.
   *
   * @param formula The formula to be parsed and converted.
   * @return A triple `(Id, Term, Role)`,
   *         such that `Id` is the identifier of the formula, `Clause` is the respective
   *         singleton clause in internal representation, and `Role` is the role of the formula as defined
   *         by the TPTP input.
   *         Note that a formula with role `definition` or `type` is returned as a triple
   *         `(Id, Clause($true), Role)` with its respective identifier and role.
   */
  def parseFormula(formula: String): (FormulaId, Clause, Role) = {
    processFormula(readFormula(formula))
  }


  /** Converts the input path to a canonical path representation */
  def canonicalPath(path: String): Path = Paths.get(path).toAbsolutePath.normalize()


  private def read0(absolutePath: Path): String = {
    if (!Files.exists(absolutePath)) { // It either does not exist or we cant access it
      throw new SZSException(SZS_InputError, s"The file ${absolutePath.toString} does not exist.")
    } else {
        val s = Source.fromFile(absolutePath.toFile)
        val res = s.getLines() mkString "\n"
        s.close()
        res
    }
  }

}
