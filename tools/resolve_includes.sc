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
import io.shiftleft.semanticcpg.language._
import scala.collection.mutable
import scala.sys.process._
import java.nio.file.{Path, Paths, Files}
import scala.Console.{RED, BLUE, YELLOW, WHITE, RESET}
import scala.util.matching.Regex

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

def writeFile(path: String, content: String): Unit = {
  try {
    val parentDir = Paths.get(path).getParent
    if (parentDir != null) Files.createDirectories(parentDir)
    Files.writeString(Paths.get(path), content)
    logger.info(s"Successfully wrote ${content.length} bytes to $path")
  } catch {
    case e: Exception =>
      logger.error(s"[!] ERROR: Failed to write to file $path.")
      logger.error(s"    Reason: ${e.getMessage}")
  }
}


var project_root : String = ""
var psr4_script : java.nio.file.Path = Paths.get("")
var warnings = mutable.ListBuffer[String]()
var errors = mutable.ListBuffer[String]()
var unhandled_autoloader_files = List[String]()
var logger : Logger = new Logger{ logLevel = Debug };

// Caches for performance
val includedFilesCache = mutable.Map[String, List[String]]()
val regexCache = mutable.Map[String, Regex]()

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
def join_paths(p1: String, p2: String) : String = {
    return Paths.get(p1, p2).normalize().toString()
}

// Check if the provided includes for a file contain a file with an unhandled autoloader
def includes_unhandled_autoloader(includes: mutable.ListBuffer[String]) : Boolean = {
  // Check if any concrete autoloader file path is matched by any of the include patterns.
  unhandled_autoloader_files.exists { autoload_file =>
    includes.exists { pattern =>
      val regex = regexCache.getOrElseUpdate(pattern, pattern.r)
      regex.matches(autoload_file)
    }
  }
}

// Resolve a magic const
def resolve_magic_const(n: CallNode) : String = {
  logger.debug("Resolving magic const " + n.code)
  val filename = n.file.name.l(0)
  n.code match  {
    case "__DIR__" => {
      val file_path = join_paths(project_root, filename)
      return Paths.get(file_path).getParent.toString
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
      if (path.contains(UNKNOWN_NODE)) {
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
        return Paths.get(arg1, arg2).normalize().toString()
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
// is replaced with a wildcard (.*)
def get_include_path(including_file: String, n: AstNode) : String = {

  logger.debug("Resolving include path for " + n)

  val incl_string = get_include_string(n)

  // If it's an absolute path (or starts with an unknown node), return it,
  // else make it absolute first
  if (incl_string.startsWith("/") || incl_string.startsWith(UNKNOWN_NODE)) {
    logger.debug("Resolved include string may be absolute: " + incl_string)
    return incl_string
  } else {
    logger.debug("Resolved include string is relative: " + incl_string)
    val incl_dir = Paths.get(including_file).getParent().toString()
    return join_paths(incl_dir, incl_string)
  }
}

// Gets the autloaded files for a Composer-generated, PSR-4 compliant
// autoloader, as described in https://www.php-fig.org/psr/psr-4/
def get_composer_autoloaded_files(composer_psr4_mappings_path: String,
  files_to_classes: mutable.Map[String, mutable.ListBuffer[String]])
  : List[String] = {
    var autoloaded_files = mutable.ListBuffer[String]()

    // If there is not autoloader, return an empty set of files
    if (!Files.exists(Paths.get(composer_psr4_mappings_path))) {
      return List()
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
              val class_file_path = Paths.get(path.str, remaining_path).normalize().toString()
              if (class_file_path == file) {
                autoloaded_files += file
              }
            }
          }
        }
      }
    }

    return autoloaded_files.distinct.toList
}

// Get files that filename includes
// We need to provide a list of all the project files here in order to resolve
// wildcards (includes.get would just return a string with a wildcard if we don't
// compare it to an actual list of files to force it to use its 'equals' method)
def get_included_files(filename: String,
  includes: mutable.Map[String, mutable.ListBuffer[String]],
  project_files: List[String]) :
  List[String] = {
    if (includedFilesCache.contains(filename)) {
      return includedFilesCache(filename)
    }

    val included = mutable.ListBuffer[String]()
    val patterns_to_match = includes.getOrElse(filename, mutable.ListBuffer[String]())
    for (pattern <- patterns_to_match) {
      val patternRegex = pattern.r
      included ++= project_files.filter(project_file =>
        patternRegex.matches(project_file) || project_file.r.matches(pattern)
      )
    }

    val result = included.distinct.toList
    includedFilesCache.put(filename, result)
    result
}

def get_files_that_include(filename: String,
  includes: mutable.Map[String, mutable.ListBuffer[String]]) :
  List[String] = {
    includes.filter { case (_, patterns) =>
      patterns.exists { p =>
        p.r.matches(filename) || filename.r.matches(p)
      }
    }.keys.toList
}

// Add all the files including the target file to the list of files to add, and continue
// recursively until we get all the files in the dependency chain
def add_includes_backwards(filename: String,
    includes: mutable.Map[String, mutable.ListBuffer[String]],
    files_to_add: mutable.Set[String]) : Boolean = { // Use Set for performance

    val all_including_files = get_files_that_include(filename, includes)

    for (including <- all_including_files) {
        // Don't re-add to avoid infinite loops. Set `add` returns false if item already exists.
        if (files_to_add.add(including)) {
            logger.info("Backwards add: " + including)
            add_includes_backwards(including, includes, files_to_add)
        }
    }
    true
}

// Creates the list of available classes at each deserialization call
def resolve_avail_classes(
  project_files: List[String],
  included_files: mutable.Map[String, mutable.ListBuffer[String]],
  files_to_classes: mutable.Map[String, mutable.ListBuffer[String]],
  focus_lines: String = ""
  ) : List[AvailClassesEntry] = {

    logger.info("Resolving available classes")

    var unser_calls_traversal = cpg.call.name("unserialize") ++ cpg.call.name("maybe_unserialize") ++ cpg.call.name("deserialize") ++ cpg.call.name("dunserialize")
    if (focus_lines != "") {
      val focus_entries = focus_lines.split(",")
      unser_calls_traversal = unser_calls_traversal.filter(x =>
          focus_entries.contains(x.method.filename + ":" + x.lineNumber.getOrElse(-1).toString))
    }
    // Group calls by filename. Materialize here as groupBy needs a collection.
    val unser_calls_grouped = unser_calls_traversal.l.groupBy(_.file.name.l(0))

    var avail_classes_entries = mutable.ListBuffer[AvailClassesEntry]()

    for ((filename, unser_calls) <- unser_calls_grouped) {

      val full_filename = join_paths(project_root, filename)
      // Use a Set for files_to_add for performance
      val files_to_add = mutable.Set[String](full_filename)

      logger.info("Adding includes backwards for " + full_filename)
      add_includes_backwards(full_filename, included_files, files_to_add)

      var avail_classes = mutable.Set[String]() // Use Set to handle duplicates efficiently
      var checked_files = mutable.Set[String]()
      val queue = mutable.Queue[String](files_to_add.toSeq: _*)

      while (queue.nonEmpty) {
        val incl_filename = queue.dequeue()

        if (checked_files.add(incl_filename)) {
            logger.info("Adding classes from " + incl_filename)
            avail_classes ++= files_to_classes.getOrElse(incl_filename, mutable.ListBuffer.empty)

            // Add all the files included by the current file to the list of files to add
            for (included <- get_included_files(incl_filename, included_files, project_files)) {
                if (!checked_files.contains(included) && !queue.contains(included)) {
                    logger.info("Adding " + included + " to files to check")
                    queue.enqueue(included)
                }
            }
        }
      }

      avail_classes_entries += AvailClassesEntry(full_filename, unser_calls.map(_.lineNumber.getOrElse(-1)), avail_classes.toList)
    }

    avail_classes_entries.toList
}

@main def exec(cpgFile: String, outFile: String, psr4Script: String, focus_lines: String = "") = {

  importCpg(cpgFile)

  val outFileWarnings = outFile + ".warnings"
  val outFileErrors = outFile + ".errors"
  psr4_script = Paths.get(psr4Script)

  // Root directory of analyzed project
  project_root = cpg.metaData.l.head.root
  // Keep queries as Traversals to materialize as late as possible
  val include_directives_traversal = (cpg.call.methodFullName("include") ++ cpg.call.methodFullName("include_once") ++ cpg.call.methodFullName("require") ++ cpg.call.methodFullName("require_once"))
  val all_classes_traversal = cpg.typeDecl.filter(_.code.startsWith("class ")).filter(_.code != "class <global>")

  val project_files = cpg.file.l.filter(_.name != "<unknown>").map(x => join_paths(project_root, x.name))

  // Check if the project uses Composer. If it does, figure out its dependency
  // directory (usually just vendor/)
  var vendor_dir = join_paths(project_root, "vendor/")
  val composer_json_path = join_paths(project_root, "composer.json")
  if (Files.exists(Paths.get(composer_json_path))) {
    val composer_contents = os.read(os.Path(composer_json_path))
    val data = ujson.read(composer_contents)
    if (data.obj.get("config").nonEmpty) {
      val config = data("config")
      if (config.obj.get("vendor-dir").nonEmpty) {
        vendor_dir = config("vendor-dir").str
      }
    }
  }

  val composer_vendor_dir = join_paths(vendor_dir, "composer")
  val autoload_file_path = join_paths(vendor_dir, "autoload.php")
  val composer_psr4_mappings_path = join_paths(composer_vendor_dir, "autoload_psr4.php")

  // Map from filename to its included files
  val included_files_map = mutable.Map[String, mutable.ListBuffer[String]]()

  // Map from filename to the classes it defines
  val files_to_classes_map = mutable.Map[String, mutable.ListBuffer[String]]()

  // Materialize all_classes here as we need to group them by filename
  val all_classes = all_classes_traversal.l

  // Check if there are any autoloaders registered other than the composer one
  val autoload_registrations = cpg.call.methodFullName("spl_autoload_register").l
  val non_composer_autoloaders = autoload_registrations.filter(x => !join_paths(project_root, x.method.filename).startsWith(composer_vendor_dir))
  unhandled_autoloader_files = non_composer_autoloaders.map(x => join_paths(project_root, x.file.name.l.head))

  if (non_composer_autoloaders.nonEmpty) {
    for (autoloader <- non_composer_autoloaders) {
      logger.error("Unhandled autoloader registered at " + autoloader.file.name.l.head)
    }
  }

  // Create the file to class map by matching each class definition with its containing filename
  for (file <- cpg.file.l) {
    val filename = file.name
    if (filename != "<unknown>") {
      val full_path = join_paths(project_root, filename)
      val contained_classes = all_classes.filter(_.filename == filename)
      if (contained_classes.nonEmpty) {
          files_to_classes_map.getOrElseUpdate(full_path, mutable.ListBuffer[String]()) ++= contained_classes.map(_.fullName)
      }
    }
  }

  // Iterate on the traversal directly
  for (include_directive <- include_directives_traversal) {
    val including_filename = join_paths(project_root, include_directive.file.name.l.head)
    val line = include_directive.lineNumber.getOrElse(-1)

    logger.info("Analyzing include directive at " + including_filename + ":" + line)

    if (include_directive.argument.size > 1) {
      throw new Exception("More than one arguments in include directive: " + including_filename + ":" + line)
    }

    val included_arg = include_directive.argument.head
    val included_path = get_include_path(including_filename, included_arg)

    logger.info("Resolved include path for " + including_filename + ":" + line + ": " + included_path)

    if (included_path.endsWith(".php")) {
      included_files_map.getOrElseUpdate(including_filename, mutable.ListBuffer[String]()) += included_path
    }
  }

  logger.info("Moving on")
  // If the project has a Composer-generated autoloader, add the autoloaded
  // files in the results as well
  if (Files.exists(Paths.get(autoload_file_path))) {
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
        included_files_map.update(filename, mutable.ListBuffer(files_to_classes_map.keys.toList: _*))
      }
    }
  }

  logger.info("Finalizing")

  writeFile(outFile + ".files_to_classes", files_to_classes_map.mkString("\n"))
  writeFile(outFile + ".included_files", included_files_map.mkString("\n"))

  val avail_classes = resolve_avail_classes(project_files, included_files_map, files_to_classes_map, focus_lines)
  val avail_classes_json: String = write(avail_classes)

  writeFile(outFile, avail_classes_json)
  println(avail_classes_json)

  writeFile(outFileWarnings, "[" + warnings.mkString(",") + "]")
  writeFile(outFileErrors, "[" + errors.mkString(",") + "]")

  // println(warnings)
  if (errors.nonEmpty) {
    println("Analysis finished with the following errors: ")
    println(errors)
  } else {
    println("Analysis finished sucessfully")
  }
}
