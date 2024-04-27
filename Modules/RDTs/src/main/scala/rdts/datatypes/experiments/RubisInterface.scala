package rdts.datatypes.experiments

import rdts.base.Uid
import rdts.datatypes.contextual.ReplicatedSet
import rdts.datatypes.experiments.AuctionInterface.Bid.User
import rdts.dotted.Dotted
import rdts.syntax.{LocalReplicaId, OpsSyntaxHelper}

/** A Rubis (Rice University Bidding System) is a Delta CRDT modeling an auction system.
  *
  * Bids can only be placed on auctions that were previously opened and with a previously registered userId. When an auction
  * is closed, concurrently placed bids are still accepted and may thus change the winner of the auction. To prevent two
  * replicas from concurrently registering the same userId, requests for registering a new userId must be resolved by a
  * central replica using resolveRegisterUser.
  *
  * This auction system was in part modeled after the Rice University Bidding System (RUBiS) proposed by Cecchet et al. in
  * "Performance and Scalability of EJB Applications", see [[https://www.researchgate.net/publication/2534515_Performance_and_Scalability_of_EJB_Applications here]]
  */
object RubisInterface {
  type AID = String

  type State = (ReplicatedSet[(User, Uid)], Map[User, Uid], Map[AID, AuctionInterface.AuctionData])

  private class DeltaStateFactory {
    val bottom: State = (ReplicatedSet.empty, Map.empty, Map.empty)

    def make(
        userRequests: ReplicatedSet[(User, Uid)] = bottom._1,
        users: Map[User, Uid] = bottom._2,
        auctions: Map[AID, AuctionInterface.AuctionData] = bottom._3
    ): State = (userRequests, users, auctions)
  }

  private def deltaState: DeltaStateFactory = new DeltaStateFactory

  implicit class RubisSyntax[C](container: C) extends OpsSyntaxHelper[C, State](container) {

    def placeBid(auctionId: AID, userId: User, price: Int): IdMutator = {
      val (_, users, m) = current
      val newMap =
        if (users.get(userId).contains(replicaId) && m.contains(auctionId)) {
          m.updatedWith(auctionId) { _.map(a => a.bid(userId, price)) }
        } else Map.empty[AID, AuctionInterface.AuctionData]

      deltaState.make(auctions = newMap).mutator
    }

    def closeAuction(auctionId: AID)(using IsMutator): C = {
      val (_, _, m) = current
      val newMap =
        if (m.contains(auctionId)) {
          m.updatedWith(auctionId) { _.map(a => a.knockDown()) }
        } else Map.empty[AID, AuctionInterface.AuctionData]

      deltaState.make(auctions = newMap).mutator
    }

    def openAuction(auctionId: AID)(using IsMutator): C = {
      val (_, _, m) = current
      val newMap =
        if (m.contains(auctionId)) Map.empty[AID, AuctionInterface.AuctionData]
        else Map(auctionId -> AuctionInterface.AuctionData())

      deltaState.make(auctions = newMap).mutator
    }

    def requestRegisterUser(using LocalReplicaId)(userId: User): CausalMutator = {
      val (req, users, _) = current
      if (users.contains(userId)) Dotted(deltaState.make(), context).mutator
      else
        val merged = req.inheritContext.add(userId -> replicaId)
        Dotted(deltaState.make(userRequests = merged.data), merged.context).mutator
    }

    def resolveRegisterUser()(using IsCausalMutator): C = {
      val (req, users, _) = current
      val newUsers = req.elements.foldLeft(Map.empty[User, Uid]) {
        case (newlyRegistered, (uid, rid)) =>
          if ((users ++ newlyRegistered).contains(uid))
            newlyRegistered
          else {
            newlyRegistered.updated(uid, rid)
          }
      }

      Dotted(req, context).clear().map { ur =>
        deltaState.make(
          userRequests = ur,
          users = newUsers
        )
      }

    }.mutator
  }
}
