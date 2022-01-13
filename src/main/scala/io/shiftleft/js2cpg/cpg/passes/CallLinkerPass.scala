package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.{nodes, DispatchTypes, EdgeTypes, Operators}
import io.shiftleft.passes.{CpgPass, DiffGraph}
import io.shiftleft.semanticcpg.language._
import overflowdb.traversal._

import scala.collection.mutable

/**
  * This pass links call sites (by full name) and call sites to methods in the same file (by name).
  */
class CallLinkerPass(cpg: Cpg) extends CpgPass(cpg) {

  private val JS_EXPORT_NAMES = IndexedSeq("module.exports", "exports")

  private type MethodsByNameAndFileType = mutable.HashMap[(String, String), nodes.Method]
  private type MethodsByFullNameType    = mutable.HashMap[String, nodes.Method]

  private def isStaticSingleAssignmentLocal(ident: nodes.Identifier): Boolean =
    ident.refsTo
      .filter(_.isInstanceOf[nodes.Local])
      .cast[nodes.Local]
      .referencingIdentifiers
      .argumentIndex(1)
      .inCall
      .name(Operators.assignment + ".*")
      .size == 1

  private def createMethodsByNameAndFile(): (MethodsByNameAndFileType, MethodsByFullNameType) = {
    val methodsByNameAndFile = new MethodsByNameAndFileType()
    val methodsByFullName    = new MethodsByFullNameType()

    cpg.method.foreach { method =>
      methodsByNameAndFile.put((method.filename, method.name), method)
      methodsByFullName.put(method.fullName, method)

      // also find anonymous functions assigned to a global variable by the var name
      // (i.e. code that does `var foo = function() {}`)
      method.start
        .fullName(".*::program:anonymous\\d*")
        .in(EdgeTypes.REF)
        .collectAll[nodes.MethodRef]
        .argumentIndex(2)
        .inCall
        .nameExact(Operators.assignment)
        .argument(1)
        .collectFirst {
          case assignee: nodes.Identifier
              if isStaticSingleAssignmentLocal(assignee) && assignee.method.name == ":program" =>
            assignee.name
          case assignee: nodes.Call
              if assignee.methodFullName == Operators.fieldAccess &&
                JS_EXPORT_NAMES.contains(assignee.argument(1).code) =>
            assignee
              .argument(2)
              .asInstanceOf[nodes.FieldIdentifier]
              .canonicalName
        }
        .foreach { name =>
          methodsByNameAndFile.put((method.filename, name), method)
        }
    }

    (methodsByNameAndFile, methodsByFullName)
  }

  override def run(): Iterator[DiffGraph] = {
    val (methodsByNameAndFileType, methodsByFullName) = createMethodsByNameAndFile()
    val diffGraph                                     = linkCallsites(methodsByNameAndFileType, methodsByFullName)
    Iterator(diffGraph)
  }

  private def linkCallsites(methodsByNameAndFile: MethodsByNameAndFileType,
                            methodsByFullName: MethodsByFullNameType): DiffGraph = {
    val diffGraph = DiffGraph.newBuilder
    cpg.call.foreach { call =>
      if (call.dispatchType == DispatchTypes.STATIC_DISPATCH) {
        methodsByFullName.get(call.methodFullName).foreach { method =>
          diffGraph.addEdgeInOriginal(call, method, EdgeTypes.CALL)
        }
      } else {
        getReceiverIdentifierName(call).foreach { name =>
          for (file   <- call.file.headOption;
               method <- methodsByNameAndFile.get((file.name, name))) {
            diffGraph.addEdgeInOriginal(call, method, EdgeTypes.CALL)
          }
        }
      }
    }
    diffGraph.build()
  }

  private def callReceiverOption(callNode: nodes.Call): Option[nodes.Expression] =
    callNode._receiverOut.nextOption() map (_.asInstanceOf[nodes.Expression])

  // Obtain method name for dynamic calls where the receiver is an identifier.
  private def getReceiverIdentifierName(call: nodes.Call): Option[String] = {
    callReceiverOption(call).collect {
      case identifier: nodes.Identifier => identifier.name
    }
  }

}
