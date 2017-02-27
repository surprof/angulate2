//     Project: angulate2 (https://github.com/jokade/angulate2)
// Description: Angulate2 extension for definition of data objects/classes via an @Data annotation

// Copyright (c) 2016 Johannes.Kastner <jokade@karchedon.de>
//               Distributed under the MIT License (see included LICENSE file)
package angulate2.ext

import de.surfice.smacrotools.MacroAnnotationHandler

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import scala.scalajs.js

/**
 * Annotation for case classes to mark them as a pure JavaScript data object.
 *
 * @example
 * {{{
 * @Data
 * case class Foo(id: Int, var bar: String)
 * }}}
 * is expanded to
 * {{{
 * @js.native
 * trait Foo extends js.Object {
 *   val id: Int = js.native
 *   var bar: String = js.native
 * }
 *
 * object Foo {
 *   def apply(id: Int, bar: String): Foo =
 *     js.Dynamic.literal(id = id, bar = bar).asInstanceOf[Foo]
 * }
 * }}}
 */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class Data extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Data.Macro.impl
}


object Data {
  private[angulate2] class Macro(val c: whitebox.Context) extends MacroAnnotationHandler {
    import c.universe._

    override val annotationName: String = "Data"

    override val supportsClasses: Boolean = true

    override val supportsTraits: Boolean = false

    override val supportsObjects: Boolean = false

    override val createCompanion: Boolean = true

    private val jsObjectType = tq"${c.weakTypeOf[js.Object]}"
    private val jsNativeAnnot = q"new scalajs.js.native()"

//    override def analyze: Analysis = super.analyze andThen {
//      case d @ (parts,_) =>
//        if(!parts.isCase)
//          error("@Data annotation is only supported on case classes")
//        d
//    }

    override def transform: Transformation = super.transform andThen {
      case cls: ClassTransformData =>
        import cls.modParts._
        val members = params map {
          case q"$mods val $name: $tpe = $rhs" => ("val",name,tpe)
          case q"$_ var $name: $tpe = $_" => ("var",name,tpe)
        }
        val bodyMembers = members map {
          case ("val",name,tpe) => q"val $name: $tpe = scalajs.js.native"
          case ("var",name,tpe) => q"var $name: $tpe = scalajs.js.native"
        }

        val args = members map ( p => q"${p._2}: ${p._3}" )
        val literalArgs = members map ( p => q"${p._2} = ${p._2}" )
        val updCompanion = companion.map{ obj =>
          val apply = q"""def apply(..$args) = scalajs.js.Dynamic.literal(..$literalArgs).asInstanceOf[${cls.modParts.name}]"""
          TransformData(obj).updBody(Seq(apply)).modParts
        }

        val traitParts = TraitParts(
          name,
          tparams,
          Nil,
          Seq(jsObjectType),
          self,
          bodyMembers,
          fullName,
          Modifiers(NoFlags,modifiers.privateWithin,modifiers.annotations:+jsNativeAnnot),
          updCompanion)
        TraitTransformData(null,traitParts,cls.data)
      case x => x
    }
  }
}
