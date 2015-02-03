package leo

import leo.datastructures.impl.Signature
import leo.modules.Utility
import leo.datastructures.PolyHORecPathOrdering
import leo.datastructures.RPO

/**
 * Created by lex on 05.01.15.
 */
object Scribble {

  def time[A](a: => A): A = {
    val now = System.nanoTime
    val result = a
    val micros = (System.nanoTime - now) / 1000
           println("%d microseconds".format(micros))
    result
  }

  def main(args: Array[String]) {
    val f1 = "thf(list_type, type, (list: $tType))."
    val f2 = "thf(cons_type, type, (cons: $int > list > list))."
    val f3 = "thf(map_type, type, (map: list > ($int > $int) > list))."
    val f4 = "thf(nil_type, type, (nil: list))."
    val f5 = "thf(f_type, type, (f: $int > $int))."

    val g1 = "thf(t1, axiom, (map @ nil @ f))."
    val g2 = "thf(t2, axiom, nil)."

    Utility.add(f1)
    Utility.add(f2)
    Utility.add(f3)
    Utility.add(f4)
    Utility.add(f5)

    Utility.add(g1)
    Utility.add(g2)

//    Utility.context()
    val parsedG1 = Utility.get("t1").clause.lits.head.term

    val parsedG2 = Utility.get("t2").clause.lits.head.term

    println(parsedG1.pretty)
    println(parsedG2.pretty)
println(RPO.compare(parsedG1,parsedG2))
//    import leo.datastructures.term.Term
//    val factory = Term.local
//    val sig = Signature.get
//    val parsedF = factory.mkTermAbs(sig.o ->: sig.o, factory.mkTermApp(factory.mkBound(sig.o ->: sig.o, 1), Seq(factory.mkAtom(6))))
//    val parsedG = factory.mkTermAbs(sig.o ->: sig.o, factory.mkTermApp(factory.mkBound(sig.o ->: sig.o, 1), Seq(factory.mkAtom(6))))
//
//    println(s"${parsedF.pretty}, hash: ${parsedF.##}, reference: ${System.identityHashCode(parsedF)}, locality: ${parsedF.locality}")
//    println(s"${parsedG.pretty}, hash: ${parsedG.##}, reference: ${System.identityHashCode(parsedG)}, locality: ${parsedG.locality}")
//
//    println(time(parsedF.eq(parsedG)))
//    println(time(parsedF.equals(parsedG)))
//    println(time(parsedF == (parsedG)))
//
//    val f2 = Term.insert(parsedF)
//    val g2 = Term.insert(parsedG)
//
//    println(s"${f2.pretty}, hash: ${f2.##}, reference: ${System.identityHashCode(f2)}, locality: ${f2.locality}")
//    println(s"${g2.pretty}, hash: ${g2.##}, reference: ${System.identityHashCode(g2)}, locality: ${g2.locality}")
//
//    println(time(f2.eq(g2)))
//    println(time(f2.equals(g2)))
//    println(time(f2 == (g2)))

//    val subterms = parsedF.occurrences
//    for ((k,v) <- subterms) {
//      println(s"${k.pretty} \t \t ${v.map(_.pretty)}")
//    }
  }

}
