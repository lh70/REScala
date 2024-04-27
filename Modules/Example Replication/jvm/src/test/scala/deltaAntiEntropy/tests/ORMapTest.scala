package deltaAntiEntropy.tests

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import deltaAntiEntropy.tools.{AntiEntropy, AntiEntropyContainer, Network}
import org.scalacheck.Prop.*
import rdts.base.Bottom
import rdts.datatypes.contextual.{ObserveRemoveMap, ReplicatedSet}
import rdts.dotted.Dotted
import replication.JsoniterCodecs.given

import scala.collection.mutable

class ORMapTest extends munit.ScalaCheckSuite {
  implicit val intCodec: JsonValueCodec[Int] = JsonCodecMaker.make

  property("contains") {
    given rdts.syntax.LocalReplicaId = rdts.syntax.LocalReplicaId.predefined("test")
    given Bottom[Int] with
      def empty = Int.MinValue
    forAll { (entries: List[Int]) =>
      val orMap = entries.foldLeft(Dotted(ObserveRemoveMap.empty[Int, Int])) { (curr, elem) => curr.update(elem, elem) }
      orMap.entries.foreach { (k, v) =>
        assert(orMap.contains(k))
      }
    }
  }

  property("mutateKey/queryKey") {
    forAll { (add: List[Int], remove: List[Int], k: Int) =>

      val network = new Network(0, 0, 0)
      val aea     = new AntiEntropy[ObserveRemoveMap[Int, ReplicatedSet[Int]]]("a", network, mutable.Buffer())
      val aeb     = new AntiEntropy[ReplicatedSet[Int]]("b", network, mutable.Buffer())

      val set = {
        val added: AntiEntropyContainer[ReplicatedSet[Int]] = add.foldLeft(AntiEntropyContainer(aeb)) {
          case (s, e) => s.add(using s.replicaID)(e)
        }

        remove.foldLeft(added) {
          case (s, e) => s.remove(e)
        }
      }

      val map = {
        val added = add.foldLeft(AntiEntropyContainer[ObserveRemoveMap[Int, ReplicatedSet[Int]]](aea)) {
          case (m, e) =>
            m.transform(k)(_.add(using m.replicaID)(e))
        }

        remove.foldLeft(added) {
          case (m, e) => m.transform(k)(_.remove(e))
        }
      }

      val mapElements = map.queryKey(k).elements

      assert(
        mapElements == set.elements,
        s"Mutating/Querying a key in an ObserveRemoveMap should have the same behavior as modifying a standalone CRDT of that type, but $mapElements does not equal ${set.elements}"
      )
    }
  }

  property("remove") {
    forAll { (add: List[Int], remove: List[Int], k: Int) =>
      val network = new Network(0, 0, 0)
      val aea =
        new AntiEntropy[ObserveRemoveMap[Int, ReplicatedSet[Int]]]("a", network, mutable.Buffer())
      val aeb = new AntiEntropy[ReplicatedSet[Int]]("b", network, mutable.Buffer())

      val empty = AntiEntropyContainer[ReplicatedSet[Int]](aeb)

      val map = {
        val added = add.foldLeft(AntiEntropyContainer[ObserveRemoveMap[Int, ReplicatedSet[Int]]](aea)) {
          case (m, e) => m.transform(k)(_.add(using m.replicaID)(e))
        }

        remove.foldLeft(added) {
          case (m, e) => m.transform(k)(_.remove(e))
        }
      }

      val removed = map.observeRemoveMap.remove(k)

      val queryResult = removed.queryKey(k).elements

      assertEquals(
        queryResult,
        empty.elements,
        s"Querying a removed key should produce the same result as querying an empty CRDT, but $queryResult does not equal ${empty.elements}"
      )
    }
  }

}
