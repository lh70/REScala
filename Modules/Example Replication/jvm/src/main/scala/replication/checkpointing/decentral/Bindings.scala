package replication.checkpointing.decentral

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import loci.registry.Binding
import loci.serializer.jsoniterScala.*
import loci.transmitter.IdenticallyTransmittable
import rdts.base.Uid
import rdts.datatypes.contextual.ReplicatedSet
import rdts.dotted.Dotted
import replication.JsoniterCodecs.given

import scala.concurrent.Future

object Bindings {
  type SetState = Dotted[ReplicatedSet[Int]]

  case class CheckpointMessage(cp: Checkpoint, changes: SetState)

  implicit val intCodec: JsonValueCodec[Int] = JsonCodecMaker.make

  implicit val checkpointCodec: JsonValueCodec[Checkpoint] = JsonCodecMaker.make

  implicit val setStateMessageCodec: JsonValueCodec[SetState] = JsonCodecMaker.make
  given JsonValueCodec[Map[Uid, Int]]                         = JsonCodecMaker.make

  implicit val checkpointMessageCodec: JsonValueCodec[CheckpointMessage] = JsonCodecMaker.make

  implicit val transmittableSetState: IdenticallyTransmittable[SetState] = IdenticallyTransmittable()
  given IdenticallyTransmittable[Map[Uid, Int]]                          = IdenticallyTransmittable()

  implicit val transmittableCheckpointMessage: IdenticallyTransmittable[CheckpointMessage] = IdenticallyTransmittable()

  val receiveDeltaBinding: Binding[SetState => Unit, SetState => Future[Unit]] =
    Binding[SetState => Unit]("receiveDelta")

  val getCheckpointsBinding: Binding[() => Map[Uid, Int], () => Future[Map[Uid, Int]]] =
    Binding[() => Map[Uid, Int]]("getCheckpoints")

  val receiveCheckpointBinding: Binding[CheckpointMessage => Unit, CheckpointMessage => Future[Unit]] =
    Binding[CheckpointMessage => Unit]("receiveCheckpoint")
}
