/*
 * This script performs the duck-typing-based type inference described in the paper
 * On a high-level it iterates through each deserialization call and collects uses
 * of the deserialized value, logging encountered evidence about the class type
 * along the way.
 *
 * NOTE: any FIXMEs in this script were not encountered during analysis of
 * the evaluated projects, and can be addressed if encountered in future proejcts
 *
 * Type matching for rules for unserialized values (corresponds to Table I):
 *  - Exact -- evidence for exact type
 *    - type -- the type we deduced
 *    - reason -- reason for the decision
 *      - FuncArg -- TypeOf(parameter of function or static method value was passed to)
 *      - MethodArg -- TypeOf(parameter of method value was passed to)
 *      - Return -- TypeOf(return value of encapsulating method/function)
 *      - Cast -- Type value was cast to
 *      - Ternary -- Type of other value in ternary operator
 *      - Compared -- compared to value of this type
 *      - StringOp -- was used in a string operator (e.g., concat)
 *      - Scalar -- part of a scalar operation
 *      - Arithmetic -- was used in an arithmetic operator
 *      - DynamicCall -- used in a dynamic call so we can't say anything about its
 *      type. When this is encountered, this value is just a placeholder meaning
 *      all types should be allowed
 *  - Duck -- evidence for duck typing
 *    - types -- the types we deduced, separated with '|'
 *    - reason -- reason for the decision
 *      - HasMethod -- has method that was called on unserialized object
 *      - HasField -- has field that was accessed on unserialized object
 *      - HasToString -- has __toString method and was used in a String operator
 *      - AssignedToField -- was assigned to a field (property) with known/deduced type
 *
 * Collected conditions types for unserialized values (for debugging purposes):
 *  - ArgToFuncIdx/ArgToMethodIdx -- Passed as argument Idx to function (or static method)/method Arg-To-Func/Method
 *    - callerFullName: full name of function/method value is being passed to
 *    - callerName: name of function/method value is being passed to
 *    - argIdx: argument index value is being passed to
 *  - Returns -- is returned
 *    - methodName: name of method that returns the value
 *  - Conditional -- part of a conditional (e.g., ternary)
 *    - argIdx: argument index of conditional value is being passed to (e.g., in ternary, 'true ? "a" : unserialize($x)', would be index 1)
 *  - AssignedToArrayIdx -- is assigned to an array index (e.g., $array[$idx] = unserialize())
 *    - array: code of array value was assigned to (e.g., for '$array[$idx] = unserialize()', array is '$array')
 *    - arrayIdx: code of array index value was assigned to (e.g., for '$array[$idx] = unserialize()', arrayIdx is '$idx')
 *  - AssignedToField -- is assigned to the field of an object
 *    - objectName: Name of object (variable) that has the field
 *    - fieldType: Property or variable
 *    - fieldName: Name of field (or variable)
 *  - CallsMethod -- an object and calls a method (e.g., $x = unserialize(); $x->hello())
 *    - methodFullName: full name of method being called
 *    - methodName: name of method being called
 *  - ArrayRef -- is referenced as an array (a[<int>|<str>]).
 *                 Actually this can also be a string access (str[int-offset])
 *    - arrayIdx: similar to AssignedToArrayIdx
 *  - Scalar -- part of a scalar operation
 *    - type: type of scalar
 *  - FieldAccess -- accessing a field/property (read or write) on a tainted var
 *    - fieldName: Name of field being accessed
 *    - varFieldName: Name of variable representing field being accessed
 *  - Iterated -- was used in an iterator
 *  - Comparison -- was used in a comparison
 *    - comparisonType: type of comparison (e.g., comparison with literal, comparison with call (i.e., return value), comparison with variable etc.)
 *    - comparedValue: type of literal in literal comparison, name of function in call comparison, var name in var (identifier) comparison
 *  - ArithmeticOp -- was used in an arithmetic operation
 *  - LogicalOp -- was used in an logical operation
 *    - type: operator name
 *  - InstanceOf -- was passed to instanceOf
 *    - class: class being checked against
 *  - ClassAlloc -- was used as a dynamic class name for a class allocation (new $var())
 *  - ByRef -- was passed by reference or assigned by reference
 */

import io.shiftleft.codepropertygraph.generated.nodes.{ Call => CallNode }
import scala.collection.mutable.ListBuffer
import scala.collection.immutable.ArraySeq
import scala.collection.mutable

import upickle.default.*

var maxDepth: Int = 3
var built_in_log = "/processed/builtins.txt"

case class UnserEntry(filename: String, lineNumber: Integer, conditions: Set[Map[String, String]]) derives ReadWriter

// Add new evidence
def createCondition(condType: String, extra: mutable.Map[String, String]=mutable.Map()): Map[String, String] = {
  return extra.addOne(("condType" -> condType)).toMap
}

// Returns the type of the node, or all the dynamic type hints separated with '|'
// Returns ANY if type is not known
def getNodeType(n: AstNode) : String = {

  // FIXME collect type if node is call
  if (n.isInstanceOf[CallNode] || n.isInstanceOf[Block]) {
    return "ANY"
  }

  val type_full_name = if (n.isIdentifier) n.asInstanceOf[Identifier].typeFullName
  else if (n.isInstanceOf[MethodReturn]) n.asInstanceOf[MethodReturn].typeFullName
  else if (n.isInstanceOf[MethodParameterIn]) n.asInstanceOf[MethodParameterIn].typeFullName
  else if (n.isInstanceOf[Literal]) n.asInstanceOf[Literal].typeFullName
  else if (n.isInstanceOf[Member]) n.asInstanceOf[Member].typeFullName
  else throw new Exception("Got unknown type of node for extracting type: " + n)

  val type_full_name_str = type_full_name.asInstanceOf[String]

  val dynamic_type_hints = if (n.isIdentifier) n.asInstanceOf[Identifier].dynamicTypeHintFullName
  else if (n.isInstanceOf[MethodReturn]) n.asInstanceOf[MethodReturn].dynamicTypeHintFullName
  else if (n.isInstanceOf[MethodParameterIn]) n.asInstanceOf[MethodParameterIn].dynamicTypeHintFullName
  else if (n.isInstanceOf[Literal]) n.asInstanceOf[Literal].dynamicTypeHintFullName
  else if (n.isInstanceOf[Member]) n.asInstanceOf[Member].dynamicTypeHintFullName
  else throw new Exception("Got unknown type of node for extracting type: " + n)

  val dynamic_type_hints_iter = dynamic_type_hints.asInstanceOf[ArraySeq[String]]

  if (type_full_name_str == "ANY") {
    if (dynamic_type_hints_iter.length != 0) {
      dynamic_type_hints_iter.l.mkString("|")
    } else {
      return "ANY"
    }
  } else {
    return type_full_name_str
  }
}

// Get the node that represents the assigned variable
def getAssignedVar(assignment: CallNode) : AstNode = {
  return assignment.argument.argumentIndex(1).l(0)
}

// Check if method is builtin
def isBuiltIn(method: Method) : Boolean = {
  val is_builtin = method.astParentFullName == "<global>"
  // if (is_builtin) {
  //   method.name #>> built_in_log
  // }
  return is_builtin
}

// Get the id of the scope of the given node in the CPG
def getScopeId(n: AstNode) : Long = {

  if (n._astIn.length == 0) {
    return -1
  }

  val parent = n.astParent

  return if (parent.isCall) parent.asInstanceOf[CallNode].method.id
  else if (parent.isReturn) parent.asInstanceOf[Return].method.id
  else if (parent.isMethod) parent.asInstanceOf[Method].id
  else if (parent.isTypeDecl) parent.asInstanceOf[TypeDecl].id
  else if (parent.isControlStructure || parent.isBlock) getScopeId(parent.astParent)
  else throw new Exception("Unknown node in scope id check: " + parent)

}

// Add the classes that have a __toString method to the evidence
def addHaveToString(conds: ListBuffer[Map[String, String]]) = {
    val have_to_string = cpg.method.name("__toString").typeDecl.name.mkString("|")
    conds += createCondition("Duck",
      mutable.Map("reason" -> "HasToString",
        "type" -> have_to_string))
}

// Follow all uses for the given parameter in the method/function
def collectParameterUses(conds: ListBuffer[Map[String, String]], analyzed: mutable.Set[Long],
  parameter: MethodParameterIn, depth: Int, warnings: ListBuffer[String]) : Boolean = {

  // Get the refs of the parameter in the method
  val refs = cpg.graph.edges.filter(_.isInstanceOf[Ref]).l
  val parameter_refs = refs.filter(e => {
    (e.inNode.isInstanceOf[AstNode]) &&
    (e.outNode.isInstanceOf[AstNode]) &&
    (e.inNode.asInstanceOf[AstNode].id == parameter.id)
  })

  // Get the uses of the parameter
  val parameter_uses = parameter_refs.map(n => n.outNode.asInstanceOf[AstNode])
    .filter(n => getScopeId(n) == parameter.method.id)

  // Iterate and collect evidence
  for (use <- parameter_uses) {
    extractConditions(conds, use, analyzed, depth, warnings)
  }

  return true

}

// Collect uses of the parameter in a method
def collectParameterUsesFromMethod(conds: ListBuffer[Map[String, String]], analyzed: mutable.Set[Long],
  method: Method, nargs: Int, argIdx: Int, depth: Int, warnings: ListBuffer[String]) : Boolean = {

  // We reached max depth, stop here
  if (depth == maxDepth) {
    val warning = "Reached max depth in method " + method.fullName + ", stopping recursion"
    warnings += warning
    return false
  }

  // Heuristic for making sure it's not a function (method full names are objectName->methodName)
  if (method.name == method.fullName) {
    return false
  }

  println("Following parameter use in method " + method.fullName)

  // Get the parameter
  val parameter = method.parameter.index(argIdx).l(0)

  collectParameterUses(conds, analyzed, parameter, depth, warnings)
}


// Collect uses of the parameter in a function
def collectParameterUsesFromFunc(conds: ListBuffer[Map[String, String]], analyzed: mutable.Set[Long],
  method: Method, nargs: Int, argIdx: Int, depth: Int, warnings: ListBuffer[String]) : Boolean = {

  // We reached max depth, stop here
  if (depth == maxDepth) {
    val warning = "Reached max depth in function " + method.fullName + ", stopping recursion"
    warnings += warning
    return false
  }

  // Heuristic for making sure it's not a method (method has parameter at 0 ($this))
  if (method.parameter.index(0).length != 0) {
    return false
  }

  println("Following parameter use in function " + method.fullName)

  // Get the parameter (+1 cause 0 is $this, doesn't exist in functions)
  val parameter = method.parameter.index(argIdx + 1).l(0)

  collectParameterUses(conds, analyzed, parameter, depth, warnings)

}

// Check if the inferred type provides any evidence for what classes should be
// allowed
def helpsWithTyping(type_str: String) : Boolean = {
  // :(
  // https://stackoverflow.com/questions/5522572/how-to-split-a-string-by-a-string-in-scala
  val types = type_str.split("\\|")
  // These don't give us any real type evidence
  val types_filtered = types.filter(x => (x != "ANY" && x != "array" && x != "null" && !(x contains "->")))
  return (types_filtered.length > 0)
}

// Examine uses of the given class field in order to try to infer its type
def collectFieldUses(conds: ListBuffer[Map[String, String]], analyzed: mutable.Set[Long],
  the_class: TypeDecl, member: Member, depth: Int, warnings: ListBuffer[String]) : Boolean = {

    println("Collecting field uses for field '" + member.name + "' of class '" + the_class.name + "'")

    // Get all field identifiers that are part of a field access operation (should
    // always be but just sanity check), then get their respective objects, and
    // filter only the ones that are of type 'the_class'
    val field_uses = cpg.all.filter(_.isInstanceOf[FieldIdentifier])
      .map(_.asInstanceOf[FieldIdentifier]).canonicalName(member.name).astParent
      .filter(x => (x.isInstanceOf[CallNode] && x.asInstanceOf[CallNode].name == "<operator>.fieldAccess"))
      .map(_.asInstanceOf[CallNode]).map(_.argument.l(0))
      .filter(x => (x.isIdentifier && x.asInstanceOf[Identifier].typeFullName == the_class.name)).l

    for (use <- field_uses) {
      extractConditions(conds, use.astParent, analyzed, depth, warnings)
    }

    return true
}

// Try to infer the type of an array slice
def tryInferSliceType(index_access: CallNode) : Set[String] = {

  val arg0 = index_access.argument.l(0)
  val arg1 = index_access.argument.l(1)

  if (arg0.isIdentifier && arg1.isLiteral) {

    val objCode = arg0.asInstanceOf[Identifier].code
    val indexName = arg1.asInstanceOf[Literal].code

    // Locations where array slice is assigned to.
    val indexAssignments = cpg.call.name("<operator>.indexAccess")
      .filter(_.argument(1).code == objCode)
      .filter(_.argument(2).code == indexName)
      .inAssignment
      .filter(_.target.code == s"${objCode}[${indexName}]")
      .source.l

    // Functions that initialize the array slice.
    // TODO: Handle general expressions?
    val initializers = indexAssignments.collect {
      case m: CallNode => m.methodFullName
    }

    // Method returns
    val returnNodes = initializers.flatMap {
      case name: String => cpg.method.fullName(name).methodReturn.l
    }

    // Set of types returned by the initialization functions.
    val types = returnNodes.flatMap(
      m => (m.typeFullName +: m.dynamicTypeHintFullName)
        .filterNot(_ == "self")
    ).toSet

    return types
  } else {
    return Set("ANY")
  }
}

// Collect evidence from the given assigned deserialized variable
def followAssignedVar(conds: ListBuffer[Map[String, String]], assigned_var: AstNode,
  analyzed: mutable.Set[Long], depth: Int, warnings: ListBuffer[String]) : Boolean = {

  println("Following assignment to: " + assigned_var)

  // First check if the assigned variable is actually an index of an array
  if (assigned_var.isCall) {
    val call = assigned_var.asInstanceOf[CallNode]

    if (call.methodFullName == "list") {
      // Assigned to a list of variables, analyze all
      // https://www.php.net/manual/en/function.list.php
      for (arg <- call.argument) {
        val array_index = arg.argumentIndex - 1

        conds += createCondition("ArrayRef",
            mutable.Map("arrayIdx" -> array_index.toString))

        // TODO: should we really follow each element?
        followAssignedVar(conds, arg, analyzed, depth, warnings)
      }
      return false
    } else if (call.methodFullName == "<operator>.doubleArrow") {
      // Get $value from $key => $value
      return followAssignedVar(conds, call.argument.argumentIndex(2).l(0), analyzed, depth, warnings)
    } else if (call.methodFullName == "<operator>.fieldAccess") {
      // Assigned to a field
      val field_access = assigned_var.asInstanceOf[CallNode]
      val fobject = field_access.argument.argumentIndex(1).l(0)

      val fobj_type = getNodeType(fobject)
      if (!fobject.isIdentifier) {
        val warning = "Can't follow field access on non-identifier: " + fobject
        warnings += warning
        return false;
      }
      val object_name = fobject.asInstanceOf[Identifier].name
      val field = field_access.argument.argumentIndex(2).l(0)

      if (field.isFieldIdentifier) {

        val field_ident = field.asInstanceOf[FieldIdentifier]
        conds += createCondition("AssignedToField",
          mutable.Map(
            "objectName" -> object_name,
            "fieldName" -> field_ident.canonicalName,
            "fieldType" -> "property")
          )

        // Try to check if we know what the type of this property is before tainting it
        val field_members_with_name = cpg.member.name(field_ident.canonicalName).l
        if (fobj_type != "ANY" && !(fobj_type contains "|")) {
          // Use 'fullName' here to match namespaces as well
          val classes_with_field = field_members_with_name.typeDecl.filter(_.fullName == fobj_type).l
          if (classes_with_field.length == 1)  {
            val the_class = classes_with_field.l(0)
            val member = the_class.member.name(field_ident.canonicalName).l(0)
            val member_type = getNodeType(member)
            if (helpsWithTyping(member_type)) {

              conds += createCondition("Duck",
                mutable.Map("reason" -> "AssignedToField",
                  "type" -> member_type, "field" -> member.name))

              if (member_type contains "string") {
                addHaveToString(conds)
              }
              // No need to follow it since we deduced the type
              return false
            } else {
              return collectFieldUses(conds, analyzed, the_class, member, depth, warnings)
            }
          }
        }

      } else if (field.isIdentifier) {
        conds += createCondition("AssignedToField",
          mutable.Map(
            "objectName" -> object_name,
            "fieldName" -> field.asInstanceOf[Identifier].name,
            "fieldType" -> "variable")
          )
      } else {
        throw new Exception("Unknown type for field access!")
      }
      return followAssignedVar(conds, fobject, analyzed, depth, warnings)
    } else if (call.methodFullName == "<operator>.indexAccess") {
      // Assigned to array index
      val index_access = assigned_var.asInstanceOf[CallNode]
      val array = index_access.argument.argumentIndex(1).l(0)
      // FIXME try to figure out if this is a literal
      conds += createCondition("AssignedToArrayIdx",
        mutable.Map("arrayIdx" -> index_access.argument.argumentIndex(2).l(0).code,
          "array" -> array.code))
      return followAssignedVar(conds, array, analyzed, depth, warnings)
    } else {
      throw new Exception("Unknown call in assignment analysis: " + call.methodFullName + " (" + call.code + ")" + call.method.filename)
    }

  }

  // Get the all the ref edges. Make sure to cast them to lists such that
  // functions such as `filter` work as expected
  val refs = cpg.graph.edges.filter(_.isInstanceOf[Ref]).l

  // Get the node that represents the local variable
  val assigned_var_refs = refs.filter(e => {
    (e.inNode.isInstanceOf[AstNode]) &&
    (e.outNode.isInstanceOf[AstNode]) &&
    (e.outNode.asInstanceOf[AstNode].id == assigned_var.id)
  })
  if (assigned_var_refs.length != 1) {
    throw new Exception("Expected 1 assigned_var ref, found " + assigned_var_refs.length)
  }
  val local_var_node = assigned_var_refs(0).inNode

  // Get all the other uses of the assigned-to variable and analyze them
  val local_var_refs = refs.filter(e => {
    (e.inNode.isInstanceOf[AstNode]) &&
    (e.outNode.isInstanceOf[AstNode]) &&
    (e.inNode.asInstanceOf[AstNode].id == local_var_node.id)
  }).l

  // Remove the use we started and the ones in different scope and analyze the rest
  // We check scope by checking if the id of the method containing the variable matches
  // with the rest of the uses
  val var_uses = local_var_refs.map(n => n.outNode.asInstanceOf[AstNode])
    .filter(_.id != assigned_var.id)
    .filter(n => getScopeId(n) == assigned_var.astParent.asInstanceOf[CallNode].method.id)
    .filter(n => {n.lineNumber.getOrElse(-1).asInstanceOf[Int] >= assigned_var.lineNumber.getOrElse(-1).asInstanceOf[Int]})
    .l

  for (use <- var_uses) {
    extractConditions(conds, use, analyzed, depth, warnings)
  }

  true
}

def getArgIdx(call: CallNode, n: AstNode) : Int = {
  return call.argument.indexOf(n)
}

// Get the variable representing the value in an iterator
def extractIteratorVariable(iterator_parent: AstNode) : AstNode = {
  if (!iterator_parent.isInstanceOf[CallNode]) {
    throw new Exception("Iterator parent is not call node!")
  }

  // The parent of the Iterator.current call should either be the iterator value
  // assignment or the addressOf call if the for-each value is passed by reference
  val val_assignment = iterator_parent.asInstanceOf[CallNode].methodFullName match
  {
    case "<operator>.assignment" => iterator_parent /* $val = $iter->current() */
    case "<operator>.addressOf" => iterator_parent.astParent /* $val = &$iter->current() */
    case _ => throw new Exception("Unexpected iterator parent method: "
      + iterator_parent.asInstanceOf[CallNode].methodFullName)
  }

  var loop_value = val_assignment.asInstanceOf[CallNode].argument.argumentIndex(1).l(0)
  // Is ($key => $value), just get $value
  if (loop_value.isInstanceOf[CallNode] &&
    loop_value.asInstanceOf[CallNode].methodFullName == "<operator>.doubleArrow") {
      loop_value = loop_value.asInstanceOf[CallNode].argument.argumentIndex(2).l(0)
  }

    return loop_value
}

// The main function that applies the typing rules on a given deserialized node
// and collects type information
def extractConditions(conds: ListBuffer[Map[String, String]], n: AstNode,
  analyzed: mutable.Set[Long], depth: Int, warnings: ListBuffer[String]) : Boolean = {

  // We reached max depth, stop here
  if (depth == maxDepth) {
    println("Reached max depth, stopping recursion")
    return false
  }

  if (analyzed.contains(n.id)) {
    // println("Node " + n.id + " already analyzed, skipping")
    return false
  } else {
    analyzed += n.id
  }

  println()
  println("Extracting conditions for node: " + n)

  // Cast the node to its specific class so that we can use certain properties
  // that don't exist in AstNode
  val n_cast = if (n.isCall) n.asInstanceOf[CallNode]
  else if (n.isIdentifier) n.asInstanceOf[Identifier]
  else if (n.isBlock) n.asInstanceOf[Block]
  else if (n.isReturn) n.asInstanceOf[Return]
  else throw new Exception("Can't figure out what to cast n to")

  if (n._astIn.length == 0) {
    // No astParent to analyze
    println("No AST parent for " + n)
    return false
  }

  var parent = n.astParent
  if (parent.isBlock || n.isBlock
    || parent.isControlStructure || n.isControlStructure) {
      return false
    } else if (parent.isCall) {

      val call = parent.asInstanceOf[CallNode]

      call.methodFullName match {
        // Ignore error suppress prefixes
        case "<operator>.errorSuppress" => {
          println("Ignoring errorSuppress node")
          return extractConditions(conds, parent, analyzed, depth, warnings)
        }
        case "<operator>.doubleArrow" => {
          // Part of a foreach, we should have processed it already
          return extractConditions(conds, parent, analyzed, depth, warnings)
        }
        case "Iterator.next" => {
          // Part of an iterator, we are extracting the conditions from
          // Iterator.current
          return extractConditions(conds, parent, analyzed, depth, warnings)
        }
        // Value is an array and is being indexed
        case "<operator>.indexAccess" => {
          val index_access = parent.asInstanceOf[CallNode]
          conds += createCondition("ArrayRef",
            mutable.Map("arrayIdx" -> index_access.argument.argumentIndex(2).l(0).code))
        }
        // Value was passed by reference (or assigned as a reference)
        case "<operator>.addressOf" => {
          conds += createCondition("ByRef")
        }
        // Part of a loop (e.g., foreach)
        case "Iterator.current" => {
          conds += createCondition("Iterated")
          // Because of the way joern represents foreach loops, we actually need
          // to process the parent assignment call to get the variable that
          // represents the value being iterated
          val loop_assign_call = parent.astParent
          val loop_value = extractIteratorVariable(loop_assign_call)
          // We essentially treat the iterated value as a new variable being reassigned
          // in the loop body
          followAssignedVar(conds, loop_value, analyzed, depth, warnings)
        }
        // Field of value is being accessed
        case "<operator>.fieldAccess" => {
          // Make sure the object we are analyzing is the one having the field access
          val field_access = parent.asInstanceOf[CallNode]
          // +1 cause weird joern indexing
          val arg_idx = getArgIdx(field_access, n_cast) + 1
          // The object is used as the field name
          if (arg_idx == 2) {
            // FIXME does this mean it should be string? Probably yes, but make sure
            conds += createCondition("FieldName")
          } else {
            if (arg_idx != 1) {
              throw new Exception("Field access but argument isn't the one being accessed or the field name! (" + field_access.code + ")")
            }
            val field_identifier = field_access.argument.argumentIndex(2).l(0)
            if (field_identifier.isFieldIdentifier) {
              // FIXME maybe filter based on field type if we have it
              // Get the classes that have a field with this name
              val field_name = field_identifier.asInstanceOf[FieldIdentifier].canonicalName
              val field_members = cpg.member.name(field_name)
              val classes_with_field = field_members.typeDecl.name.mkString("|")

              conds += createCondition("Duck",
                mutable.Map("reason" -> "HasField",
                  "type" -> classes_with_field, "field" -> field_name))

              conds += createCondition("FieldAccess",
                mutable.Map(
                  "fieldName" -> field_name)
                )
            } else if (field_identifier.isIdentifier) {
              conds += createCondition("FieldAccess",
                mutable.Map(
                  "varFieldName" -> field_identifier.asInstanceOf[Identifier].name)
                )
            } else if (field_identifier.isExpression) {
              // Dynamic field access, so we can't say anything about the type
              conds += createCondition("Exact",
                mutable.Map("reason" -> "DynamicCall",
                  "type" -> "ANY", "call" -> call.name)
                )
              return false // No need to collect anything else about this
            }
            else {
              throw new Exception("Unknown type for field identifier: " + field_identifier.code)
            }
          }
        }
        // Value is being cast to a type
        case "<operator>.cast" => {
          val casted_type = call.argument(1).asInstanceOf[TypeRef].typeFullName
          conds += createCondition("Exact",
            mutable.Map(
              "type" -> casted_type,
              "reason" -> "Cast"))

              if (casted_type == "string") {
                addHaveToString(conds)
              }

          // No need to collect anything else about this since we got the exact
          // casted type, unless it was an array (doesn't tell us anything)
          if (casted_type != "array") {
            return false
          }
        }
        // Used in comparison
        case comparison @ ("<operator>.identical" | "<operator>.notIdentical" |
          "<operator>.lessThan" | "<operator>.lessEqualsThan" |
          "<operator>.greaterThan" | "<operator>.greaterEqualsThan" |
          "<operator>.equals" | "<operator>.notEquals") => {
            //FIXME maybe add different type evidence for identical vs the rest
          val arg_idx = getArgIdx(call, n_cast)
          val other_idx = if (arg_idx == 0) 2 else 1
          val other_arg = call.argument.argumentIndex(other_idx).l(0)
          if (other_arg.isLiteral) {
            conds += createCondition("Comparison",
              mutable.Map("comparisonType" -> "literal",
                "comparedValue" -> other_arg.asInstanceOf[Literal].typeFullName))
          } else if (other_arg.isCall) {
            conds += createCondition("Comparison",
              mutable.Map("comparisonType" -> "call",
                "comparedValue" -> other_arg.asInstanceOf[CallNode].methodFullName))
          } else if (other_arg.isIdentifier) {
            // Add duck type evidence if we know the type of value we are being
            // compared to
            val other_arg_type = getNodeType(other_arg)
            conds += createCondition("Comparison",
              mutable.Map("comparisonType" -> "variable",
                "comparedValue" -> other_arg.asInstanceOf[Identifier].name))
          }
          else {
            throw new Exception("Comparison with unhandled value: " + other_arg)
          }
        }
        // Arithmetic operation
        case arithmetic @ ("<operator>.plus" | "<operator>.minus" |
          "<operator>.multiplication" | "<operator>.modulo" |
          "<operator>.division" | "<operator>.postIncrement" |
          "<operator>.preDecrement" | "<operator>.preIncrement" |
          "<operator>.postDecrement" | "<operator>.exponentiation") => {

          conds += createCondition("ArithmeticOp",
            mutable.Map("type" -> parent.asInstanceOf[CallNode].name))

          conds += createCondition("Exact",
            mutable.Map("reason" -> "Arithmetic", "type" -> "numeric"))

        }
        // Bitwise operation
        case bitwise @ ("<operator>.and" | "<operator>.or" |
          "<operator>.xor" | "<operator>.not" |
          "<operator>.shiftLeft" | "<operator>.arithmeticShiftRight") => {

          conds += createCondition("ArithmeticOp",
            mutable.Map("type" -> parent.asInstanceOf[CallNode].name))

          conds += createCondition("Exact",
            mutable.Map("reason" -> "Arithmetic", "type" -> "numeric"))
        }
        // Arithmetic assignment (e.g. +=, -=)
        case arithmetic_assignment @ ("<operator>.assignmentPlus" |
          "<operator>.assignmentMinus" | "<operator>.assignmentMultiplication" |
          "<operator>.assignmentDivision" | "<operators>.assignmentModulo" |
          "<operators>.assignmentExponentiation") => {

          conds += createCondition("ArithmeticOp",
            mutable.Map("type" -> parent.asInstanceOf[CallNode].name))

          conds += createCondition("Exact",
            mutable.Map("reason" -> "Arithmetic", "type" -> "numeric"))

          var assigned_var = getAssignedVar(parent.asInstanceOf[CallNode])
          // Being assigned with another variable
          if (assigned_var.id != n.id) {
            followAssignedVar(conds, assigned_var, analyzed, depth, warnings)
          }
        }
        // Bitwise assignment (e.g. &=, |=)
        case bitwise_assignment @ ("<operators>.assignmentAnd" |
          "<operators>.assignmentOr" | "<operators>.assignmentXor" |
          "<operators>.assignmentShiftLeft" | "<operators>.assignmentArithmeticShiftRight") => {

          conds += createCondition("ArithmeticOp",
            mutable.Map("type" -> parent.asInstanceOf[CallNode].name))

          conds += createCondition("Exact",
            mutable.Map("reason" -> "Arithmetic", "type" -> "numeric"))

          var assigned_var = getAssignedVar(parent.asInstanceOf[CallNode])
          // Being assigned with another variable
          if (assigned_var.id != n.id) {
            followAssignedVar(conds, assigned_var, analyzed, depth, warnings)
          }
        }
        // Logical operation
        case logical @ ("<operator>.logicalAnd" | "<operator>.logicalOr" |
          "<operator>.logicalNot") => {
          conds += createCondition("LogicalOp",
            mutable.Map("type" -> parent.asInstanceOf[CallNode].name))
        }
        // Adding to array stuff (need them here in order to taint array, funcs
        // that don't add elements to the array shouldn't be added here, e.g.,
        // array_map)
        case array_funcs @ ("array_push") => {
          val call = parent.asInstanceOf[CallNode]
          val arg_idx = getArgIdx(call, n_cast)
          // Value being pushed in the array
          if (arg_idx == 1)  {
            val array = call.argument.argumentIndex(1).l(0)
            followAssignedVar(conds, array, analyzed, depth, warnings)
          }
        }
        // Part of a conditional (e.g., ternary, elvis)
        case conditional @ ("<operator>.conditional" | "<operator>.elvis") => {
          val call = parent.asInstanceOf[CallNode]
          val arg_idx = getArgIdx(call, n_cast)

          // If analyzing the first argument in ternary (e.g. $a in $a ? $b : $c), we can't deduce anything
          if (!(call.methodFullName == "<operator>.conditional" && arg_idx == 0)) {
            // Calculate index of the other conditional argument, where getArgIdx returns
            // a 0-based index but call.argument.argumentIndex takes 1-based index.
            val other_idx = if (call.methodFullName == "<operator>.elvis") {
              // elvis 'collapses' first and second argument: $a ?: $c is the same as $a ? $a : $c
              if (arg_idx == 0) 2 else 1
            } else {
              if (arg_idx == 1) 3 else 2
            }

            val other_arg = call.argument.argumentIndex(other_idx).l(0)
            val other_arg_type = getNodeType(other_arg)
            if (other_arg_type != "ANY") {

              conds += createCondition("Exact",
                mutable.Map("reason" -> "Ternary", "type" -> other_arg_type))

                if (other_arg_type == "string") {
                  addHaveToString(conds)
                }
            }
          }

          conds += createCondition("Conditional",
            mutable.Map("argIdx" -> arg_idx.toString))
        }
        // Part of a string operation
        case string_op @ ("<operator>.concat" | "<operator>.assignmentConcat") => {

          addHaveToString(conds)

          conds += createCondition("Exact",
            mutable.Map("type" -> "string", "reason" -> "StringOp"))

          if (call.methodFullName == "<operator>.assignmentConcat") {
            var assigned_var = getAssignedVar(parent.asInstanceOf[CallNode])
            // Being concatenated to another variable
            if (assigned_var.id != n.id) {
              followAssignedVar(conds, assigned_var, analyzed, depth, warnings)
            }
          }
        }
        // Encapsulated scalar
        case "encaps" => {

          conds += createCondition("Scalar",
            mutable.Map("type" -> call.typeFullName))

          conds += createCondition("Exact",
            mutable.Map("type" -> call.typeFullName, "reason" -> "Scalar"))

          if (call.typeFullName == "string") {
            addHaveToString(conds)
          }
        }
        // Used in 'new' as a dynamic class name (new $var())
        case "<operator>.alloc" => {
          // FIXME does this mean it should be string?
          conds += createCondition("ClassAlloc")
        }
        // Assignments are special calls and need to be analyzed independently
        case "<operator>.assignment" => {
          /* Unserialized value is assigned to a variable, follow it to collect
           * more conditions */
          var assigned_var = getAssignedVar(parent.asInstanceOf[CallNode])
          if (assigned_var.id != n.id) {
            followAssignedVar(conds, assigned_var, analyzed, depth, warnings)
          } else {
            // Don't follow re-assignments
            println("Node " + n + " is being reassigned, ignoring")
            return false
          }
        }
        // Functions that take callbacks
        case callbacks @ ("array_map" | "call_user_func_array") => {
          val fcall = parent.asInstanceOf[CallNode]
          // FIXME for now we assume callback is the first argument
          val callback_name_node = fcall.argument.argumentIndex(1).l(0)
          val arg_idx = getArgIdx(fcall, n_cast)

          // Treat the callback as a function call if we know its name
          if (callback_name_node.isLiteral) {

            val callback_name_literal = callback_name_node.asInstanceOf[Literal]
            println("Callback name is literal: " + callback_name_literal)

            if (getNodeType(callback_name_literal) == "string") {
              val callback_name = callback_name_literal.code.replace("\"", "")
              val callback_func_l = cpg.method.name(callback_name).l

              if (callback_func_l.length > 0) {
                val callback_func = callback_func_l.l(0).asInstanceOf[Method]
                // Normally we would have to -1 the arg index, but we would have
                // add it again because joern indexing, so just keep what we got,
                // it's correct
                val callback_arg_l = callback_func.parameter.index(arg_idx).l
                if (callback_arg_l.length > 0) {
                  val callback_arg = callback_arg_l.l(0)
                  val arg_type = getNodeType(callback_arg)
                  if (helpsWithTyping(arg_type)) {

                    conds += createCondition("Exact", mutable.Map("reason" ->
                      "FuncArg", "type" -> arg_type, "function" ->
                      callback_func.name))

                    if (arg_type contains "string") {
                      addHaveToString(conds)
                    }
                  }
                }
              }
            }

          } else {
            // We can't resolve what the callback is, we need to allow everything
            conds += createCondition("Exact",
              mutable.Map("reason" -> "DynamicCall",
                "type" -> "ANY", "call" -> call.name))
          }

        }
        // Passed to instanceOf
        case "<operator>.instanceOf" => {
          val class_name = call.argument.argumentIndex(2).l(0).asInstanceOf[Identifier].name
          conds += createCondition("InstanceOf",
            mutable.Map("type" -> class_name))
        }
        // The node is used as an argument to a call, record the condition
        case _ => {
          if (call.methodFullName.startsWith("<operator>") || call.methodFullName.startsWith("<operators>")) {
            throw new Exception("Unknown operator: " + call.methodFullName + " (" + call.code + ")")
          }
          // The call the node is being passed to
          val fcall = parent.asInstanceOf[CallNode]
          val nargs = fcall.argument.l.length
          // Functions with this name
          var methods = cpg.method.name(fcall.name).l
          val arg_idx = getArgIdx(fcall, n_cast)

          if (fcall.dispatchType == "STATIC_DISPATCH") {
            // Regular function call
            conds += createCondition("ArgToFuncIdx",
              mutable.Map(
                "callerFullName" -> call.methodFullName,
                "callerName" -> call.name,
                "argIdx" -> arg_idx.toString))
            // Recursively collect evidence by following parameter use in the
            // called function (or static method)
            for (method <- methods) {
              if (method.parameter.l.length >= nargs) {
                val param_node = method.parameter.index(arg_idx + 1).l
                var param_type = "ANY"
                if (param_node.length > 0) {
                  val param = param_node.l(0)
                  param_type = getNodeType(param)
                }

                // Check if we know the parameter type before collecting evidence from within the function
                if (helpsWithTyping(param_type)) {
                  conds += createCondition("Exact",
                    mutable.Map("reason" -> "FuncArg", "type" -> param_type, "function" -> fcall.name))

                  if (param_type contains "string") {
                    addHaveToString(conds)
                  }

                } else if (!isBuiltIn(method)) {
                  collectParameterUsesFromFunc(conds, analyzed, method, nargs, arg_idx, depth + 1, warnings)
                }
              } else {
                println("More arguments than parameters in function " + method.name + " " + method.astParentFullName)
              }
            }
          } else if (fcall.dispatchType == "DYNAMIC_DISPATCH") {
            // Method call
            if (arg_idx == 0) {
              // The value is the object the method is called on
              conds += createCondition("CallsMethod",
                mutable.Map("methodName" -> fcall.name,
                  "methodFullName" -> fcall.methodFullName))
              // FIXME maybe filter based on number of arguments
              val methods = cpg.method.name(fcall.name).filter(_.astParentFullName != "<global>")
              val types = methods.typeDecl.name.mkString("|")

              conds += createCondition("Duck",
                mutable.Map("reason" -> "HasMethod",
                  "type" -> types, "method" -> fcall.name))

            } else {
              // Try to identify the object first and filter, else just try everything
              // that has a method with this name
              val fobject = fcall.argument(0)
              if (fobject.isIdentifier) {
                val fobj_type = fobject.asInstanceOf[Identifier].typeFullName
                if (fobj_type != "ANY") {
                  methods = methods.filter(x => {(x.typeDecl.l.length != 0) && (x.typeDecl.name.l(0) == fobj_type)})
                }
                if (fobj_type.startsWith("$") && fcall.name == "__construct") {
                  // Flows into a dynamic constructor which we can't follow,
                  // we have to allow all classes
                  conds += createCondition("Exact",
                    mutable.Map("reason" -> "DynamicCall",
                      "type" -> "ANY", "call" -> (fobj_type + "->__construct")))

                  return false
                }
              } else if (fobject.isCall) {
                // Check if it's a field with known type too
                val call = fobject.asInstanceOf[CallNode]
                if (call.methodFullName == "<operator>.fieldAccess") {
                  val fobject = call.argument.argumentIndex(1).l(0)
                  val fobj_type = getNodeType(fobject)

                  if (fobject.isIdentifier) {
                    val field = call.argument.argumentIndex(2).l(0)

                    if (field.isFieldIdentifier) {

                      val field_ident = field.asInstanceOf[FieldIdentifier]
                      val field_members_with_name = cpg.member.name(field_ident.canonicalName).l
                      if (fobj_type != "ANY" && !(fobj_type contains "|")) {
                        // Use 'fullName' here to match namespaces as well
                        val classes_with_field = field_members_with_name.typeDecl.filter(_.fullName == fobj_type).l
                        if (classes_with_field.length == 1)  {
                          val the_class = classes_with_field.l(0)
                          val member = the_class.member.name(field_ident.canonicalName).l(0)
                          val member_type = getNodeType(member)
                          if (member_type != "ANY") {
                            methods = methods.filter(x => {(x.typeDecl.l.length != 0) && (x.typeDecl.name.l(0) == member_type)})

                            // println("Filtered methods: "+ methods)
                          }
                        }
                      }
                    }
                  }
                }
                else if (call.methodFullName == "<operator>.indexAccess") {
                  val inferred_types = tryInferSliceType(call)
                  methods = methods.filter(x => {(x.typeDecl.l.length != 0) && (inferred_types.contains(x.typeDecl.name.l(0)))})
                }
              }
              // The value is the argument to the method call
              conds += createCondition("ArgToMethodIdx",
                mutable.Map(
                  "callerFullName" -> call.methodFullName,
                  "callerName" -> call.name,
                  "argIdx" -> (arg_idx - 1).toString))
              // Recursively collect evidence by following parameter use in the
              // called method
              for (method <- methods) {
                // println("Checking " + method.name + " nargs: " + nargs + " param length: " + method.parameter.l.length)
                // Only do this if this method has enough parameters
                if (method.parameter.l.length >= nargs) {
                  val param = method.parameter.index(arg_idx).l(0)
                  val param_type = getNodeType(param)

                  // Check if we know the parameter type before collecting evidence from within the method
                  if (helpsWithTyping(param_type)) {

                    conds += createCondition("Exact",
                      mutable.Map("reason" -> "MethodArg",
                        "type" -> param_type,
                        "method" -> fcall.name))

                    if (param_type contains "string") {
                      addHaveToString(conds)
                    }

                  } else if (!isBuiltIn(method)) {
                    // FIXME we should always know the types for built-ins, so this check
                    // might not be needed
                    // argument length - 1 to account for object
                    collectParameterUsesFromMethod(conds, analyzed, method, nargs, arg_idx, depth + 1, warnings)
                  }
                } else {
                  println("More arguments than parameters in method " + method.name + " " + method.typeDecl.name)
                }
              }
            }
          }
          return false
        }
      }
    } else if (parent.isReturn) {
      val method = parent.asInstanceOf[Return].method
      val method_return = method.asInstanceOf[Method].methodReturn

      val return_type = getNodeType(method_return)

      conds += createCondition("Returns",
        mutable.Map("methodName" -> method.name)
      )

      // Check if we know the return type before collecting evidence from the call sites
      if (return_type != "ANY") {
        conds += createCondition("Exact",
          mutable.Map("reason" -> "Return", "type" -> return_type, "methodName" -> method.name))
      } else if (!isBuiltIn(method)) {
        // Recurse backwards to collect more evidence from the call sites of
        // the parent method
        val calls_to_parent_method = cpg.call.name(method.name.replace("\\", "\\\\"))
        for (call <- calls_to_parent_method) {
          extractConditions(conds, call, analyzed, depth + 1, warnings)
        }
      }
    } else {
      throw new Exception("Unknown node: " + parent)
    }

    return extractConditions(conds, parent, analyzed, depth, warnings)

}

@main def exec(cpgFile: String, outFile: String, kBound: Int = 3, focus_lines: String = "") = {

  val outFileWarnings = outFile + ".warnings"
  maxDepth = kBound
  importCpg(cpgFile)
  // Names of deserialization APIs to look for
  var calls = cpg.call.name("unserialize") ++ cpg.call.name("maybe_unserialize") ++ cpg.call.name("deserialize")

  // Only analyze the requested calls if specified
  if (focus_lines != "") {
    val focus_entries = focus_lines.split(",")
    calls = calls.filter(x => focus_entries.contains(x.method.filename + ":" + x.lineNumber.getOrElse(-1).toString))
  }

  // Remove calls in the dependencies
  calls = calls.filter(!_.file.name.l(0).startsWith("vendor/"))

  // We save all the collected evidence in here
  var collected_conditions = new ListBuffer[String]()
  var warnings = new ListBuffer[String]()

  // To avoid cycles
  var analyzed_node_ids = mutable.Set[Long]()

  // Iterate through each deserialization call and collect evidence
  for (call <- calls) {
    println(call.file.name.l(0) + ":" + call.lineNumber.getOrElse(-1))
    var conditions = new ListBuffer[Map[String, String]]()
    // Call the main function that implements the type inference algorithm
    extractConditions(conditions, call, analyzed_node_ids, 0, warnings)

    // Create and store an entry containing the inferred types
    val entry = UnserEntry(call.file.name.l(0), call.lineNumber.getOrElse(-1), conditions.toSet)

    val conds_json: String = write(entry)
    collected_conditions += conds_json

    println()
    println("Conditions:")
    for (cond <- conditions.toSet) {
      println("\t" + cond)
    }

  }
  ("[" + collected_conditions.mkString(",") + "]") #> outFile

  println("Warnings:")
  for (warn <- warnings.toSet) {
    println("\t" + warn)
  }

  ("[" + warnings.mkString(",") + "]") #> outFileWarnings
}
