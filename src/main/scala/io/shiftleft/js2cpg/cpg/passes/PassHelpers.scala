package io.shiftleft.js2cpg.cpg.passes

import com.oracle.js.parser.ir.LiteralNode.ArrayLiteralNode
import com.oracle.js.parser.ir._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object PassHelpers {

  def generateUnusedVariableName(
    usedVariableNames: mutable.HashMap[String, Int],
    usedIdentNodes: Set[String],
    variableName: String
  ): String = {
    var counter = usedVariableNames.getOrElse(variableName, 0)

    var currentVariableName = ""
    while ({
      currentVariableName = s"${variableName}_$counter"
      counter += 1
      usedIdentNodes.contains(currentVariableName)
    }) {}

    usedVariableNames.put(variableName, counter)

    currentVariableName
  }

  private def unwrapBlockExpression(node: Node): Option[Node] = {
    node match {
      case expression: BlockExpression =>
        val block = expression.getBlock
        if (block.getStatementCount == 2 && block.getFirstStatement.isInstanceOf[VarNode]) {
          return Some(block.getFirstStatement)
        }
      case _ =>
    }
    None
  }

  // capture the pattern used to represent class declarations using the 'class' keyword
  def getClassDeclaration(varNode: VarNode): Option[ClassNode] = {
    varNode.getAssignmentSource match {
      case blockExpression: BlockExpression =>
        val unwrapped1 = unwrapBlockExpression(blockExpression)
        if (unwrapped1.nonEmpty && unwrapped1.get.isInstanceOf[VarNode]) {
          val targetVar = unwrapped1.get.asInstanceOf[VarNode]
          targetVar.getAssignmentSource match {
            case node: ClassNode => Some(node)
            case _               => None
          }
        } else {
          None
        }
      case classNode: ClassNode =>
        Some(classNode)
      case _ =>
        None
    }
  }

  @tailrec
  def getRequire(callNode: CallNode): Option[String] = {
    callNode.getFunction match {
      case identNode: IdentNode
          if identNode.getName.toJavaStringUncached == "require" && callNode.getArgs.size() == 1 =>
        callNode.getArgs.asScala
          .collectFirst { case literalNode: LiteralNode.PrimitiveLiteralNode[_] =>
            literalNode
          }
          .map(_.getPropertyName.toJavaStringUncached)
      case accessNode: AccessNode if accessNode.getBase.isInstanceOf[CallNode] =>
        getRequire(accessNode.getBase.asInstanceOf[CallNode])
      case other: Node => getRequire(other)
    }
  }

  @tailrec
  def getRequire(node: Node): Option[String] = {
    node match {
      case node: CallNode =>
        getRequire(node)
      case accessNode: AccessNode =>
        getRequire(accessNode.getBase)
      case _ => None
    }
  }

  def cleanParameterNodeName(parameterNode: Node): String =
    parameterNode.toString().replaceAll("\\[", "").replaceAll("]", "")

  object ParamNodeInitKind extends Enumeration {
    type ParamNodeInitKind = Value
    val FALSE, PLAIN, CONDITIONAL = Value
  }

  // If one parameter of a function has a default value, all parameters
  // are initialized via the build in parameter array. The accesses to this
  // array are represented with ParameterNode instances.
  def initializedViaParameterNode(varNode: VarNode): ParamNodeInitKind.ParamNodeInitKind =
    varNode match {
      case varNode: VarNode if varNode.getInit.isInstanceOf[TernaryNode] =>
        val ternaryNode = varNode.getInit.asInstanceOf[TernaryNode]
        ternaryNode.getTest match {
          case binaryNode: BinaryNode if binaryNode.getLhs.isInstanceOf[ParameterNode] =>
            ParamNodeInitKind.CONDITIONAL
          case _ =>
            ParamNodeInitKind.FALSE
        }
      case varNode: VarNode if varNode.getInit.isInstanceOf[ParameterNode] =>
        ParamNodeInitKind.PLAIN
      case _ =>
        ParamNodeInitKind.FALSE
    }

  def collectSyntheticParameters(statements: Iterable[Statement]): List[IdentNode] =
    statements
      .collect {
        case varNode: VarNode if initializedViaParameterNode(varNode) != ParamNodeInitKind.FALSE =>
          List(varNode.getName)
      }
      .flatten
      .toList

  def collectDestructingParameters(statements: Iterable[Statement]): List[List[IdentNode]] =
    statements
      .collect { case expressionStatement: ExpressionStatement =>
        expressionStatement.getExpression match {
          case binaryNode: BinaryNode
              if binaryNode.getRhs.isInstanceOf[ParameterNode] &&
                binaryNode.getLhs.isInstanceOf[ArrayLiteralNode]
                && binaryNode.getRhs
                  .asInstanceOf[ParameterNode]
                  .toString()
                  .startsWith("arguments") =>
            binaryNode.getLhs
              .asInstanceOf[ArrayLiteralNode]
              .getElementExpressions
              .asScala
              .collect {
                case expr: Expression if expr.isInstanceOf[IdentNode] =>
                  expr.asInstanceOf[IdentNode]
              }
              .toList
          case binaryNode: BinaryNode
              if binaryNode.getRhs.isInstanceOf[ParameterNode] &&
                binaryNode.getLhs.isInstanceOf[ObjectNode]
                && binaryNode.getRhs
                  .asInstanceOf[ParameterNode]
                  .toString()
                  .startsWith("arguments") =>
            binaryNode.getLhs
              .asInstanceOf[ObjectNode]
              .getElements
              .asScala
              .collect {
                case p: PropertyNode if p.getKey.isInstanceOf[IdentNode] =>
                  p.getKey.asInstanceOf[IdentNode]
              }
              .toList
          case binaryNode: BinaryNode
              if binaryNode.getLhs.isInstanceOf[ObjectNode] && binaryNode.getRhs
                .isInstanceOf[TernaryNode] =>
            val ternaryNode = binaryNode.getRhs.asInstanceOf[TernaryNode]
            ternaryNode.getTest match {
              case binaryNode2: BinaryNode if binaryNode2.getLhs.isInstanceOf[ParameterNode] =>
                if (
                  binaryNode2.getLhs
                    .asInstanceOf[ParameterNode]
                    .toString()
                    .startsWith("arguments")
                ) {
                  binaryNode.getLhs
                    .asInstanceOf[ObjectNode]
                    .getElements
                    .asScala
                    .collect {
                      case p: PropertyNode if p.getKey.isInstanceOf[IdentNode] =>
                        p.getKey.asInstanceOf[IdentNode]
                    }
                    .toList
                } else Nil
              case _ => Nil
            }
          case _ => Nil
        }
      }
      .toList
      .filter(_.nonEmpty)

  def isConditionallyInitialized(statement: Statement, identNodes: List[IdentNode]): Boolean = {
    statement match {
      case varNode: VarNode
          if initializedViaParameterNode(varNode) == ParamNodeInitKind.CONDITIONAL && identNodes
            .exists(_.getName == varNode.getName.getName) =>
        true
      case _ =>
        val candidate =
          statement.isInstanceOf[ExpressionStatement] && isSynthetic(statement, identNodes)
        if (candidate) {
          statement.asInstanceOf[ExpressionStatement].getExpression match {
            case node: BinaryNode =>
              node.getRhs
                .isInstanceOf[TernaryNode]
            case _ => false
          }
        } else false
    }
  }

  def isSynthetic(statement: Statement, identNodes: List[IdentNode]): Boolean = statement match {
    case unaryNode: Statement if unaryNode.toString() == "yield void 0" => true
    case varNode: VarNode =>
      identNodes.exists(_.getName == varNode.getName.getName)
    case expressionStatement: ExpressionStatement =>
      expressionStatement.getExpression match {
        case binaryNode: BinaryNode
            if binaryNode.getRhs.isInstanceOf[ParameterNode] && binaryNode.getLhs
              .isInstanceOf[ArrayLiteralNode]
              && binaryNode.getRhs
                .asInstanceOf[ParameterNode]
                .toString()
                .startsWith("arguments") =>
          binaryNode.getLhs
            .asInstanceOf[ArrayLiteralNode]
            .getElementExpressions
            .asScala
            .exists {
              case expr if expr == null => false
              case expr: Expression if expr.isInstanceOf[IdentNode] =>
                identNodes.exists(_.getName == expr.asInstanceOf[IdentNode].getName)
              case _ => false
            }
        case binaryNode: BinaryNode
            if binaryNode.getRhs.isInstanceOf[ParameterNode] &&
              binaryNode.getLhs.isInstanceOf[ObjectNode]
              && binaryNode.getRhs
                .asInstanceOf[ParameterNode]
                .toString()
                .startsWith("arguments") =>
          binaryNode.getLhs.asInstanceOf[ObjectNode].getElements.asScala.exists {
            case p if p == null => false
            case p: PropertyNode if p.getKey.isInstanceOf[IdentNode] =>
              identNodes.exists(_.getName == p.getKey.asInstanceOf[IdentNode].getName)
            case p: PropertyNode if p.isRest =>
              identNodes.exists(
                _.getName == p.getKey
                  .asInstanceOf[UnaryNode]
                  .getExpression
                  .asInstanceOf[IdentNode]
                  .getName
              )
            case _ => false
          }
        case binaryNode: BinaryNode
            if binaryNode.getLhs.isInstanceOf[ObjectNode] && binaryNode.getRhs
              .isInstanceOf[TernaryNode] =>
          val ternaryNode = binaryNode.getRhs.asInstanceOf[TernaryNode]
          ternaryNode.getTest match {
            case binaryNode2: BinaryNode if binaryNode2.getLhs.isInstanceOf[ParameterNode] =>
              if (
                binaryNode2.getLhs
                  .asInstanceOf[ParameterNode]
                  .toString()
                  .startsWith("arguments")
              ) {
                binaryNode.getLhs.asInstanceOf[ObjectNode].getElements.asScala.exists {
                  case p if p == null => false
                  case p: PropertyNode if p.getKey.isInstanceOf[IdentNode] =>
                    identNodes.exists(_.getName == p.getKey.asInstanceOf[IdentNode].getName)
                  case p: PropertyNode if p.isRest =>
                    identNodes.exists(
                      _.getName == p.getKey
                        .asInstanceOf[UnaryNode]
                        .getExpression
                        .asInstanceOf[IdentNode]
                        .getName
                    )
                  case _ => false
                }
              } else false
            case _ => false
          }
        case _ => false
      }
    case _ => false
  }

  private def getIndex(node: ExpressionStatement, identNode: IdentNode): Int = {
    node.getExpression match {
      case binaryNode: BinaryNode
          if binaryNode.getRhs.isInstanceOf[ParameterNode] && binaryNode.getLhs
            .isInstanceOf[ArrayLiteralNode]
            && binaryNode.getRhs
              .asInstanceOf[ParameterNode]
              .toString()
              .startsWith("arguments") =>
        if (
          binaryNode.getLhs
            .asInstanceOf[ArrayLiteralNode]
            .getElementExpressions
            .asScala
            .exists {
              case expr if expr == null => false
              case expr: Expression if expr.isInstanceOf[IdentNode] =>
                identNode.getName == expr.asInstanceOf[IdentNode].getName
              case _ => false
            }
        ) {
          binaryNode.getRhs
            .asInstanceOf[ParameterNode]
            .getIndex
        } else -1
      case binaryNode: BinaryNode
          if binaryNode.getRhs.isInstanceOf[ParameterNode] && binaryNode.getLhs
            .isInstanceOf[ObjectNode]
            && binaryNode.getRhs
              .asInstanceOf[ParameterNode]
              .toString()
              .startsWith("arguments") =>
        if (
          binaryNode.getLhs.asInstanceOf[ObjectNode].getElements.asScala.exists {
            case p if p == null => false
            case p: PropertyNode if p.getKey.isInstanceOf[IdentNode] =>
              identNode.getName == p.getKey.asInstanceOf[IdentNode].getName
            case _ => false
          }
        ) {
          binaryNode.getRhs
            .asInstanceOf[ParameterNode]
            .getIndex
        } else -1
      case binaryNode: BinaryNode
          if binaryNode.getLhs.isInstanceOf[ObjectNode] && binaryNode.getRhs
            .isInstanceOf[TernaryNode] =>
        val ternaryNode = binaryNode.getRhs.asInstanceOf[TernaryNode]
        ternaryNode.getTest match {
          case binaryNode2: BinaryNode if binaryNode2.getLhs.isInstanceOf[ParameterNode] =>
            if (
              binaryNode2.getLhs
                .asInstanceOf[ParameterNode]
                .toString()
                .startsWith("arguments")
            ) {
              if (
                binaryNode.getLhs.asInstanceOf[ObjectNode].getElements.asScala.exists {
                  case p if p == null => false
                  case p: PropertyNode if p.getKey.isInstanceOf[IdentNode] =>
                    identNode.getName == p.getKey.asInstanceOf[IdentNode].getName
                  case _ => false
                }
              ) {
                binaryNode2.getLhs
                  .asInstanceOf[ParameterNode]
                  .getIndex
              } else -1
            } else -1
          case _ => -1
        }
      case _ => -1
    }
  }

  def calculateParameterIndex(identNode: IdentNode, statements: List[Statement]): Int = {
    statements.collect {
      case node: ExpressionStatement if isSynthetic(node, List(identNode)) =>
        val index = getIndex(node, identNode)
        if (index != -1) return index
      case node: VarNode if node.getName.getName == identNode.getName =>
        node match {
          case varNode: VarNode if varNode.getInit.isInstanceOf[TernaryNode] =>
            val ternaryNode = varNode.getInit.asInstanceOf[TernaryNode]
            ternaryNode.getTest match {
              case binaryNode: BinaryNode if binaryNode.getLhs.isInstanceOf[ParameterNode] =>
                return binaryNode.getLhs.asInstanceOf[ParameterNode].getIndex
            }
          case varNode: VarNode if varNode.getInit.isInstanceOf[ParameterNode] =>
            return varNode.getInit.asInstanceOf[ParameterNode].getIndex
          case _ =>
        }
    }
    -1
  }

}
