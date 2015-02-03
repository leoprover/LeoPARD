package leo.datastructures.context

import leo.Configuration
import leo.modules.CLParameterParser
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import leo.datastructures.context.impl.TreeContext

/**
 *
 * Tests the {@see TreeContext} Implementation of {@see Context}.
 *
 * @author Max Wisniewski
 * @since 11/25/14
 */
@RunWith(classOf[JUnitRunner])
class TreeContextTestSuite extends FunSuite{

  test("BaseContext empty"){
    val b : Context = new TreeContext // Creates an empty BaseContext
    assert(b.parentContext == null, "Parent Context is not empty")
    assert(b.childContext.isEmpty, "There are children in initial state.")
    assert(b.splitKind == NoSplit, "The initial context is already splitted.")
  }

  test("Creating children") {
    val b : Context = new TreeContext
    b.split(AlphaSplit, 3)
    assert(b.splitKind == AlphaSplit, "The parent is not Alpha splitted.")
    assert(b.childContext.size == 3, "The root context should have 3 children")
    b.childContext foreach { c =>
      assert(c.parentContext == b, s"Parent Context should be root=$b")
      assert(c.childContext.isEmpty, "There are children in initial state.")
      assert(c.splitKind == NoSplit, "The initial context is already splitted.")
    }
  }

  test("Closing context") {
    val b : Context = new TreeContext
    b.split(AlphaSplit, 3)
    assert(b.childContext.size == 3, "The root context should have 3 children")

    b.close()

    assert(b.splitKind == UnSplittable, "The root context should be closed.")
    assert(b.childContext.size == 0, "The root context should have no children")
  }
}
