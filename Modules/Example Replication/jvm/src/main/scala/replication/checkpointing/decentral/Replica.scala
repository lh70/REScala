package replication.checkpointing.decentral

import loci.transmitter.{RemoteAccessException, RemoteRef}
import rdts.base.{Lattice, Uid}
import rdts.datatypes.contextual.ReplicatedSet
import rdts.dotted.Dotted
import rdts.syntax.{DeltaBuffer, LocalUid}
import replication.checkpointing.decentral.Bindings.*

import scala.concurrent.Future
import scala.io.StdIn.readLine
import scala.util.matching.Regex

class Replica(val listenPort: Int, val connectTo: List[(String, Int)], id: Uid, initSize: Int) extends Peer {
  val add: Regex       = """add (\d+)""".r
  val remove: Regex    = """remove (\d+)""".r
  val clear: String    = "clear"
  val elements: String = "elements"
  val size: String     = "size"
  val exit: String     = "exit"

  val minAtomsForCheckpoint = 100
  val maxAtomsForCheckpoint = 500

  var set: DeltaBuffer[Dotted[ReplicatedSet[Int]]] = DeltaBuffer(Dotted(ReplicatedSet.empty))

  var checkpoints: Map[Uid, Int] = Map(id -> 0)

  var checkpointMap: Map[Checkpoint, SetState] = Map()

  var unboundLocalChanges: List[SetState] = List()

  var unboundRemoteChanges: SetState = Dotted(ReplicatedSet.empty[Int])

  given LocalUid = id

  def sendDeltaRecursive(
      remoteReceiveDelta: SetState => Future[Unit],
      atoms: Iterable[SetState],
      merged: SetState
  ): Unit = {
    remoteReceiveDelta(merged).failed.foreach {
      case e: RemoteAccessException => e.reason match {
          case RemoteAccessException.RemoteException(name, _) if name.contains("JsonReaderException") =>
            val (firstHalf, secondHalf) = {
              val a =
                if (atoms.isEmpty) Lattice[SetState].decompose(merged)
                else atoms

              val atomsSize = a.size

              (a.take(atomsSize / 2), a.drop(atomsSize / 2))
            }

            sendDeltaRecursive(remoteReceiveDelta, firstHalf, firstHalf.reduce(Lattice[SetState].merge))
            sendDeltaRecursive(remoteReceiveDelta, secondHalf, secondHalf.reduce(Lattice[SetState].merge))
          case _ => e.printStackTrace()
        }

      case e => e.printStackTrace()
    }
  }

  def sendDelta(deltaState: SetState, rr: RemoteRef): Unit =
    sendDeltaRecursive(registry.lookup(receiveDeltaBinding, rr), List(), deltaState)

  def propagateDeltas(): Unit = {
    registry.remotes.foreach { rr =>
      set.deltaBuffer.reduceOption(Lattice[SetState].merge).foreach(sendDelta(_, rr))
    }

    set = set.clearDeltas()
  }

  def bindGetCheckpoints(): Unit = registry.bind(getCheckpointsBinding) { () => checkpoints }

  def bindReceiveDelta(): Unit = registry.bindSbj(receiveDeltaBinding) { (remoteRef: RemoteRef, deltaState: SetState) =>
    set = set.applyDelta(deltaState)

    set.deltaBuffer.headOption match {
      case None =>
      case Some(deltaState) =>
        unboundRemoteChanges = Lattice[SetState].merge(unboundRemoteChanges, deltaState)

        propagateDeltas()

        println(set.elements)
    }
  }

  def bindReceiveCheckpoint(): Unit =
    registry.bindSbj(receiveCheckpointBinding) { (remoteRef: RemoteRef, msg: CheckpointMessage) =>
      msg match {
        case CheckpointMessage(cp @ Checkpoint(replicaID, counter), changes) =>
          if (checkpoints.contains(replicaID) && checkpoints(replicaID) >= counter) ()
          else {
            set = set.applyDelta(changes).clearDeltas()

            unboundRemoteChanges =
              Lattice[SetState].diff(changes, unboundRemoteChanges).getOrElse(Dotted(ReplicatedSet.empty))

            checkpoints = checkpoints.updated(replicaID, counter)

            checkpointMap = checkpointMap.updated(cp, changes)

            registry.remotes.foreach { rr =>
              if (rr != remoteRef) sendCheckpoint(msg, rr)
            }
          }
      }
    }

  def sendCheckpoint(msg: CheckpointMessage, rr: RemoteRef): Unit = {
    val receiveCheckpoint = registry.lookup(receiveCheckpointBinding, rr)
    receiveCheckpoint(msg)
    ()
  }

  def createCheckpoint(atoms: List[SetState]): Unit = {
    val newCounter = checkpoints(id) + 1
    checkpoints = checkpoints.updated(id, newCounter)

    val newCheckpoint = Checkpoint(id, newCounter)
    val changes       = atoms.reduce(Lattice[SetState].merge)
    checkpointMap = checkpointMap.updated(newCheckpoint, changes)

    registry.remotes.foreach { sendCheckpoint(CheckpointMessage(newCheckpoint, changes), _) }
  }

  def createCheckpoints(): Unit = {
    while (unboundLocalChanges.size > maxAtomsForCheckpoint) {
      val changesForCheckPoint = unboundLocalChanges.takeRight(maxAtomsForCheckpoint)
      unboundLocalChanges = unboundLocalChanges.dropRight(maxAtomsForCheckpoint)
      createCheckpoint(changesForCheckPoint)
    }

    if (unboundLocalChanges.size >= minAtomsForCheckpoint) {
      createCheckpoint(unboundLocalChanges)
      unboundLocalChanges = List()
    }
  }

  def onMutate(): Unit = {
    if (set.deltaBuffer.isEmpty) {
      return
    }

    unboundLocalChanges = set.deltaBuffer.foldLeft(unboundLocalChanges) { (list, delta) =>
      list.prependedAll(Lattice[SetState].decompose(delta))
    }

    if (unboundLocalChanges.size < minAtomsForCheckpoint) {
      propagateDeltas()
    } else {
      createCheckpoints()

      if (unboundLocalChanges.nonEmpty) {
        val delta = unboundLocalChanges.reduce(Lattice[SetState].merge)

        registry.remotes.foreach { sendDelta(delta, _) }
      }

      set = set.clearDeltas()
    }
  }

  def monitorJoin(): Unit = {
    registry.remoteJoined.monitor { rr =>
      registry.lookup(getCheckpointsBinding, rr)().map { remoteCheckpoints =>
        checkpoints.foreachEntry { (replicaID, counter) =>
          val remoteCounter = remoteCheckpoints.getOrElse(replicaID, 0)

          (remoteCounter + 1 to counter).foreach { n =>
            val cp      = Checkpoint(replicaID, n)
            val changes = checkpointMap(cp)

            sendCheckpoint(CheckpointMessage(cp, changes), rr)
          }
        }
      }

      val unboundChanges = unboundLocalChanges.foldLeft(unboundRemoteChanges) { Lattice[SetState].merge }

      if (unboundChanges != Dotted(ReplicatedSet.empty))
        sendDelta(unboundChanges, rr)
    }
    ()
  }

  def run(): Unit = {
    bindGetCheckpoints()
    bindReceiveDelta()
    bindReceiveCheckpoint()

    monitorJoin()

    setupConnectionHandling()

    set = set.addAll(0 until initSize)
    onMutate()

    while (true) {
      readLine() match {
        case add(n) =>
          set = set.add(n.toInt)
          onMutate()

        case remove(n) =>
          set = set.remove(n.toInt)
          onMutate()

        case `clear` =>
          set = set.clear()
          onMutate()

        case `elements` =>
          println(set.elements)

        case `size` =>
          println(set.elements.size)

        case `exit` =>
          System.exit(0)

        case _ => println("Unknown command")
      }
    }
  }
}
