package replication.calendar

import rdts.base.{Lattice, Uid}
import rdts.datatypes.contextual.ReplicatedSet
import rdts.datatypes.experiments.RaftState
import rdts.dotted.Dotted
import rdts.syntax.{DeltaBuffer, LocalUid}

import scala.util.Random

case class Token(id: Long, owner: Uid, value: String) {
  def same(other: Token) = owner == other.owner && value == other.value
}

case class RaftTokens(
    replicaID: Uid,
    tokenAgreement: RaftState[Token],
    want: DeltaBuffer[Dotted[ReplicatedSet[Token]]],
    tokenFreed: DeltaBuffer[Dotted[ReplicatedSet[Token]]]
) {

  given LocalUid = replicaID

  def owned(value: String): List[Token] = {
    val freed  = tokenFreed.elements
    val owners = tokenAgreement.values.filter(t => t.value == value && !freed.contains(t))
    val mine   = owners.filter(_.owner == replicaID)
    // return all ownership tokens if this replica owns the oldest one
    if (mine.headOption == owners.headOption) mine else Nil
  }

  def isOwned(value: String): Boolean = owned(value).nonEmpty

  def acquire(value: String): RaftTokens = {
    val token = Token(Random.nextLong(), replicaID, value)

    // conditional is only an optimization
    if (!(tokenAgreement.values.iterator ++ want.elements.iterator).exists(_.same(token))) {
      copy(want = want.add(token))
    } else this
  }

  def free(value: String): RaftTokens = {
    copy(tokenFreed = tokenFreed.addAll(owned(value)))
  }

  def update(): RaftTokens = {
    val generalDuties = tokenAgreement.supportLeader(replicaID).supportProposal(replicaID)

    if (tokenAgreement.leader == replicaID) {
      val unwanted = want.removeAll(want.elements.filter(generalDuties.values.contains))
      unwanted.elements.headOption match {
        case None => copy(tokenAgreement = generalDuties, want = unwanted)
        case Some(tok) =>
          copy(tokenAgreement = generalDuties.propose(replicaID, tok), want = unwanted)
      }
    } else copy(tokenAgreement = generalDuties)
  }

  def applyWant(state: Dotted[ReplicatedSet[Token]]): RaftTokens = {
    copy(want = want.applyDelta(state))
  }

  def applyFree(state: Dotted[ReplicatedSet[Token]]): RaftTokens = {
    copy(tokenFreed = tokenFreed.applyDelta(state))
  }

  def applyRaft(state: RaftState[Token]): RaftTokens = {
    copy(tokenAgreement = Lattice.merge(tokenAgreement, state))
  }

  def lead(): RaftTokens =
    copy(tokenAgreement = tokenAgreement.becomeCandidate(replicaID))

}

object RaftTokens {
  def init(replicaID: Uid): RaftTokens =
    RaftTokens(
      replicaID,
      RaftState(Set(replicaID)),
      DeltaBuffer(Dotted(ReplicatedSet.empty[Token])),
      DeltaBuffer(Dotted(ReplicatedSet.empty[Token]))
    )
}
