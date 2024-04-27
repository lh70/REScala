package rdts.datatypes.contextual

import rdts.base.{Bottom, Lattice}
import rdts.datatypes.contextual.CausalQueue.QueueElement
import rdts.dotted.{Dotted, HasDots}
import rdts.syntax.{LocalReplicaId, OpsSyntaxHelper}
import rdts.time.{Dot, Dots, VectorClock}

import scala.collection.immutable.Queue

case class CausalQueue[+T](values: Queue[QueueElement[T]])

object CausalQueue:
  case class QueueElement[+T](value: T, dot: Dot, order: VectorClock)

  given elementLattice[T]: Lattice[QueueElement[T]] with {
    override def merge(left: QueueElement[T], right: QueueElement[T]): QueueElement[T] =
      if left.order < right.order then right else left
  }

  def empty[T]: CausalQueue[T] = CausalQueue(Queue())

  given hasDots[A]: HasDots[CausalQueue[A]] with {
    extension (value: CausalQueue[A])
      override def dots: Dots = Dots.from(value.values.view.map(_.dot))

      override def removeDots(dots: Dots): Option[CausalQueue[A]] =
        Some(CausalQueue(value.values.filter(qe => !dots.contains(qe.dot))))
  }

  given bottomInstance[T]: Bottom[CausalQueue[T]] = Bottom.derived

  extension [C, T](container: C)
    def causalQueue: syntax[C, T] = syntax(container)

  implicit class syntax[C, T](container: C)
      extends OpsSyntaxHelper[C, CausalQueue[T]](container) {

    def enqueue(using LocalReplicaId, IsCausalMutator)(e: T): C =
      val time = context.clock.inc(replicaId)
      val dot  = time.dotOf(replicaId)
      Dotted(CausalQueue(Queue(QueueElement(e, dot, time))), Dots.single(dot)).mutator

    def head(using IsQuery) =
      val QueueElement(e, _, _) = current.values.head
      e

    def dequeue(using IsCausalMutator)(): C =
      val QueueElement(_, dot, _) = current.values.head
      Dotted(CausalQueue.empty, Dots.single(dot)).mutator

    def removeBy(using IsCausalMutator)(p: T => Boolean) =
      val toRemove = current.values.filter(e => p(e.value)).map(_.dot)
      Dotted(CausalQueue.empty, Dots.from(toRemove)).mutator

    def elements(using IsQuery): Queue[T] =
      current.values.map(_.value)

  }

  given lattice[A]: Lattice[CausalQueue[A]] with {
    override def merge(left: CausalQueue[A], right: CausalQueue[A]): CausalQueue[A] =
      CausalQueue:
        (left.values concat right.values)
          .sortBy { qe => qe.order }(using VectorClock.vectorClockTotalOrdering).distinct

  }
