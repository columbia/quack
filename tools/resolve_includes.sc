// TODOs
//  [ ] Implement cases where include arg contains:
//    [ ] Constant reference (try to resolve the value first)
//    [x] Magic constant
//      [ ] Warn on unknown magic constants
//    [ ] Variable (try to resolve its value if it was defined in the same scope)
//    [x] Function call
//      - For now we warn on unknown calls and use wildcard
//      - Could try to find a way to identify builtins
//  [ ] - Optimize when paths are fully resolved

import io.shiftleft.codepropertygraph.generated.nodes.{ Call => CallNode }
import scala.collection.mutable
import scala.sys.process._
import java.nio.file.{Path, Paths, Files}
import scala.Console.{RED, BLUE, YELLOW, WHITE, RESET}

import upickle.default.*

sealed trait LogLevel extends Ordered[LogLevel]
case object Debug extends LogLevel { def compare(that: LogLevel) = that match { case Debug => 0; case _ => -1 } }
case object Info extends LogLevel { def compare(that: LogLevel) = that match { case Debug | Info => 0; case _ => -1 } }
case object Warning extends LogLevel { def compare(that: LogLevel) = that match { case Debug | Info | Warning => 0; case _ => -1 } }
case object Error extends LogLevel { def compare(that: LogLevel) = 0 }

trait Logger {
  var logLevel: LogLevel = Info // Default log level

  def debug(message: => String): Unit = log(Debug, message)
  def info(message: => String): Unit = log(Info, message)
  def warning(message: => String): Unit = log(Warning, message)
  def error(message: => String): Unit = log(Error, message)

  private def log(level: LogLevel, message: => String): Unit = {
    if (level >= logLevel) {
      val color = if (level == Debug) WHITE
        else if (level == Info) BLUE
        else if (level == Warning) YELLOW
        else RED
      println(s"$color[${level.toString.toUpperCase}] $message ${RESET}")
    }
  }
}

class RegexPath(path: Path) {

  private var _path: Path = path.normalize();

  def this(path_str: String) = this(Paths.get(path_str))

  def +(that: RegexPath) : RegexPath =
    RegexPath(this._path.toString() + that._path.toString())

  def +(that: String) : RegexPath =
    RegexPath(this._path.toString() + that)

  def isFullyResolved() : Boolean = {
    return !(this._path.toString() contains UNKNOWN_NODE)
  }

  def getParent() : RegexPath = {
    return RegexPath(this._path.getParent)
  }

  def asPath() : Path = {
    return this._path
  }

  // Either starts with the given string or the UNKNOWN_NODE
  def startsWith(s: String) : Boolean = {
    return this._path.toString().startsWith(s) || this._path.toString().startsWith(UNKNOWN_NODE)
  }

  def startsWith(s: RegexPath) : Boolean = {
    return this._path.toString().startsWith(s.toString()) || this._path.toString().startsWith(UNKNOWN_NODE)
  }

  // Either ends with the given string or the UNKNOWN_NODE
  def endsWith(s: String) : Boolean = {
    return this._path.toString().endsWith(s) || this._path.toString().endsWith(UNKNOWN_NODE)
  }

  def endsWith(s: RegexPath) : Boolean = {
    return this._path.toString().endsWith(s.toString()) || this._path.toString().endsWith(UNKNOWN_NODE)
  }

  override def equals(other: Any) : Boolean = {
    // print("Checking equality between " + this + " and " + other)
    other match {
      case that: RegexPath => {
        // Check both directions, since either can contain a regular expression
        val equal = this._path.toString() == that._path.toString() ||
                 this._path.toString().r.matches(that._path.toString()) ||
                 that._path.toString().r.matches(this._path.toString())
        return equal
      }
      case _ => false
    }
  }

  override def hashCode() : Int = {
    this._path.hashCode()
  }

  override def toString = this._path.toString()

}

var project_root : RegexPath = RegexPath("")
var psr4_script : java.nio.file.Path = Paths.get("")
var warnings = mutable.ListBuffer[String]()
var errors = mutable.ListBuffer[String]()
var unhandled_autoloader_files = List[RegexPath]()
var logger : Logger = new Logger{ logLevel = Info };

val MAGIC_CONSTS : List[String] = List.apply("__DIR__", "__FILE__")
val BUILTINS : List[String] = List.apply("dirname")

// Can match any character in the regular expression
val UNKNOWN_NODE : String = ".*"

case class AvailClassesEntry(filename: String, line_numbers: List[Integer], avail_classes: List[String]) derives ReadWriter

// Check if we are dealing with a magic constant (Joern represents them as field accesses)
def is_magic_const(n: CallNode) : Boolean = {
  logger.debug("Checking if " + n.code + " is a magic const")
  return MAGIC_CONSTS.contains(n.code)
}

// Check if we are dealing with a PHP builtin
def is_builtin(n: CallNode) : Boolean = {
  logger.debug("Checking if " + n.code + " is a builtin")
  return BUILTINS.contains(n.methodFullName)
}

// Check if a fieldAccess is actually a constant and if so, try to resolve its
// value
def try_resolve_const(n: CallNode) : String =  {

  // TODO: Store constants so we don't have to resolve them again

  logger.debug("Trying to check if " + n.code + " is a const")

  // Get all calls to 'define'
  val define_calls = cpg.call.filter(_.methodFullName == "define").l
  // Check if any of them are match the string in the field access
  val definitions = define_calls.argument.argumentIndex(1).isLiteral.filter(_.code.replace("\"", "") == n.code).map(_.astParent.asInstanceOf[CallNode]).l
  if (definitions.length == 0) {
    logger.debug("No definitions found for " + n.code + ", probably not a const")
    return UNKNOWN_NODE
  } else if (definitions.length == 1) {
    logger.info("Found definition for constant " + n.code)
    val const_val = get_include_string(definitions.l(0).argument.argumentIndex(2).l(0).asInstanceOf[AstNode])
    return const_val
  } else {
    logger.warning("Multiple definitions found for " + n.code)
    return UNKNOWN_NODE
  }
}

// Returns the normalized absolute path by prepending the project root as a string
def join_paths(p1: RegexPath, p2: RegexPath) : RegexPath = {
    return RegexPath(p1.toString() + "/" + p2.toString())
}

// Check if the provided includes for a file contain a file with an unhandled autoloader
def includes_unhandled_autoloader(includes: mutable.ListBuffer[RegexPath]) : Boolean = {
  for (autoload_file <- unhandled_autoloader_files) {
    if (includes.filter(_ == autoload_file).l.length > 0) {
      return true
    }
  }
  return false
}

// Resolve a magic const
def resolve_magic_const(n: CallNode) : String = {
  logger.debug("Resolving magic const " + n.code)
  val filename = RegexPath(n.file.name.l(0))
  n.code match  {
    case "__DIR__" => {
      val file_path = join_paths(project_root, filename)
      val dir_path = file_path.getParent()
      return dir_path.toString()
    }
    case "__FILE__" => {
      return join_paths(project_root, filename).toString()
    }
  }
}

// Resolve a builtin
def resolve_builtin(n: CallNode) : String = {
  logger.debug("Resolving builtin " + n.methodFullName)
  n.methodFullName match  {
    case "dirname" => {
      val args = n.argument.l
      val path = get_include_string(args(0))
      if (!(RegexPath(path).isFullyResolved())) {
        return UNKNOWN_NODE
      }
      val levels = if (args.length > 1) args(1).asInstanceOf[Int] else 1
      var dir_path = Paths.get(path).getParent
      for (i <- 1 until levels) {
        dir_path = dir_path.getParent
      }
      return dir_path.toString()
    }
  }
}

// Resolves a string in an include directive
def get_include_string(n: AstNode) : String = {
  logger.debug("Resolving include string for " + n)
  if (n.isLiteral) {
    // Argument is a literal, remove quotes and return
    return n.asInstanceOf[Literal].code.replace("\"", "")
  } else if (n.isCall){
    val call = n.asInstanceOf[CallNode]
    call.methodFullName match {
      case "<operator>.concat" => {
        val arg1 = get_include_string(call.argument.argumentIndex(1).l(0).asInstanceOf[AstNode])
        val arg2 = get_include_string(call.argument.argumentIndex(2).l(0).asInstanceOf[AstNode])
        return Paths.get(arg1 + arg2).normalize().toString()
      }
      case "<operator>.fieldAccess" => {
        if (is_magic_const(call)) {
          return resolve_magic_const(call)
        } else {
          return try_resolve_const(call)
        }
      }
      case _ => {
        // Check if it's a builtin we don't support and log it, else treat it
        // as an unknown node
        // XXX: Can add support for framework (e.g., WordPress) functions apart
        // from builtins as well
        if (is_builtin(call)) {
          return resolve_builtin(call)
        } else {
          logger.warning("Unknown call " + call.methodFullName + " at " + call.file.name.l(0) + ":" + call.lineNumber.getOrElse(-1))
          return UNKNOWN_NODE
        }
      }
    }
  } else if (n.isIdentifier) {
    // XXX: Maybe try to resolve its value first if it's in the same scope
    logger.debug("Unknown node: " + n)
    return UNKNOWN_NODE
  } else {
    throw new Exception("Unknown type for include argument: " + n)
  }
}

// Returns a string representing the path of included file. In cases where the
// path can't fully be resolved, the part of the path that can't be resolved
// is replaced with a wildcard (*)
def get_include_path(including_file: RegexPath, n: AstNode) : RegexPath = {

  logger.debug("Resolving include path for " + n)

  val incl_string = get_include_string(n)

  // If it's an absolute path (or starts with an unknown node), return it,
  // else make it absolute first
  if (incl_string.startsWith("/") || incl_string.startsWith(UNKNOWN_NODE)) {
    logger.debug("Resolved include string may be absolute: " + incl_string)
    return RegexPath(incl_string)
  } else {
    logger.debug("Resolved include string is relative: " + incl_string)
    val incl_dir = including_file.getParent()
    return join_paths(incl_dir, RegexPath(incl_string))
  }
}

// Gets the autloaded files for a Composer-generated, PSR-4 compliant
// autoloader, as described in https://www.php-fig.org/psr/psr-4/
def get_composer_autoloaded_files(composer_psr4_mappings_path: RegexPath,
  files_to_classes: mutable.Map[RegexPath, mutable.ListBuffer[String]])
  : mutable.ListBuffer[RegexPath] = {
    var autoloaded_files = mutable.ListBuffer[RegexPath]()

    // If there is not autoloader, return an empty set of files
    if (!Files.exists(composer_psr4_mappings_path.asPath())) {
      return autoloaded_files
    }

    // Load the namespace-to-path mappings
    val cmd = "php " + psr4_script.toString() + " " + composer_psr4_mappings_path.toString()
    val mappings = ujson.read(Process(cmd).!!)

    // Implement the autoloader as described in the spec. We iterate through
    // the class definitions for the project, and check if the developer has
    // provided a namespace mapping that can load the class. If we find one,
    // add the class to the classes that can be loaded with the autoloader
    for ((namespace, paths) <- mappings.obj) {
      for (path <- paths.arr) {
        for ((file, classes) <- files_to_classes) {
          for (class_fqn <- classes) {
            if (class_fqn.startsWith(namespace)) {
              val remaining_namespace = class_fqn.substring(namespace.length)
              val remaining_path = remaining_namespace.replace("\\", "/") + ".php"
              val class_file_path = RegexPath(path.str + "/" + remaining_path)
              if (class_file_path == file) {
                autoloaded_files += file
              }
            }
          }
        }
      }
    }

    return autoloaded_files
}

// Get files that filename includes
// We need to provide a list of all the project files here in order to resolve
// wildcards (includes.get would just return a string with a wildcard if we don't
// compare it to an actual list of files to force it to use its 'equals' method)
def get_included_files(filename: RegexPath,
  includes: mutable.Map[RegexPath, mutable.ListBuffer[RegexPath]],
  project_files: List[RegexPath]) :
  mutable.ListBuffer[RegexPath] = {
    val included = mutable.ListBuffer[RegexPath]()
    val to_match = includes.get(filename).getOrElse(mutable.ListBuffer[RegexPath]())
    for (file <- to_match) {
      included ++= project_files.filter(x => {x == file});
    }
    return included
}

// Get files that include filename
def get_files_that_include(filename: RegexPath,
  includes: mutable.Map[RegexPath, mutable.ListBuffer[RegexPath]]) :
  List[RegexPath] = {
    return includes.filter(_._2.contains(filename)).keys.l
}

// Add all the files including the target file to the list of files to add, and continue
// recursively until we get all the files in the dependency chain
def add_includes_backwards(filename: RegexPath,
    includes: mutable.Map[RegexPath, mutable.ListBuffer[RegexPath]],
    files_to_add: mutable.ListBuffer[RegexPath]) : Boolean = {

    var all_including_files = get_files_that_include(filename, includes)

    for (including <- all_including_files) {
        // Don't re-add to avoid infinite loops
        if (!files_to_add.contains(including)) {
            files_to_add += including
            add_includes_backwards(including, includes, files_to_add)
        }
    }

    true
}

// Creates the list of available classes at each deserialization call
def resolve_avail_classes(
  project_files: List[RegexPath],
  included_files: mutable.Map[RegexPath, mutable.ListBuffer[RegexPath]],
  files_to_classes: mutable.Map[RegexPath, mutable.ListBuffer[String]],
  ) : List[AvailClassesEntry] = {

    logger.debug("Resolving available classes")

    var unser_calls_all = cpg.call.name("unserialize") ++ cpg.call.name("maybe_unserialize") ++ cpg.call.name("deserialize") ++ cpg.call.name("dunserialize")
    // Group calls by filename
    // XXX: might have to change this to account for mid-file includes
    var unser_calls_grouped = unser_calls_all.groupBy(_.file.name.l(0))

    var avail_classes_entries = mutable.ListBuffer[AvailClassesEntry]()

    for ((filename, unser_calls) <- unser_calls_grouped) {

      val full_filename = join_paths(project_root, RegexPath(filename))
      // All the files that are on a dependency path passing from filename
      var files_to_add = mutable.ListBuffer[RegexPath]()
      files_to_add += full_filename

      logger.debug("Adding includes backwards for " + full_filename)
      add_includes_backwards(full_filename, included_files, files_to_add)

      var avail_classes = mutable.ListBuffer[String]()
      var checked_files = mutable.ListBuffer[RegexPath]()

      while (!files_to_add.isEmpty) {


        // Get all the files that match the included file name (might be more than
        // one cause filename might contain wildwards)
        val incl_filename = files_to_add.remove(0)

        logger.debug("Adding classes from " + incl_filename)

        val incl_classes = mutable.ListBuffer[String]()
        for ((filename, classes) <- files_to_classes) {
          if (filename == incl_filename) {
            incl_classes ++= classes
          }
        }

        avail_classes ++= incl_classes
        checked_files += incl_filename

        // Add all the files included by the current file to the list of files to add
        for (included <- get_included_files(incl_filename, included_files, project_files)) {
            if (!checked_files.contains(included) && !files_to_add.contains(included)) {
                logger.debug("Adding " + included + " to files to check")
                files_to_add += included
            }
        }
      }

      avail_classes_entries += AvailClassesEntry(full_filename.toString(), unser_calls.lineNumber.l, avail_classes.toList)

    }

    return avail_classes_entries.toList

}

@main def exec(cpgFile: String, outFile: String, psr4Script: String) = {

  importCpg(cpgFile)

  val outFileWarnings = outFile + ".warnings"
  val outFileErrors = outFile + ".errors"
  psr4_script = Paths.get(psr4Script)

  // Root directory of analyzed project
  project_root = RegexPath(cpg.metaData.l(0).root)
  // All include directives in the project
  val include_directives = (cpg.call.methodFullName("include") ++ cpg.call.methodFullName("include_once") ++ cpg.call.methodFullName("require") ++ cpg.call.methodFullName("require_once")).l

  val project_files = cpg.file.l.filter(_.name != "<unknown>").map(x => {join_paths(project_root, RegexPath(x.name))})

  // Check if the project uses Composer. If it does, figure out its dependency
  // directory (usually just vendor/)
  var vendor_dir = join_paths(project_root, RegexPath("vendor/"))
  val composer_json_path = join_paths(project_root, RegexPath("composer.json"))
  if (Files.exists(composer_json_path.asPath())) {
    val composer_contents = os.read(os.Path(composer_json_path.toString()))
    val data = ujson.read(composer_contents)
    if (data.obj.get("config").nonEmpty) {
      val config = data("config")
      if (config.obj.get("vendor-dir").nonEmpty) {
        vendor_dir = RegexPath(config("vendor-dir").str)
      }
    }
  }

  val composer_vendor_dir = join_paths(vendor_dir, RegexPath("composer"))
  val autoload_file_path = join_paths(vendor_dir, RegexPath("autoload.php"))
  val composer_psr4_mappings_path = join_paths(composer_vendor_dir, RegexPath("autoload_psr4.php"))

  // Map from filename to its included files
  var included_files_map = mutable.Map[RegexPath, mutable.ListBuffer[RegexPath]]()

  // Map from filename to the classes it defines
  var files_to_classes_map = mutable.Map[RegexPath, mutable.ListBuffer[String]]()
  // All classes declared in project
  // FIXME: make sure this is the right way to filter the classes
  val all_classes = cpg.typeDecl.filter(_.code.startsWith("class ")).filter(_.code != "class <global>").l

  // Check if there are any autoloaders registered other than the composer one
  val autoload_registrations = cpg.call.methodFullName("spl_autoload_register").l
  val non_composer_autoloaders = autoload_registrations.filter(x => {!join_paths(project_root, RegexPath(x.method.filename)).startsWith(composer_vendor_dir)}).l
  unhandled_autoloader_files = non_composer_autoloaders.map(x => {RegexPath(x.file.name.l(0))})

  if (non_composer_autoloaders.length > 0) {
    for (autoloader <- non_composer_autoloaders) {
      logger.error("Unhandled autoloader registered at " + autoloader.file.name.l(0))
    }
  }

  // Create the file to class map by matching each class definition with its containing filename
  for (file <- cpg.file) {
    val filename = file.name

    if (filename != "<unknown>") {
      val full_path = join_paths(project_root, RegexPath(filename))
      var contained_classes = all_classes.filter(_.filename == filename)
      files_to_classes_map.getOrElseUpdate(full_path, mutable.ListBuffer[String]()) ++= contained_classes.fullName.l
    }

  }

  for (include_directive <- include_directives) {

    // println("Include directive: " + include_directive)

    val including_filename = join_paths(project_root, RegexPath(include_directive.file.name.l(0)))
    // println("Including filename: " + including_filename)
    val line = include_directive.lineNumber.getOrElse(-1)

    logger.info("Analyzing include directive at " + including_filename + ":" + line)

    if (include_directive.argument.l.length > 1) {
      throw new Exception("More than one arguments in include directive: " + including_filename + ":" + line)
    }

    val included_arg = include_directive.argument.l(0)
    val included_path = get_include_path(including_filename, included_arg)

    logger.debug("Resolved include path for " + including_filename + ":" + line + ": " + included_path)

    if (included_path.endsWith(".php")) {
      included_files_map.getOrElseUpdate(including_filename, mutable.ListBuffer[RegexPath]()) += included_path
    }
  }

  // If the project has a Composer-generated autoloader, add the autoloaded
  // files in the results as well
  if (Files.exists(autoload_file_path.asPath())) {
    val composer_autoloaded_files = get_composer_autoloaded_files(composer_psr4_mappings_path, files_to_classes_map)
    // Check which files include the autoload.php file, and add the autoloaded classes
    // in their list of includes
    for ((filename, includes) <- included_files_map) {
      if (includes.contains(autoload_file_path) && (filename != autoload_file_path)) {
        logger.debug(filename.toString() + " includes composer autoloader")
        includes ++= composer_autoloaded_files
      }
      // If it contains an unhandled autoloader, we have to treat it as if it
      // includes all files
      // Note: this has to be done last, to make sure we resolved all other includes first
      if (includes_unhandled_autoloader(includes)){
        logger.debug(filename.toString() + " includes unhandled autoloader")
        included_files_map.update(filename, mutable.ListBuffer(files_to_classes_map.keys.l: _*))
      }
    }
  }

  files_to_classes_map.mkString("\n") #> (outFile + ".files_to_classes")
  included_files_map.mkString("\n") #> (outFile + ".included_files")

  val avail_classes = resolve_avail_classes(project_files, included_files_map, files_to_classes_map)
  val avail_classes_json: String = write(avail_classes)

  avail_classes_json #> outFile
  println(avail_classes_json)

  ("[" + warnings.mkString(",") + "]") #> outFileWarnings
  ("[" + errors.mkString(",") + "]") #> outFileErrors
  // println(warnings)
  if (errors.length > 0) {
    println("Analysis finished with the following errors: ")
    println(errors)
  } else {
    println("Analysis finished sucessfully")
  }
}
