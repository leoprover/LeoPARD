package leo.modules.parsers

import leo.Out

import leo.datastructures._
import leo.datastructures.impl.Signature
import leo.datastructures.term.Term
import leo.datastructures.tptp.Commons.Term

import scala.language.implicitConversions

import leo.datastructures.tptp.Commons._
import leo.datastructures.tptp.Commons.{Term => TPTPTerm}
import leo.datastructures.{Kind, Clause, Literal, Role}
import Term.{mkAtom,λ,Λ, mkBound,mkTermApp, mkTypeApp}
import Type.{mkFunType,mkType,∀,mkVarType, typeKind,mkProdType, mkUnionType}

import leo.datastructures.tptp.Commons.THFAnnotated
import leo.datastructures.tptp.Commons.TPIAnnotated
import leo.datastructures.tptp.Commons.TFFAnnotated
/**
 * Processing module from TPTP input.
 * Declarations are inserted into the given Signature,
 * terms are returned in internal term representation.
 *
 * @author Alexander Steen
 * @since 18.06.2014
 */
object InputProcessing {
  // (Formula name, Term, Formula Role)
  type Result = (String, Clause, Role)

  // Types for replacing bound variables by de bruijn indices
  type TermVarReplaces = (Map[Variable, (Type, Int)], Int) // (varname -> (vartype, position in list), index offset)
  type TypeVarReplaces = (Map[Variable, (Kind, Int)], Int)
  type Replaces = (TermVarReplaces, TypeVarReplaces)
  def termMapping(r: Replaces) = (r._1)._1
  def typeMapping(r: Replaces) = (r._2)._1
  def termOffset(r: Replaces) = (r._1)._2
  def typeOffset(r: Replaces) = (r._2)._2
  def noRep: Replaces = ((Map.empty, 0), (Map.empty, 0))

  type TypeOrKind = Either[Type, Kind]
  type ProcessedVar = (Variable, TypeOrKind)
  /**
   * Assumptions:
   * - To guarantee coherence, the processing is invoked in the right order (i.e. included files are parsed an processed before all
   * following tptp statements)
   *
   * Side effects: All declarations that are not representable as term (e.g. type declarations, subtype declarations) are
   * inserted into the signature `sig` while processing.
   *
   * @param sig The signature declarations are inserted into
   * @param input The TPTP formula to process/translate
   * @return A List of tuples (name, term, role) of translated terms
   */
  def processAll(sig: Signature)(input: Seq[AnnotatedFormula]): Seq[Result] = {
    input.map({case f => process(sig)(f) match {
      case None      => {  // TODO: Why not just drop it?
        val role = processRole(f.role)
        (f.name, singleTermToClause(LitTrue(), role), role)
      }
      case Some(res) => res
    }})
  }

  private def processRole(role: String): Role = Role(role)
  private def roleToClauseOrigin(role: Role): ClauseOrigin = role match {
    case Role_Conjecture => FromConjecture
    case Role_NegConjecture => FromConjecture
    case _ => FromAxiom
  }
  private def singleTermToClause(t: term.Term, role: Role): Clause = Clause.mkClause(Seq(Literal.mkPosLit(t)), roleToClauseOrigin(role))

  def process(sig: Signature)(input: AnnotatedFormula): Option[Result] = {
    input match {
      case _:TPIAnnotated => processTPI(sig)(input.asInstanceOf[TPIAnnotated])
      case _:THFAnnotated => processTHF(sig)(input.asInstanceOf[THFAnnotated])
      case _:TFFAnnotated => processTFF(sig)(input.asInstanceOf[TFFAnnotated])
      case _:FOFAnnotated => processFOF(sig)(input.asInstanceOf[FOFAnnotated])
      case _:CNFAnnotated => processCNF(sig)(input.asInstanceOf[CNFAnnotated])
    }
  }


  //////////////////////////
  // TPI Formula processing
  //////////////////////////

  def processTPI(sig: Signature)(input: TPIAnnotated): Option[Result] = ???


  //////////////////////////
  // THF Formula processing
  //////////////////////////

  def processTHF(sig: Signature)(input: THFAnnotated): Option[Result] = {
    import leo.datastructures.tptp.thf.{Sequent, Logical, Typed, Term}

    input.formula match {
      case Logical(lf) if input.role == "definition" => {
        processTHFDef(sig)(lf) match {
          case None => {
            Out.info(s"No direction of definition ${input.name} detected. Treating as axiom.")
            val role = processRole("axiom");
            Some((input.name, singleTermToClause(processTHF0(sig)(lf, noRep), role), role))
          }
          case Some((defName, defDef)) => {
            if (sig.exists(defName)) {
              val meta = sig(defName)
              if (meta.isUninterpreted && meta._ty == defDef.ty) {
                sig.addDefinition(meta.key, defDef)
              } else {
                Out.debug("Old type: " + meta._ty.pretty)
                Out.debug("Symbol: " + defName)
                Out.debug("Def:" +defDef.pretty)
                Out.debug("Type of def: "+defDef.ty.pretty)
                Out.warn("Symbol "+defName + " already defined. Redefinition ignored.")
              }
            } else {
              sig.addDefined(defName, defDef, defDef.ty)
            }
            None
          }
        }
                                                        }
      case Logical(Typed(Term(Func(atom, _)),ty)) if input.role == "type" => {
                                                        convertTHFType(sig)(ty, noRep) match {
                                                          case Left(ty) => sig.addUninterpreted(atom, ty)
                                                          case Right(k) => sig.addTypeConstructor(atom, k)
                                                        }
                                                        None
                                                      }
      case Logical(lf)                               => val role = processRole(input.role); Some((input.name, singleTermToClause(processTHF0(sig)(lf, noRep), role), role))
      case Sequent(_,_)                              => throw new IllegalArgumentException("Processing of THF sequents not implemented")
    }
  }

  import leo.datastructures.tptp.thf.{LogicFormula => THFLogicFormula}
  protected[parsers] def processTHFDef(sig: Signature)(input: THFLogicFormula): Option[(String, term.Term)] = {
    import leo.datastructures.tptp.thf.{Binary, Term, Eq}
    input match {
      case Binary(Term(Func(name, Seq())), Eq, right) => Some(name, processTHF0(sig)(right, noRep))
      case _                                        => None
    }
  }

  protected[parsers] def processTHF0(sig: Signature)(input: THFLogicFormula, replaces: Replaces): term.Term = {
    import leo.datastructures.tptp.thf.{Typed, Binary, Unary, Quantified, Connective, Term, BinType, Subtype, Cond, Let}

    input match {
      case Typed(f, ty) => processTHF0(sig)(f,replaces) // TODO: What to do with type information?
      case Binary(left, conn, right) => processTHFBinaryConn(conn).apply(processTHF0(sig)(left, replaces),processTHF0(sig)(right, replaces))
      case Unary(conn, f) => processTHFUnaryConn(conn).apply(processTHF0(sig)(f, replaces))
      case Quantified(q, vars, matrix) => {
        val quantifier = processTHFUnaryConn(q)
        var newReplaces = replaces

        // Fold through the variables to propagate bindings trough variable list
        // and save bindings to `newReplaces` for body conversion
        val processedVars = vars.map(_ match {
          case (name, None) => {
            termMapping(newReplaces).get(name) match {
              case None => newReplaces = ((termMapping(newReplaces).+((name,(sig.i, termMapping(newReplaces).size+1))),termOffset(newReplaces)),newReplaces._2)
              case _ =>  newReplaces = ((termMapping(newReplaces).+((name,(sig.i, termMapping(newReplaces).size+1))),termOffset(newReplaces)+1),newReplaces._2)
            }
            (name, Left(sig.i))
          }
          case (name, Some(ty)) => convertTHFType(sig)(ty, newReplaces) match {
            case Left(t) => {
              termMapping(newReplaces).get(name) match {
                case None => newReplaces = ((termMapping(newReplaces).+((name,(t, termMapping(newReplaces).size+1))),termOffset(newReplaces)),newReplaces._2)
                case _ =>  newReplaces = ((termMapping(newReplaces).+((name,(t, termMapping(newReplaces).size+1))),termOffset(newReplaces)+1),newReplaces._2)
              }
              (name, Left(t))
            }
            case Right(k) => {
              typeMapping(newReplaces).get(name) match {
                case None => newReplaces = (newReplaces._1,(typeMapping(newReplaces).+((name,(k, typeMapping(newReplaces).size+1))),typeOffset(newReplaces)))
                case _ =>  newReplaces = (newReplaces._1,(typeMapping(newReplaces).+((name,(k, typeMapping(newReplaces).size+1))),typeOffset(newReplaces) +1))
              }
              (name, Right(k))
            }
          }

          })

        mkPolyQuantified(quantifier, processedVars, processTHF0(sig)(matrix, newReplaces))
      }
      case Connective(c) => c.fold(processTHFBinaryConn(_),processTHFUnaryConn(_))
      case Term(t) => processTerm(sig)(t, replaces, false)
      case BinType(binTy) => throw new IllegalArgumentException("Binary Type formulae should not appear on top-level")
      case Subtype(left,right) => ???
      case Cond(c, thn, els) => {
        import leo.datastructures.IF_THEN_ELSE
        IF_THEN_ELSE(processTHF0(sig)(c, replaces),processTHF0(sig)(thn, replaces),processTHF0(sig)(els, replaces))
      }
      case Let(binding, in) => Out.warn("Unsupported let-definition in term, treated as $true."); LitTrue()
    }
  }

  import leo.datastructures.tptp.thf.{BinaryConnective => THFBinaryConnective}
  protected[parsers] def processTHFBinaryConn(conn: THFBinaryConnective): HOLBinaryConnective = {
    import leo.datastructures.tptp.thf.{Eq => THFEq, Neq => THFNeq, <=> => THFEquiv, Impl => THFImpl, <= => THFIf, <~> => THFNiff, ~| => THFNor, ~& => THFNand, | => THFOr, & => THFAnd, App => THFApp}
    import leo.datastructures.{<=> => equiv, Impl => impl, <= => i_f, ||| => or, & => and, ~||| => nor, ~& => nand, <~> => niff, ===, !===}
    import leo.datastructures.term.Term.@@@

    conn match {
      case THFEq => ===
      case THFNeq => !===
      case THFEquiv => equiv
      case THFImpl  => impl
      case THFIf    => i_f
      case THFOr    => or
      case THFAnd   => and
      case THFNor   => nor
      case THFNand  => nand
      case THFNiff  => niff
      case THFApp   => @@@
    }
  }

  import leo.datastructures.tptp.thf.{UnaryConnective => THFUnaryConnective}
  protected[parsers] def processTHFUnaryConn(conn: THFUnaryConnective): HOLUnaryConnective = {
    import leo.datastructures.tptp.thf.{~ => THFNot, !! => THFAllComb, ?? => THFExistsComb}
    import leo.datastructures.{Not => not, Forall => forall, Exists => exists}

    conn match {
      case THFNot => not
      case THFAllComb => forall
      case THFExistsComb => exists
    }
  }

  import leo.datastructures.tptp.thf.{Quantifier => THFQuantifier}
  protected[parsers] def processTHFUnaryConn(conn: THFQuantifier): HOLUnaryConnective = {
    import leo.datastructures.tptp.thf.{! => THFAll, ? => THFExists, ^ => THFLambda, @+ => THFChoice, @- => THFDesc}
    import leo.datastructures.{Forall, Exists, Choice, Description}

    conn match {
      case THFAll => Forall
      case THFExists => Exists
      case THFLambda => new HOLUnaryConnective { // little hack here, to simulate a lambda, the apply function is the identity
                                                 // this is because the mkPolyQuantified will apply a new abstraction
        val key: Signature#Key = Integer.MIN_VALUE // just for fun!
        override def apply(arg: term.Term) = arg
      }

      case THFChoice => Choice
      case THFDesc => Description

      case _ => throw new IllegalArgumentException("Illegal quantifier symbol:" +conn.toString)
    }
  }

  protected[parsers] def convertTHFType(sig: Signature)(typ: THFLogicFormula, replaces: Replaces): TypeOrKind = {
    import leo.datastructures.tptp.thf.{Quantified, Term, BinType}

    typ match {
      case Quantified(q, vars, matrix) => {
        import leo.datastructures.tptp.thf.{!> => THFTyForAll, ?* => THFTyExists}

        q match {
          case THFTyForAll => {
            val processedVars = vars.map(_ match {
              case (name, None) => (name, Right(typeKind)) // * is assumed when no type is given
              case (name, Some(ty)) => (name, convertTHFType(sig)(ty, replaces))
            })
            require(processedVars.forall(_._2.isRight), "Only '$tType' as type assertion is allowed for type variables in quantified types")
            val newReplaces = processedVars.foldLeft(replaces)({case (repl,vari) => vari match {
              case (name, Left(ty)) => {
                termMapping(repl).get(name) match {
                  case None => ((termMapping(repl).+((name,(ty, termMapping(repl).size+1))),termOffset(repl)),repl._2)
                  case _ =>  ((termMapping(repl).+((name,(ty, termMapping(repl).size+1))),termOffset(repl)+1),repl._2)
                }
              }
              case (name, Right(k)) => {
                typeMapping(repl).get(name) match {
                  case None => (repl._1,(typeMapping(repl).+((name,(k, typeMapping(repl).size+1))),typeOffset(repl)))
                  case _ =>  (repl._1,(typeMapping(repl).+((name,(k, typeMapping(repl).size+1))),typeOffset(repl)+1))
                }
              }
            }})
            processedVars.foldRight(convertTHFType(sig)(matrix,newReplaces).left.get)({case (_,b) => ∀(b)}) // NOTE: this is only allowed on top-level
            // the body of quantification must be a type.
            // TODO: better error treating
          }
          case THFTyExists => ???
          case _ => throw new IllegalArgumentException("Illegal quantifier on type level: " + typ.toString)
        }
      }
      case Term(t) => {
        t match {
          case Func(ty, List()) => mkType(sig(ty).key) // Base type
//          case Func(ty, args)   => ???
          case DefinedFunc(ty, List()) if ty == "$tType" =>  typeKind // kind *
          case DefinedFunc(ty, List()) =>  mkType(sig(ty).key) // defined type
          case SystemFunc(ty, List()) =>  mkType(sig(ty).key) // system type
          case Var(name) =>  mkVarType(typeMapping(replaces).size + typeOffset(replaces) - typeMapping(replaces)(name)._2 + 1)
          case _ => throw new IllegalArgumentException("malformed/unsupported term type expression: "+typ.toString)
        }
      }
      case BinType(binTy) => {
        import leo.datastructures.tptp.thf.{->, *, +}
        binTy match {
          case ->(tys) => {
            val converted = tys.map(convertTHFType(sig)(_, replaces))
            // as for TFF, we consider only types here. this may change in future
            require(converted.forall(_.isLeft), "Function constructor only applicable on types at the moment")
            mkFunType(converted.map(_.left.get))
          }
          case *(tys) => {
            val converted = tys.map(convertTHFType(sig)(_, replaces))
            // TODO: we consider only types here, is this correct?
            require(converted.forall(_.isLeft), "Sum constructor only allowed on types")
            mkProdType(converted.map(_.left.get))
          }
          case +(tys) => {
            val converted = tys.map(convertTHFType(sig)(_, replaces))
            // TODO: we consider only types here, is this correct?
            require(converted.forall(_.isLeft), "Union constructor only allowed on types")
            mkUnionType(converted.map(_.left.get))
          }
        }
      } //arrow type etc
      case _ => throw new IllegalArgumentException("malformed type expression: "+typ.toString)
    }
  }

  //////////////////////////
  // TFF Formula processing
  //////////////////////////

  def processTFF(sig: Signature)(input: TFFAnnotated): Option[Result] = {
    import leo.datastructures.tptp.tff.{Logical, TypedAtom, Sequent}

    input.formula match {
      // Logical formulae can either be terms (axioms, conjecture, ...) or definitions.
      case Logical(lf) if input.role == "definition" => processTFFDef(sig)(lf) match {
        case None => Out.info(s"No direction of definition ${input.name} detected. Treating as axiom.");
                     val role = processRole("axiom"); Some((input.name, singleTermToClause(processTFF0(sig)(lf, noRep),role), role))
        case Some((defName, defDef)) => sig.addDefined(defName, defDef, defDef.ty)
                                        None
      }
      case Logical(lf) => val role = processRole(input.role); Some((input.name, singleTermToClause(processTFF0(sig)(lf, noRep),role), role))
      // Typed Atoms are top-level declarations, put them into signature
      case TypedAtom(atom, ty) => {
        convertTFFType(sig)(ty, noRep) match {
          case Left(ty) => sig.addUninterpreted(atom, ty)
          case Right(k) => sig.addTypeConstructor(atom, k) // TODO: constructors get own method and symbol type!
        }
        None
      }
      // Sequents
      case Sequent(_, _) => throw new IllegalArgumentException("Processing of TFF sequents not yet implemented")
    }


  }

  import leo.datastructures.tptp.tff.{LogicFormula => TFFLogicFormula}
  // Formula definitions
  protected[parsers] def processTFFDef(sig: Signature)(input: TFFLogicFormula): Option[(String, term.Term)] = {
    import leo.datastructures.tptp.tff.Atomic
    input match {
      case Atomic(Equality(Func(name, Nil),right)) => Some(name, processTerm(sig)(right, noRep, false))  // TODO Is this the right term to construct equalities in tff?
      case _ => None
    }
  }

  // Ordinary terms
  protected[parsers] def processTFF0(sig: Signature)(input: TFFLogicFormula, replaces: Replaces): term.Term = {
    import leo.datastructures.tptp.tff.{Binary, Quantified, Unary, Inequality, Atomic, Cond, Let}
    input match {
      case Binary(left, conn, right) => processTFFBinaryConn(conn).apply(processTFF0(sig)(left,replaces),processTFF0(sig)(right,replaces))
      case Quantified(q, vars, matrix) => {
        val quantifier = processTFFUnary(q)
        var newReplaces = replaces

        // Fold through the variables to propagate bindings trough variable list
        // and save bindings to `newReplaces` for body conversion
        val processedVars = vars.map(_ match {
          case (name, None) => {
            termMapping(newReplaces).get(name) match {
              case None => newReplaces = ((termMapping(newReplaces).+((name,(sig.i, termMapping(newReplaces).size+1))),termOffset(newReplaces)),newReplaces._2)
              case _ =>  newReplaces = ((termMapping(newReplaces).+((name,(sig.i, termMapping(newReplaces).size+1))),termOffset(newReplaces)+1),newReplaces._2)
            }
            (name, Left(sig.i))
          }
          case (name, Some(ty)) => convertTFFType(sig)(ty, newReplaces) match {
            case Left(t) => {
              termMapping(newReplaces).get(name) match {
                case None => newReplaces = ((termMapping(newReplaces).+((name,(t, termMapping(newReplaces).size+1))),termOffset(newReplaces)),newReplaces._2)
                case _ =>  newReplaces = ((termMapping(newReplaces).+((name,(t, termMapping(newReplaces).size+1))),termOffset(newReplaces)+1),newReplaces._2)
              }
              (name, Left(t))
            }
            case Right(k) => {
              typeMapping(newReplaces).get(name) match {
                case None => newReplaces = (newReplaces._1,(typeMapping(newReplaces).+((name,(k, typeMapping(newReplaces).size+1))),typeOffset(newReplaces)))
                case _ =>  newReplaces = (newReplaces._1,(typeMapping(newReplaces).+((name,(k, typeMapping(newReplaces).size+1))),typeOffset(newReplaces) +1))
              }
              (name, Right(k))
            }
          }

        })
        mkPolyQuantified(quantifier, processedVars, processTFF0(sig)(matrix, newReplaces))
      }
      case Unary(conn, formula) => processTFFUnary(conn).apply(processTFF0(sig)(formula,replaces))
      case Inequality(left, right) => {
        val (l,r) = (processTerm(sig)(left, replaces, false),processTerm(sig)(right, replaces, false))
        import leo.datastructures.Not
        Not(===(l,r))
      }
      case Atomic(atomic) => processAtomicFormula(sig)(atomic, replaces, false)
      case Cond(cond, thn, els) => {
        import leo.datastructures.IF_THEN_ELSE
        IF_THEN_ELSE(processTFF0(sig)(cond, replaces),processTFF0(sig)(thn, replaces),processTFF0(sig)(els, replaces))
      }
      case Let(binding, in) =>  Out.warn("Unsupported let-definition in term, treated as $true."); LitTrue()
    }
  }

  import leo.datastructures.tptp.tff.{BinaryConnective => TFFBinaryConnective}
  protected[parsers] def processTFFBinaryConn(conn: TFFBinaryConnective): HOLBinaryConnective = {
    import leo.datastructures.tptp.tff.{<=> => TFFEquiv, Impl => TFFImpl, <= => TFFIf, | => TFFOr, & => TFFAnd, ~| => TFFNor, ~& => TFFNand, <~> => TFFNiff}
    import leo.datastructures.{<=> => equiv, Impl => impl, <= => i_f, ||| => or, & => and, ~||| => nor, ~& => nand, <~> => niff}

    conn match {
      case TFFEquiv => equiv
      case TFFImpl  => impl
      case TFFIf    => i_f
      case TFFOr    => or
      case TFFAnd   => and
      case TFFNor   => nor
      case TFFNand  => nand
      case TFFNiff  => niff
    }
  }

  import leo.datastructures.tptp.tff.{UnaryConnective => TFFUnaryConnective}
  protected[parsers] def processTFFUnary(conn: TFFUnaryConnective): HOLUnaryConnective = {
    import leo.datastructures.tptp.tff.{Not => TFFNot}
    import leo.datastructures.{Not => not}

    conn match {
      case TFFNot => not
    }
  }

  import leo.datastructures.tptp.tff.{Quantifier => TFFQuantifier}
  protected[parsers] def processTFFUnary(conn: TFFQuantifier): HOLUnaryConnective = {
    import leo.datastructures.tptp.tff.{! => TFFAll, ? => TFFAny}
    import leo.datastructures.{Forall => forall, Exists => exists}

    conn match {
      case TFFAll => forall
      case TFFAny => exists
    }
  }

  // Type processing
  import leo.datastructures.tptp.tff.{Type => TFFType}
  type TFFBoundTyReplaces = Seq[Variable]
  protected[parsers] def convertTFFType(sig: Signature)(tffType: TFFType, replace: Replaces): Either[Type,Kind] = {
    import leo.datastructures.tptp.tff.{AtomicType,->,*,QuantifiedType}
    tffType match {
      // "AtomicType" constructs: Type variables, Base types, type kinds, or type/kind applications
      case AtomicType(ty, List()) if ty.charAt(0).isUpper => mkVarType(typeMapping(replace).size + typeOffset(replace) - typeMapping(replace)(ty)._2 + 1)  // Type Variable
      case AtomicType(ty, List()) if ty == "$tType" => typeKind // kind *
      case AtomicType(ty, List())  => mkType(sig.meta(ty).key)  // Base type
      case AtomicType(_, _) => throw new IllegalArgumentException("Processing of applied types not implemented yet") // TODO
      // Function type / kind
      case ->(tys) => { // Tricky here: It might be functions of "sort" * -> [], * -> *, [] -> [], [] -> *
                        // We only plan to support variant 1 (polymorphism),2 (constructors), 3 (ordinary functions) in a medium time range (4 is dependent type)
                        // Case 1 is captured by 'case QuantifiedType' due to TFF1's syntax
                        // So, only consider case 3 for now, but keep case 2 in mind
        val convertedTys = tys.map(convertTFFType(sig)(_, replace))
        require(convertedTys.forall(_.isLeft), "Constructors are not yet supported, but kind found inside a function: " +tffType.toString) // TODO
        mkFunType(convertedTys.map(_.left.get)) // since we only want case 3
      }
      // Product type / kind
      case *(tys) => {
        val converted = tys.map(convertTFFType(sig)(_, replace))
        // TODO: we consider only types here, is this correct?
        require(converted.forall(_.isLeft), "Sum constructor only allowed on types")
        mkProdType(converted.map(_.left.get))
      }
      // Quantified type
      case QuantifiedType(vars, body) => {
        val processedVars = vars.map(_ match {
          case (name, None) => (name, Right(typeKind)) // * is assumed when no type is given
          case (name, Some(ty)) => (name, convertTFFType(sig)(ty, replace))
        })
        require(processedVars.forall(_._2.isRight), "Only '$tType' as type assertion is allowed for type variables in quantified types")
        val newReplaces = processedVars.foldLeft(replace)({case (repl,vari) => vari match {
          case (name, Left(ty)) => termMapping(repl).get(name) match {
            case None => ((termMapping(repl).+((name,(ty, termMapping(repl).size+1))),termOffset(repl)),repl._2)
            case _ =>  ((termMapping(repl).+((name,(ty, termMapping(repl).size+1))),termOffset(repl)+1),repl._2)
          }
          case (name, Right(k)) => typeMapping(repl).get(name) match {
            case None => (repl._1,(typeMapping(repl).+((name,(k, typeMapping(repl).size+1))),typeOffset(repl)))
            case _ =>  (repl._1,(typeMapping(repl).+((name,(k, typeMapping(repl).size+1))),typeOffset(repl)+1))
          }
        }})
        processedVars.foldRight(convertTFFType(sig)(body,newReplaces).left.get)({case (_,b) => ∀(b)}) // NOTE: this is only allowed on top-level
        // the body of quantification must be a type.
        // TODO: better error treating
      }
    }
  }

  implicit def kindToTypeOrKind(k: Kind): Either[Type, Kind] = Right(k)
  implicit def typeToTypeOrKind(ty: Type): Either[Type, Kind] = Left(ty)

  //////////////////////////
  // FOF Formula processing
  //////////////////////////

  def processFOF(sig: Signature)(input: FOFAnnotated): Option[Result] = {
    import leo.datastructures.tptp.fof.{Logical, Sequent}
    input.formula match {
//      case Logical(lf) if input.role == "definition" => {  // TODO: Commented out -- how do definitions look like in FOF? See COM021+1.p, RNG126+1.p
//                                                          val (defName, defDef) = processFOFDef(sig)(lf)
//                                                          sig.addDefined(defName, defDef, defDef.ty)
//                                                          None
//                                                        }
      case Logical(lf) => val role = processRole(input.role); Some((input.name, singleTermToClause(processFOF0(sig)(lf, noRep), role), role))
      case Sequent(_,_) => throw new IllegalArgumentException("Processing of fof sequents not yet implemented.")
    }
  }

  import leo.datastructures.tptp.fof.{LogicFormula => FOFLogicalFormula}
  protected[parsers] def processFOFDef(sig: Signature)(input: FOFLogicalFormula): (String, term.Term) = {
    import leo.datastructures.tptp.fof.Atomic
    input match {
      case Atomic(Equality(Func(name, Nil),right)) => (name, processTerm(sig)(right, noRep))  // TODO See above TODO
      case _ => throw new IllegalArgumentException("Malformed definition")
    }
  }

  protected[parsers] def processFOF0(sig: Signature)(input: FOFLogicalFormula, replaces: Replaces): term.Term = {
    import leo.datastructures.tptp.fof.{Binary, Unary, Quantified, Atomic, Inequality}
    input match {
      case Binary(left, conn, right) => processFOFBinaryConn(conn).apply(processFOF0(sig)(left, replaces),processFOF0(sig)(right, replaces))
      case Unary(conn, f) => processFOFUnary(conn).apply(processFOF0(sig)(f, replaces))
      case Quantified(q, varList, matrix) => {
        val quantifier = processFOFUnary(q)
        val processedVars = varList.map((_, sig.i))
        val newReplaces = processedVars.foldLeft(replaces)({case (repl,vari) => vari match {
          case (name, ty) => termMapping(repl).get(name) match {
            case None => ((termMapping(repl).+((name,(ty, termMapping(repl).size+1))),termOffset(repl)),repl._2)
            case _ =>  ((termMapping(repl).+((name,(ty, termMapping(repl).size+1))),termOffset(repl)+1),repl._2)
          }
        }})
        mkPolyQuantifiedFOF(quantifier, processedVars, processFOF0(sig)(matrix, newReplaces))
      }
      case Atomic(atomic) => processAtomicFormula(sig)(atomic, replaces)
      case Inequality(left,right) => {
        val (l,r) = (processTermArgs(sig)(left, replaces),processTermArgs(sig)(right, replaces))
        !===(l,r)
      }
    }
  }

  import leo.datastructures.tptp.fof.{BinaryConnective => FOFBinaryConnective}
  protected[parsers] def processFOFBinaryConn(conn: FOFBinaryConnective): HOLBinaryConnective = {
    import leo.datastructures.tptp.fof.{<=> => FOFEquiv, Impl => FOFImpl, <= => FOFIf, | => FOFOr, & => FOFAnd, ~| => FOFNor, ~& => FOFNand, <~> => FOFNiff}
    import leo.datastructures.{<=> => equiv, Impl => impl, <= => i_f, ||| => or, & => and, ~||| => nor, ~& => nand, <~> => niff}

    conn match {
      case FOFEquiv => equiv
      case FOFImpl  => impl
      case FOFIf    => i_f
      case FOFOr    => or
      case FOFAnd   => and
      case FOFNor   => nor
      case FOFNand  => nand
      case FOFNiff  => niff
    }
  }

  import leo.datastructures.tptp.fof.{UnaryConnective => FOFUnaryConnective}
  protected[parsers] def processFOFUnary(conn: FOFUnaryConnective): HOLUnaryConnective = {
    import leo.datastructures.tptp.fof.{Not => FOFNot}
    import leo.datastructures.{Not => not}

    conn match {
      case FOFNot => not
    }
  }

  import leo.datastructures.tptp.fof.{Quantifier => FOFQuantifier}
  protected[parsers] def processFOFUnary(conn: FOFQuantifier): HOLUnaryConnective = {
    import leo.datastructures.tptp.fof.{! => FOFAll, ? => FOFAny}
    import leo.datastructures.{Forall => forall, Exists => exists}

    conn match {
      case FOFAll => forall
      case FOFAny => exists
    }
  }


  //////////////////////////
  // CNF Formula processing
  //////////////////////////

  import leo.datastructures.tptp.cnf.{ Formula => CNFLogicalFormula}
  def processCNF(sig: Signature)(input: CNFAnnotated): Option[Result] = {
    val role = processRole(input.role)
    Some((input.name, processCNF0(sig)(input.formula, roleToClauseOrigin(role)), role))
  }

  protected[parsers] def processCNF0(sig: Signature)(input: CNFLogicalFormula, origin: ClauseOrigin): Clause = {
    import leo.datastructures.tptp.cnf.{Positive, Negative, Inequality}
    import leo.datastructures.Literal.{mkNegLit, mkPosLit, mkUniLit}
    val lits = input.literals.map { _ match {
      case Positive(f) => mkPosLit(processAtomicFormula(sig)(f, ???))
      case Negative(f) => mkNegLit(processAtomicFormula(sig)(f, ???))
      case Inequality(l, r) => mkUniLit(processTerm(sig)(l, ???), processTerm(sig)(r, ???))
    }
    }
    import leo.datastructures.Clause.{mkClause}
    mkClause(lits, origin)
  }


  ////////////////////////////
  // Common 'term' processing
  ////////////////////////////

  def processTermArgs(sig: Signature)(input: TPTPTerm, replace: Replaces, adHocDefs: Boolean = true): term.Term = input match {
    case Func(name, vars) => {
      val converted = vars.map(processTermArgs(sig)(_, replace, adHocDefs))
      if (sig.exists(name) || !adHocDefs) {
        mkTermApp(mkAtom(sig(name).key), converted)
      } else {
        mkTermApp(mkAtom(sig.addUninterpreted(name, mkFunType(vars.map(_ => sig.i), sig.i))), converted)
      }
    }
    case other => processTerm(sig)(other, replace, adHocDefs)
  }

  def processTerm(sig: Signature)(input: TPTPTerm, replace: Replaces, adHocDefs: Boolean = true): term.Term = input match {
    case Func(name, vars) => {
      val converted = vars.map(processTermArgs(sig)(_, replace, adHocDefs))
      if (sig.exists(name) || !adHocDefs) {
        mkTermApp(mkAtom(sig(name).key), converted)
      } else {
        mkTermApp(mkAtom(sig.addUninterpreted(name, mkFunType(vars.map(_ => sig.i), sig.o))), converted)
      }

    }
    case DefinedFunc(name, vars) => {
      val converted = vars.map(processTerm(sig)(_, replace, adHocDefs))
      mkTermApp(mkAtom(sig(name).key), converted)
    }
    case SystemFunc(name, vars) => {
      val converted = vars.map(processTerm(sig)(_, replace, adHocDefs))
      mkTermApp(mkAtom(sig(name).key), converted)
    }
    case Var(name) => termMapping(replace).get(name) match {
      case None => throw new IllegalArgumentException("Unbound variable found in formula: "+input.toString)
      case Some((ty, scope)) => mkBound(ty, termMapping(replace).size + termOffset(replace) - scope +1)
    }

    case NumberTerm(value) => value match {
      case IntegerNumber(value) => {
        val constName = "$$int(" + value.toString + ")"
        if (sig.exists(constName)) {
          mkAtom(sig(constName).key)
        } else {
          // Note: that this is a hack, we use the fact that untyped
          // languages use ad-hoc definitions of symbols
          // Here, we must use type $i for numbers of all kinds
          if (adHocDefs) {
            mkAtom(sig.addUninterpreted(constName, sig.i))
          } else {
            mkAtom(sig.addUninterpreted(constName, sig.int))
          }

        }
      }
      case DoubleNumber(value) => {
        val constName = "$$real(" + value.toString + ")"
        if (sig.exists(constName)) {
          mkAtom(sig(constName).key)
        } else {
          // See note above
          if (adHocDefs) {
            mkAtom(sig.addUninterpreted(constName, sig.i))
          } else {
            mkAtom(sig.addUninterpreted(constName, sig.real))
          }
        }
      }
      case RationalNumber(p,q) =>  {
        val constName = "$$rational(" + p.toString + "/" + q.toString +")"
        if (sig.exists(constName)) {
          mkAtom(sig(constName).key)
        } else {
          // See note above
          if (adHocDefs) {
            mkAtom(sig.addUninterpreted(constName, sig.i))
          } else {
            mkAtom(sig.addUninterpreted(constName, sig.rat))
          }
        }
      }
    }
    case Distinct(data) => // NOTE: Side-effects may occur if this is the first occurence of '"data"'
                            if (sig.exists("\""+data+"\"")) {
                              mkAtom(sig.apply("\""+data+"\"").key)
                            } else {
                              mkAtom(sig.addUninterpreted("\""+data+"\"", sig.i))
                            }
    case Cond(cond, thn, els) => {
      import leo.datastructures.IF_THEN_ELSE
      IF_THEN_ELSE(processTFF0(sig)(cond, replace),processTerm(sig)(thn, replace, adHocDefs),processTerm(sig)(els, replace, adHocDefs))
    }
    case Let(binding, in) =>  Out.warn("Unsupported let-definition in term, treated as $true."); LitTrue()
  }

  def processAtomicFormula(sig: Signature)(input: AtomicFormula, replace: Replaces, adHocDefs: Boolean = true): term.Term = input match {
    case Plain(func) => processTerm(sig)(func, replace,adHocDefs)
    case DefinedPlain(func) => processTerm(sig)(func, replace, adHocDefs)
    case SystemPlain(func) => processTerm(sig)(func, replace, adHocDefs)
    case Equality(left,right) => {
      import leo.datastructures.===
      ===(processTermArgs(sig)(left, replace, adHocDefs),processTermArgs(sig)(right, replace, adHocDefs))
    }
  }

  ///////////
  // Utility
  ///////////

  protected[parsers] def mkPolyQuantified(q: HOLUnaryConnective, varList: Seq[ProcessedVar], body: term.Term): term.Term = {
    def mkPolyHelper(a: ProcessedVar, b: term.Term): term.Term = a match {
      case (_, Left(ty)) => q.apply(λ(ty)(b))
      case (_, Right(`typeKind`)) => Λ(b)
      case (_, Right(_))        => throw new IllegalArgumentException("Formalization of kinds other than * not yet implemented.")
    }

    varList.foldRight(body)(mkPolyHelper(_,_))
  }

  protected[parsers] def mkPolyQuantifiedFOF(q: HOLUnaryConnective, varList: Seq[(Variable, Type)], body: term.Term): term.Term = {
    varList.foldRight(body)({case ((_, ty), term) => q.apply(λ(ty)(term))})
  }

  protected[parsers] def mkITE(sig: Signature)(cond: term.Term, thn: term.Term, els: term.Term): term.Term = {
    mkTermApp( mkAtom(sig.iteKey), List(thn,els))
  }
}

