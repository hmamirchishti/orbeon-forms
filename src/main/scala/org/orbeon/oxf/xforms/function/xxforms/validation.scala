/**
 * Copyright (C) 2015 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.{NamespaceMapping, ShareableXPathStaticContext}
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.expr._
import org.orbeon.saxon.value.BooleanValue

import scala.collection.JavaConverters._
import scala.util.Try

trait ValidationFunction extends XFormsFunction with FunctionSupport {

  def propertyName: String

  def evaluate(value: String, constraintOpt: Option[Long]): Boolean

  override def evaluateItem(xpathContext: XPathContext): BooleanValue = {

    implicit val ctx = xpathContext

    val valueOpt      = Option(xpathContext.getContextItem) map (_.getStringValue)
    val constraintOpt = longArgumentOpt(0)

    val propertyStringOpt = constraintOpt map (_.toString) orElse Some("true")

    setProperty(propertyName, propertyStringOpt)

    valueOpt match {
      case Some(item) ⇒ evaluate(item, constraintOpt)
      case None       ⇒ true
    }
  }

  override def getIntrinsicDependencies =
    StaticProperty.DEPENDS_ON_CONTEXT_ITEM

  override def addToPathMap(
    pathMap        : PathMap,
    pathMapNodeSet : PathMapNodeSet
  ): PathMapNodeSet  = {

    val attachmentPoint = pathMapAttachmentPoint(pathMap, pathMapNodeSet)

    // For dependency on context
    if (attachmentPoint ne null)
      attachmentPoint.setAtomized()

    val result = new PathMapNodeSet
    iterateSubExpressions.asScala.asInstanceOf[Iterator[Expression]] foreach { child ⇒
      result.addNodeSet(child.addToPathMap(pathMap, attachmentPoint))
    }

    null
  }
}

object ValidationFunction {

  private val BasicNamespaceMapping =
    new NamespaceMapping(Map(
      XFORMS_PREFIX        → XFORMS_NAMESPACE_URI,
      XFORMS_SHORT_PREFIX  → XFORMS_NAMESPACE_URI,
      XXFORMS_PREFIX       → XXFORMS_NAMESPACE_URI,
      XXFORMS_SHORT_PREFIX → XXFORMS_NAMESPACE_URI
    ).asJava)

  def analyzeKnownConstraint(xpathString: String)(implicit logger: IndentedLogger): Option[(String, Option[String])] = {

    def tryCompile =
      Try(
        XPath.compileExpressionMinimal(
          staticContext = new ShareableXPathStaticContext(
            XPath.GlobalConfiguration,
            BasicNamespaceMapping, // TODO: use node namespaces
            XFormsFunctionLibrary
          ),
          xpathString   = xpathString
        )
      )

    def analyze(expr: Expression) =
      expr match {
        case e: ValidationFunction ⇒
          e.arguments.headOption match {
            case Some(l: Literal) ⇒ Some(e.propertyName → Some(l.getValue.getStringValue))
            case None             ⇒ Some(e.propertyName → None)
            case other            ⇒ None
          }
        case other ⇒
          None
      }

    tryCompile.toOption flatMap analyze
  }

}

class MaxLengthValidation extends ValidationFunction {

  val propertyName = "max-length"

  def evaluate(value: String, constraintOpt: Option[Long]) = constraintOpt match {
    case Some(constraint) ⇒ org.orbeon.saxon.value.StringValue.getStringLength(value) <= constraint
    case None             ⇒ true
  }
}

class MinLengthValidation extends ValidationFunction {

  val propertyName = "min-length"

  def evaluate(value: String, constraintOpt: Option[Long]) = constraintOpt match {
    case Some(constraint) ⇒ org.orbeon.saxon.value.StringValue.getStringLength(value) >= constraint
    case None             ⇒ true
  }
}

class NonNegativeValidation extends ValidationFunction {

  val propertyName = "non-negative"

  def evaluate(value: String, constraintOpt: Option[Long]) =
    NumericValidation.signum(value) != -1
}

class NegativeValidation extends ValidationFunction {

  val propertyName = "negative"

  def evaluate(value: String, constraintOpt: Option[Long]) =
    NumericValidation.signum(value) == -1
}

class NonPositiveValidation extends ValidationFunction {

  val propertyName = "non-positive"

  def evaluate(value: String, constraintOpt: Option[Long]) =
    NumericValidation.signum(value) != 1
}

class PositiveValidation extends ValidationFunction {

  val propertyName = "positive"

  def evaluate(value: String, constraintOpt: Option[Long]) =
    NumericValidation.signum(value) == 1
}

object NumericValidation {

  def signum(value: String): Int = parseAsLongDoubleOrBigDecimal(value) match {
    case v: Long       ⇒ v.signum
    case v: Double     ⇒ v.signum
    case v: BigDecimal ⇒ v.signum
    case _             ⇒ throw new IllegalStateException
  }

  // Return Long | Double | BigDecimal
  def parseAsLongDoubleOrBigDecimal(value: String): Any =
    try {
      value.toLong
    } catch {
      case e: NumberFormatException ⇒
        try {
          value.toDouble
        } catch {
          case e: NumberFormatException ⇒
            BigDecimal(value)
        }
    }
}