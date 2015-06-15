//package leo.modules.proofCalculi.enumeration
//
//import leo.datastructures._
//import leo.datastructures.Type._
//import Term._
//import leo.datastructures.impl.Signature
//
///**
// * Created by lex on 07.04.15.
// */
//object SpecialEnums extends Enumeration{
//  def enum(ty: Type) = {
//    val sig = Signature.get
//    val o = sig.o
//
//    if (ty == o ->: o) {
//      Seq(
//        λ(o)(LitTrue),
//        λ(o)(LitFalse),
//        λ(o)(1),
//        λ(o)(Not(1))
//      )
//    } else if (ty == o ->: o ->: o) {
//      Seq(
//        λ(o,o)(LitTrue),
//        λ(o,o)(LitFalse),
//        λ(o,o)(2),
//        λ(o,o)(1),
//
//        λ(o,o)(&(2,1)),
//        λ(o,o)(&(Not(2),Not(1))),
//        λ(o,o)(&(Not(2),1)),
//        λ(o,o)(&(2,Not(1))),
//
//        λ(o,o)(|||(2,1)),
//        λ(o,o)(|||(&(2,Not(1)),&(Not(2),1))),
//        λ(o,o)(|||(&(Not(2),Not(1)),&(2,1))),
//
//        λ(o,o)(Not(&(2,1))),
//        λ(o,o)(Not(&(Not(2),1))),
//        λ(o,o)(Not(&(2,Not(1)))),
//        λ(o,o)(Not(1)),
//        λ(o,o)(Not(2))
//      )
//    }
//    else {
//      Seq.empty
//    }
//
//  }
//}
