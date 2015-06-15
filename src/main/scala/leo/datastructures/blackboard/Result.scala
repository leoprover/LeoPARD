package leo
package datastructures.blackboard

import scala.collection.mutable

/**
 * Compagnion Object for Result
 */
object Result {

  /**
   * Creates an Empty Result Object.
   *
   * @return an empty Result Object.
   */
  def apply() : Result = new Result
}

/**
 * The Result / Delta of data an agent has
 * as an action on the data structures in the blackboard.
 *
 * The class allows adding of various types and a retrieval per type.
 *
 * This is a <b>mutable</b> class. The manipulating operations return
 * the changed object for conviniece.
 */
class Result {

  private val insertM : mutable.HashMap[DataType, Seq[Any]] = new mutable.HashMap[DataType, Seq[Any]]()
  private val updateM : mutable.HashMap[DataType, Seq[(Any,Any)]] = new mutable.HashMap[DataType, Seq[(Any,Any)]]()
  private val removeM : mutable.HashMap[DataType, Seq[Any]] = new mutable.HashMap[DataType, Seq[Any]]()
  private var prio : Int = 5


  /**
   * Inserts given data d of type t into
   * the registered data structures.
   *
   *
   * @param t is the type of the data.
   * @param d is the data itself
   */
  def insert (t : DataType)(d : Any): Result = {
    insertM.put(t, d +: insertM.getOrElse(t, Nil))
    this
  }

  /**
   * Updates a given data d1 to a new value d2
   * of type t in the registered data structures.
   *
   * @param t is the type of the data.
   * @param d1 is the old data
   * @param d2 is the new data
   */
  def update (t : DataType)(d1 : Any)(d2 : Any): Result = {
    updateM.put(t, (d1,d2) +: updateM.getOrElse(t, Nil))
    this
  }

  /**
   * Removes a given data d of type t
   *
   * @param t is the type of data.
   * @param d is the data itself.
   */
  def remove (t : DataType)(d : Any): Result = {
    removeM.put(t, d +: removeM.getOrElse(t, Nil))
    this
  }


  /**
   * Returns all inserts of type t.
   *
   * @param t is the requested type.
   * @return all inserted data of type t.
   */
  protected[blackboard] def inserts(t : DataType) : Seq[Any] = insertM.getOrElse(t, Nil)

  /**
   *
   * All updates of type t.
   *
   * @param t is the requested type.
   * @return all inserted data of type t.
   */
  protected[blackboard] def updates(t : DataType) : Seq[(Any, Any)] = updateM.getOrElse(t,Nil)

  /**
   *
   * All removes of type t.
   *
   * @param t is the requested type.
   * @return all inserted data of type t.
   */
  protected[blackboard] def removes(t : DataType) : Seq[Any] = removeM.getOrElse(t,Nil)

  /**
   * Returns a sequence of all stored datatypes by this result.
   *
   * @return all stored datatypes
   */
  protected[blackboard] def keys : Seq[DataType] = ((removeM.keySet union insertM.keySet) union updateM.keySet).toList

  /**
   * Returns the priority of the Result.
   * The priority is between 1 and 10, where 1 is important and 10 is unimportant.
   *
   * @return priority of the result
   */
  protected[blackboard] def priority : Int = prio

  /**
   * Sets the priority of the result to a value between
   * 1 and 10, where 1 is important and 10 is unimportant.
   *
   * @param prio sets the priority to max(min(prio,10),1)
   */
  def setPriority(prio : Int) : Result = {this.prio = prio.min(10).max(1); this}
}
